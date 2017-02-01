package org.bdgp.OpenHiCAMM.Modules;

import static org.bdgp.OpenHiCAMM.Util.where;

import java.awt.Component;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bdgp.OpenHiCAMM.Dao;
import org.bdgp.OpenHiCAMM.Logger;
import org.bdgp.OpenHiCAMM.Util;
import org.bdgp.OpenHiCAMM.ValidationError;
import org.bdgp.OpenHiCAMM.WorkflowRunner;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.Image;
import org.bdgp.OpenHiCAMM.DB.ModuleConfig;
import org.bdgp.OpenHiCAMM.DB.Slide;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.Task.Status;
import org.bdgp.OpenHiCAMM.DB.TaskConfig;
import org.bdgp.OpenHiCAMM.DB.TaskDispatch;
import org.bdgp.OpenHiCAMM.DB.WorkflowModule;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Configuration;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Module;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudio;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
//import org.micromanager.graph.MultiChannelHistograms;
//import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.MMSerializationException;

import ij.ImagePlus;
//import ij.WindowManager;
import ij.gui.NewImage;
import ij.io.FileSaver;
import ij.process.Blitter;
import ij.process.ImageProcessor;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;

public class SlideSurveyor implements Module {
    private static final String SURVEY_IMAGE_DIRECTORY_PREFIX = "survey";

	WorkflowRunner workflowRunner;
    WorkflowModule workflowModule;

    @Override
    public void initialize(WorkflowRunner workflowRunner, WorkflowModule workflowModule) {
        this.workflowRunner = workflowRunner;
        this.workflowModule = workflowModule;

        // set initial configs
        workflowRunner.getModuleConfig().insertOrUpdate(
                new ModuleConfig(this.workflowModule.getId(), "canImageSlides", "yes"), 
                "id", "key");
    }
    
    @Override
    public Status run(Task task, final Map<String,Config> conf, final Logger logger) {
        Dao<WorkflowModule> wmDao = workflowRunner.getWorkflow();
        Dao<Slide> slideDao = workflowRunner.getWorkflowDb().table(Slide.class);
        Dao<Image> imageDao = workflowRunner.getWorkflowDb().table(Image.class);
        Dao<TaskConfig> taskConfigDao = workflowRunner.getWorkflowDb().table(TaskConfig.class);

    	logger.fine(String.format("Running task: %s", task));
    	for (Config c : conf.values()) {
    		logger.fine(String.format("Using configuration: %s", c));
    	}

    	// load the position list
        if (!conf.containsKey("posListFile")) {
            throw new RuntimeException("Cuold not find configuration for posListFile!");
        }
        Config posList = conf.get("posListFile");
        logger.fine(String.format("%s: Loading position list from file: %s", this.workflowModule.getName(), Util.escape(posList.getValue())));
        File posListFile = new File(posList.getValue());
        if (!posListFile.exists()) {
            throw new RuntimeException("Cannot find position list file "+posListFile.getPath());
        }
        PositionList positionList = new PositionList();
        try { positionList.load(posListFile.getPath()); } 
        catch (MMException e) {throw new RuntimeException(e);}
        
        // get the survey folder
        Config surveyFolderConf = conf.get("surveyFolder");
        if (surveyFolderConf == null) throw new RuntimeException(String.format(
                "%s: surveyFolder config not found!", task.getName(this.workflowRunner.getWorkflow())));
        File surveyFolder = new File(surveyFolderConf.getValue());
        logger.fine(String.format("Survey folder: %s", surveyFolder));
        surveyFolder.mkdirs();
        
        // load configs
        Config slideIdConf = conf.get("slideId");
        if (slideIdConf == null) throw new RuntimeException("Undefined conf value for slideId!");
        Integer slideId = new Integer(slideIdConf.getValue());
        Slide slide = slideDao.selectOneOrDie(where("id", slideId));

        Config imageScaleFactorConf = conf.get("imageScaleFactor");
        if (imageScaleFactorConf == null) throw new RuntimeException("Undefined conf value for scaleFactor!");
        Double imageScaleFactor = new Double(imageScaleFactorConf.getValue());
        
        Config pixelSizeConf = conf.get("pixelSize");
        if (pixelSizeConf == null) throw new RuntimeException("Undefined conf value for pixelSize!");
        Double pixelSize = new Double(pixelSizeConf.getValue());

        Config invertXAxisConf = conf.get("invertXAxis");
        if (invertXAxisConf == null) throw new RuntimeException("Undefined conf value for invertXAxis!");
        Boolean invertXAxis = new Boolean(invertXAxisConf.getValue().equals("yes"));

        Config invertYAxisConf = conf.get("invertYAxis");
        if (invertYAxisConf == null) throw new RuntimeException("Undefined conf value for invertYAxis!");
        Boolean invertYAxis = new Boolean(invertYAxisConf.getValue().equals("yes"));
        
        ImagePlus slideThumb;
        Double minX = null, minY = null, maxX = null, maxY = null; 
        CMMCore core = this.workflowRunner.getOpenHiCAMM().getApp().getMMCore();
        try {
            if (core.isSequenceRunning()) {
                core.stopSequenceAcquisition();
                Thread.sleep(1000);
            }

            // close all open acquisition windows
            for (String name : MMStudio.getInstance().getAcquisitionNames()) {
                try { MMStudio.getInstance().closeAcquisitionWindow(name); } 
                catch (MMScriptException e) { /* do nothing */ }
            }
            
            // start live mode
            core.clearCircularBuffer();
            core.startContinuousSequenceAcquisition(0);
            
            // display the live mode GUI
            //MMStudio.getInstance().enableLiveMode(true);
            
            // attempt to fix the histogram scaling
            //VirtualAcquisitionDisplay display = VirtualAcquisitionDisplay.getDisplay(WindowManager.getCurrentImage());
            //if (display != null) {
            //    if (MultiChannelHistograms.class.isAssignableFrom(display.getHistograms().getClass())) {
            //        MultiChannelHistograms mch = (MultiChannelHistograms)display.getHistograms();
            //        if (mch != null) {
            //            try { mch.fullScaleChannels(); }
            //            catch (Throwable e) { /* do nothing */ }
            //            logger.info("Set histogram channels to full!");
            //        }
            //    }
            //}

            // determine the image width/height
            TaggedImage img0 = core.getLastTaggedImage();
            ImageProcessor ip0 = ImageUtils.makeProcessor(img0);
            while (img0 == null || ip0 == null) {
                Thread.sleep(10);
                img0 = core.getLastTaggedImage();
                ip0 = ImageUtils.makeProcessor(img0);
            }
            Integer imageWidth = ip0.getWidth(), imageHeight = ip0.getHeight();

            // determine the bounds of the stage coordinates
            for (MultiStagePosition msp : positionList.getPositions()) {
                if (minX == null || msp.getX() < minX) minX = msp.getX();
                if (maxX == null || msp.getX() > maxX) maxX = msp.getX();
                if (minY == null || msp.getY() < minY) minY = msp.getY();
                if (maxY == null || msp.getY() > maxY) maxY = msp.getY();
            }
            logger.fine(String.format("minX = %f, minY = %f, maxX = %f, maxY = %f, imageWidth = %d, imageHeight = %d", 
                    minX, minY, maxX, maxY, imageWidth, imageHeight));
            if (minX == null || maxX == null || minY == null || maxY == null || imageWidth == null || imageHeight == null) {
                throw new RuntimeException(String.format(
                        "Could not determine bounds of slide! minX = %f, minY = %f, maxX = %f, maxY = %f, imageWidth = %d, imageHeight = %d", 
                        minX, minY, maxX, maxY, imageWidth, imageHeight));
            }

            int slideWidthPx = (int)Math.floor(((maxX - minX) / pixelSize) + (double)imageWidth);
            int slideHeightPx = (int)Math.floor(((maxY - minY) / pixelSize) + (double)imageHeight);
            logger.fine(String.format("slideWidthPx = %d, slideHeightPx = %d", slideWidthPx, slideHeightPx));
            
            logger.fine(String.format("scaleFactor = %f", imageScaleFactor));
            int slidePreviewWidth = (int)Math.floor(imageScaleFactor * slideWidthPx);
            logger.fine(String.format("slidePreviewWidth = %d", slidePreviewWidth));
            int slidePreviewHeight = (int)Math.floor(imageScaleFactor * slideHeightPx);
            logger.fine(String.format("slidePreviewHeight = %d", slidePreviewHeight));
            
            // set the initial Z Position
            if (conf.containsKey("initialZPos")) {
                Double initialZPos = new Double(conf.get("initialZPos").getValue());
                logger.info(String.format("Setting initial Z Position to: %.02f", initialZPos));
                String focusDevice = core.getFocusDevice();

                final double EPSILON = 1.0;
                try { 
                    Double currentPos = core.getPosition(focusDevice);
                    while (Math.abs(currentPos-initialZPos) > EPSILON) {
                        core.setPosition(focusDevice, initialZPos); 
                        core.waitForDevice(focusDevice);
                        Thread.sleep(500);
                        currentPos = core.getPosition(focusDevice);
                    }
                } 
                catch (Exception e1) {throw new RuntimeException(e1);}
            }

            Date startAcquisition = new Date();
            this.workflowRunner.getTaskConfig().insertOrUpdate(
                    new TaskConfig(task.getId(),
                            "startAcquisition", 
                            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(startAcquisition)), 
                    "id", "key");
            
            logger.info(String.format("Now acquiring %s survey images for slide %s...", 
                    positionList.getNumberOfPositions(),
                    slide.getName()));

            // create the empty large slide image
            slideThumb = NewImage.createRGBImage(String.format("%s.%s.%s", workflowModule.getName(), task.getName(wmDao), slide.getName()), 
                    slidePreviewWidth, slidePreviewHeight, 1, NewImage.FILL_WHITE);
            
            // iterate through the position list, imaging using live mode to build up the large slide image
            for (int i=0; i<positionList.getNumberOfPositions(); ++i) {
                MultiStagePosition msp = positionList.getPosition(i);

                logger.fine(String.format("Acquired survey image for slide %s: %s [%d/%d images]", 
                        slide.getName(),
                        msp.getLabel(),
                        i+1, positionList.getNumberOfPositions()));

                // move the stage into position
                double[] xy_stage = SlideImager.moveStage(this.workflowModule.getName(), core, msp.getX(), msp.getY(), logger);
                double x_stage_new = xy_stage[0];
                double y_stage_new = xy_stage[1];

                // acquire the live mode image
                TaggedImage img = null;
                ImageProcessor ip = null;
                ImagePlus imp = null;
                while (img == null || ip == null || ip.getPixels() == null) {
                    img = core.getLastTaggedImage();
                    logger.info(String.format("Image %s/%s tags: %s", i+1, positionList.getNumberOfPositions(), img.tags.toString()));
                    if (img != null) ip = ImageUtils.makeProcessor(img);
                    if (ip != null && ip.getPixels() != null) {
                        imp = new ImagePlus(String.format("%s.%s.%s.x%s.y%s", 
                                this.workflowModule.getName(), task.getName(wmDao), slide.getName(), msp.getX(), msp.getY()), 
                                ip);
                    }
                    if (imp != null) break;

                    Thread.sleep(10);
                }
                //new ImageConverter(imp).convertToGray8();

                int width = imp.getWidth(), height = imp.getHeight();
                logger.fine(String.format("Image width: %d, height: %d", width, height));
                imp.getProcessor().setInterpolationMethod(ImageProcessor.BILINEAR);
                imp.setProcessor(imp.getTitle(), imp.getProcessor().resize(
                        (int)Math.floor(imp.getWidth() * imageScaleFactor), 
                        (int)Math.floor(imp.getHeight() * imageScaleFactor)));
                logger.fine(String.format("Resized image width: %d, height: %d", imp.getWidth(), imp.getHeight()));
                
                int xloc = (int)Math.floor(((x_stage_new - minX) / pixelSize));
                int xlocInvert = invertXAxis? slideWidthPx - (xloc + width) : xloc;
                int xlocScale = (int)Math.floor(xlocInvert * imageScaleFactor);
                logger.fine(String.format("xloc = %d, xlocInvert = %d, xlocScale = %d", xloc, xlocInvert, xlocScale));
                int yloc = (int)Math.floor(((y_stage_new - minY) / pixelSize));
                int ylocInvert = invertYAxis? slideHeightPx - (yloc + height) : yloc;
                int ylocScale = (int)Math.floor(ylocInvert * imageScaleFactor);
                logger.fine(String.format("yloc = %d, ylocInvert = %d, ylocScale = %d", yloc, ylocInvert, ylocScale));

                // draw the thumbnail image
                slideThumb.getProcessor().copyBits(imp.getProcessor(), xlocScale, ylocScale, Blitter.COPY);
            }
        } 
        catch (Exception e) {
            throw new RuntimeException(e);
        } 
        finally {
            // close the live mode GUI
            //MMStudio.getInstance().enableLiveMode(false);

            // stop live mode
            try { core.stopSequenceAcquisition(); } 
            catch (Exception e) {throw new RuntimeException(e);}
        }
        
        // perform any necessary image preprocessing on slideThumb
        
        // save the stitched image to the stitched folder using the stitch group as the 
        // file name.
        FileSaver fileSaver = new FileSaver(slideThumb);
        File imageFile = new File(surveyFolder, String.format("%s.tif", slideThumb.getTitle()));
        fileSaver.saveAsTiff(imageFile.getPath());

        // create necessary DB records so that ROIFinder can work on the large slide image
        Image image = new Image(imageFile.getPath(), slideId);
        imageDao.insert(image);
        logger.fine(String.format("Inserted image: %s", image));
        imageDao.reload(image, "path","slideId");
        
        // Store the Image ID as a Task Config variable
        TaskConfig imageIdConf = new TaskConfig(
                task.getId(),
                "imageId",
                new Integer(image.getId()).toString());
        taskConfigDao.insertOrUpdate(imageIdConf,"id","key");
        logger.fine(String.format("Inserted/Updated imageId config: %s", imageIdConf));
                                
        return Status.SUCCESS;
    }

    private File createSurveyImageFolder() {
        String rootDir = this.workflowRunner.getWorkflowDir().getPath();
        int count = 1;
        File stitchedFolder = new File(rootDir, String.format("%s_%d", SURVEY_IMAGE_DIRECTORY_PREFIX, count));
        while (!stitchedFolder.mkdirs()) {
            ++count;
            stitchedFolder = new File(rootDir, String.format("%s_%d", SURVEY_IMAGE_DIRECTORY_PREFIX, count));
        }
        return stitchedFolder;
    }

    @Override
    public String getTitle() {
        return this.getClass().getName();
    }

    @Override
    public String getDescription() {
        return this.getClass().getName();
    }

    @Override
    public Configuration configure() {
        return new Configuration() {
            SlideSurveyorDialog slideSurveyorDialog = new SlideSurveyorDialog(SlideSurveyor.this.workflowRunner);

            @Override
            public Config[] retrieve() {
                List<Config> configs = new ArrayList<Config>();
                if (slideSurveyorDialog.posListText.getText().length()>0) {
                    configs.add(new Config(workflowModule.getId(), 
                            "posListFile", 
                            slideSurveyorDialog.posListText.getText()));
                }
                if (((Double)slideSurveyorDialog.pixelSize.getValue()).doubleValue() != 0.0) {
                    configs.add(new Config(workflowModule.getId(), "pixelSize", slideSurveyorDialog.pixelSize.getValue().toString()));
                }

                if (((Double)slideSurveyorDialog.imageScaleFactor.getValue()).doubleValue() != 0.0) {
                    configs.add(new Config(workflowModule.getId(), "imageScaleFactor", slideSurveyorDialog.imageScaleFactor.getValue().toString()));
                }

                if (slideSurveyorDialog.invertXAxisYes.isSelected()) {
                	configs.add(new Config(workflowModule.getId(), "invertXAxis", "yes"));
                }
                else if (slideSurveyorDialog.invertXAxisNo.isSelected()) {
                	configs.add(new Config(workflowModule.getId(), "invertXAxis", "no"));
                }
                
                if (slideSurveyorDialog.invertYAxisYes.isSelected()) {
                	configs.add(new Config(workflowModule.getId(), "invertYAxis", "yes"));
                }
                else if (slideSurveyorDialog.invertYAxisNo.isSelected()) {
                	configs.add(new Config(workflowModule.getId(), "invertYAxis", "no"));
                }
                
                if (slideSurveyorDialog.setInitZPosYes.isSelected()) {
                    configs.add(new Config(workflowModule.getId(), "initialZPos", slideSurveyorDialog.initialZPos.getValue().toString()));
                }
                return configs.toArray(new Config[0]);
            }
            @Override
            public Component display(Config[] configs) {
                Map<String,Config> conf = new HashMap<String,Config>();
                for (Config config : configs) {
                    conf.put(config.getKey(), config);
                }
                if (conf.containsKey("posListFile")) {
                    Config posList = conf.get("posListFile");
                    slideSurveyorDialog.posListText.setText(posList.getValue());
                }
                
                if (conf.containsKey("pixelSize")) {
                    slideSurveyorDialog.pixelSize.setValue(new Double(conf.get("pixelSize").getValue()));
                }
                if (conf.containsKey("imageScaleFactor")) {
                    slideSurveyorDialog.imageScaleFactor.setValue(new Double(conf.get("imageScaleFactor").getValue()));
                }
                
                if (conf.containsKey("invertXAxis")) {
                	if (conf.get("invertXAxis").getValue().equals("yes")) {
                		slideSurveyorDialog.invertXAxisYes.setSelected(true);
                		slideSurveyorDialog.invertXAxisNo.setSelected(false);
                	}
                	else if (conf.get("invertXAxis").getValue().equals("no")) {
                		slideSurveyorDialog.invertXAxisYes.setSelected(false);
                		slideSurveyorDialog.invertXAxisNo.setSelected(true);
                	}
                }

                if (conf.containsKey("invertYAxis")) {
                	if (conf.get("invertYAxis").getValue().equals("yes")) {
                		slideSurveyorDialog.invertYAxisYes.setSelected(true);
                		slideSurveyorDialog.invertYAxisNo.setSelected(false);
                	}
                	else if (conf.get("invertYAxis").getValue().equals("no")) {
                		slideSurveyorDialog.invertYAxisYes.setSelected(false);
                		slideSurveyorDialog.invertYAxisNo.setSelected(true);
                	}
                }
                
                if (conf.containsKey("initialZPos")) {
                    slideSurveyorDialog.setInitZPosYes.setSelected(true);
                    slideSurveyorDialog.initialZPos.setValue(new Double(conf.get("initialZPos").getValue()));
                }
                else {
                    slideSurveyorDialog.setInitZPosNo.setSelected(true);
                    slideSurveyorDialog.initialZPos.setValue(new Double(0.0));
                }
                return slideSurveyorDialog;
            }
            @Override
            public ValidationError[] validate() {
                List<ValidationError> errors = new ArrayList<ValidationError>();
                if (slideSurveyorDialog.posListText.getText().length()>0) {
                    File posListFile = new File(slideSurveyorDialog.posListText.getText());
                    if (!posListFile.exists()) {
                        errors.add(new ValidationError(workflowModule.getName(), "Position list file "+posListFile.toString()+" not found."));
                    }
                }
                if ((Double)slideSurveyorDialog.pixelSize.getValue() <= 0.0) {
                    errors.add(new ValidationError(workflowModule.getName(), 
                            "Pixel size must be greater than zero."));
                }
                if ((Double)slideSurveyorDialog.imageScaleFactor.getValue() <= 0.0) {
                    errors.add(new ValidationError(workflowModule.getName(), 
                            "Image scale factor must be greater than zero."));
                }
                return errors.toArray(new ValidationError[0]);
            }
        };
    }
    
    
    @Override
    public List<Task> createTaskRecords(List<Task> parentTasks, Map<String,Config> config, Logger logger) {
        Dao<Slide> slideDao = workflowRunner.getWorkflowDb().table(Slide.class);
        Dao<ModuleConfig> moduleConfig = workflowRunner.getModuleConfig();
        Dao<TaskConfig> taskConfigDao = workflowRunner.getTaskConfig();

        // Load all the module configuration into a HashMap
        Map<String,Config> moduleConf = new HashMap<String,Config>();
        for (ModuleConfig c : moduleConfig.select(where("id",this.workflowModule.getId()))) {
            moduleConf.put(c.getKey(), c);
            workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Using module config: %s", 
            		this.workflowModule.getName(), c));
        }

        // create a folder to store the stitched images
        File surveyFolder = createSurveyImageFolder();

        // Create task records and connect to parent tasks
        // If no parent tasks were defined, then just create a single task instance.
        List<Task> tasks = new ArrayList<Task>();
        for (Task parentTask : parentTasks.size()>0? parentTasks.toArray(new Task[]{}) : new Task[]{null}) 
        {
            workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Connecting parent task %s", 
            		this.workflowModule.getName(), Util.escape(parentTask)));

        	// get the parent task configuration
        	Map<String,TaskConfig> parentTaskConf = new HashMap<String,TaskConfig>();
        	if (parentTask != null) {
                for (TaskConfig c : taskConfigDao.select(where("id",parentTask.getId()))) {
                    parentTaskConf.put(c.getKey(), c);
                    workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Using task config: %s", 
                            this.workflowModule.getName(), c));
                }
        	}

        	// Get the associated slide.
        	Slide slide;
            if (parentTaskConf.containsKey("slideId")) {
            	slide = slideDao.selectOneOrDie(where("id",new Integer(parentTaskConf.get("slideId").getValue())));
            	workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Inherited slideId %s", this.workflowModule.getName(), parentTaskConf.get("slideId")));
            }
            // If no associated slide is registered, create a slide to represent this task
            else {
                String uuid = UUID.randomUUID().toString();
            	slide = new Slide(uuid);
            	slideDao.insertOrUpdate(slide,"experimentId");
            	slideDao.reload(slide, "experimentId");
            	workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Created new slide: %s", this.workflowModule.getName(), slide.toString()));
            }
            config.put("slideId", new Config(this.workflowModule.getId(), "slideId", new Integer(slide.getId()).toString()));

            // Create task record
            Task task = new Task(this.workflowModule.getId(), Status.NEW);
            workflowRunner.getTaskStatus().insert(task);
            tasks.add(task);
            workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Created task record: %s", 
                    this.workflowModule.getName(), task));
            
            // add the surveyFolder task config
            TaskConfig surveyFolderConf = new TaskConfig(
                    task.getId(),
                    "surveyFolder", surveyFolder.toString());
            taskConfigDao.insert(surveyFolderConf);
                            
            // Create taskConfig record for the image label
            TaskConfig imageLabel = new TaskConfig(
                    task.getId(),
                    "imageLabel", 
                    MDUtils.generateLabel(0, 0, 0, 0));
            taskConfigDao.insert(imageLabel);
            workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Created task config: %s", 
                    this.workflowModule.getName(), imageLabel));

            // Create taskConfig record for the MSP label (positionName)
            TaskConfig positionName = new TaskConfig(
                    task.getId(),
                    "positionName", 
                    "Pos0");
            taskConfigDao.insert(positionName);
            workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Created task config: %s", 
                    this.workflowModule.getName(), positionName));

            // load the position list
            if (!moduleConf.containsKey("posListFile")) {
                throw new RuntimeException("Cuold not find configuration for posListFile!");
            }
            Config posList = moduleConf.get("posListFile");
            logger.fine(String.format("%s: Loading position list from file: %s", this.workflowModule.getName(), Util.escape(posList.getValue())));
            File posListFile = new File(posList.getValue());
            if (!posListFile.exists()) {
                throw new RuntimeException("Cannot find position list file "+posListFile.getPath());
            }
            PositionList positionList = new PositionList();
            try { positionList.load(posListFile.getPath()); } 
            catch (MMException e) {throw new RuntimeException(e);}

            // determine the bounds of the stage coordinates
            Double minX = null, minY = null, maxX = null, maxY = null; 
            for (MultiStagePosition msp : positionList.getPositions()) {
                if (minX == null || msp.getX() < minX) minX = msp.getX();
                if (maxX == null || msp.getX() > maxX) maxX = msp.getX();
                if (minY == null || msp.getY() < minY) minY = msp.getY();
                if (maxY == null || msp.getY() > maxY) maxY = msp.getY();
            }
            logger.fine(String.format("minX = %s, minY = %s, maxX = %s, maxY = %s", minX, minY, maxX, maxY));
            if (minX == null || maxX == null || minY == null || maxY == null) {
                throw new RuntimeException(String.format(
                        "Could not determine bounds of slide! minX = %s, minY = %s, maxX = %s, maxY = %s", 
                        minX, minY, maxX, maxY));
            }

            // Store the X and Y stage positions as task config variables
            double XPositionUm = ((maxX - minX) / 2.0) + minX;
            TaskConfig XPositionUmConf = new TaskConfig(
                    task.getId(),
                    "XPositionUm",
                    new Double(XPositionUm).toString());
            taskConfigDao.insertOrUpdate(XPositionUmConf,"id","key");
            logger.fine(String.format("Inserted/Updated XPositionUm config: %s", XPositionUm));

            double YPositionUm = ((maxY - minY) / 2.0) + minY;
            TaskConfig YPositionUmConf = new TaskConfig(
                    task.getId(),
                    "YPositionUm",
                    new Double(YPositionUm).toString());
            taskConfigDao.insertOrUpdate(YPositionUmConf,"id","key");
            logger.fine(String.format("Inserted/Updated YPositionUm config: %s", YPositionUm));
            
            // Store the MSP value as a JSON string
            CMMCore core = this.workflowRunner.getOpenHiCAMM().getApp().getMMCore();
            String xyStage = core.getXYStageDevice();
            String focus = core.getFocusDevice();
            try {
                PositionList mspPosList = new PositionList();
                MultiStagePosition msp = new MultiStagePosition(xyStage, XPositionUm, YPositionUm, focus, 0.0);
                mspPosList.addPosition(msp);
                String mspJson = new JSONObject(mspPosList.serialize()).
                        getJSONArray("POSITIONS").getJSONObject(0).toString();
                TaskConfig mspConf = new TaskConfig(
                        task.getId(), "MSP", mspJson);
                taskConfigDao.insert(mspConf);
                config.put("MSP", mspConf);
                workflowRunner.getLogger().fine(String.format(
                        "Inserted MultiStagePosition config: %s", mspJson));
            } 
            catch (MMSerializationException e) {throw new RuntimeException(e);} 
            catch (JSONException e) {throw new RuntimeException(e);}

            // create taskConfig record for the slide ID
            TaskConfig slideId = new TaskConfig(
                    task.getId(),
                    "slideId", 
                    new Integer(slide.getId()).toString());
            taskConfigDao.insert(slideId);
            workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Created task config: %s", 
                    this.workflowModule.getName(), slideId));
            
            // Create task dispatch record
            if (parentTask != null) {
                TaskDispatch dispatch = new TaskDispatch(task.getId(), parentTask.getId());
                workflowRunner.getTaskDispatch().insert(dispatch);
                workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Created task dispatch record: %s", 
                        this.workflowModule.getName(), dispatch));
            }
        }
        return tasks;
    }
    
    @Override
    public TaskType getTaskType() {
        return Module.TaskType.SERIAL;
    }

    @Override public void cleanup(Task task, Map<String,Config> config, Logger logger) { }

    @Override
    public void runInitialize() { }
    
    @Override
    public Status setTaskStatusOnResume(Task task) {
        return Status.NEW;
    }
}
