package org.bdgp.OpenHiCAMM.Modules;

import static org.bdgp.OpenHiCAMM.Util.where;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
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
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.internal.MMStudio;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.data.internal.DefaultImage;
//import org.micromanager.graph.MultiChannelHistograms;
//import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.internal.utils.imageanalysis.ImageUtils;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
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
    
    public static boolean compareImages(TaggedImage taggedImage1, TaggedImage taggedImage2) {
        if (taggedImage1 == null || taggedImage2 == null)
            return false;
        if (taggedImage1.pix instanceof byte[] && taggedImage2.pix instanceof byte[]) {
            byte[] pix1 = (byte[])taggedImage1.pix;
            byte[] pix2 = (byte[])taggedImage2.pix;
            if (pix1.length != pix2.length) return false;
            for (int i = 0; i < pix1.length; ++i) {
                if (pix1[i] != pix2[i]) return false;
            }
            return true;
        }
        if (taggedImage1.pix instanceof int[] && taggedImage2.pix instanceof int[]) {
            int[] pix1 = (int[])taggedImage1.pix;
            int[] pix2 = (int[])taggedImage2.pix;
            if (pix1.length != pix2.length) return false;
            for (int i = 0; i < pix1.length; ++i) {
                if (pix1[i] != pix2[i]) return false;
            }
            return true;
        }
        if (taggedImage1.pix instanceof short[] && taggedImage2.pix instanceof short[]) {
            short[] pix1 = (short[])taggedImage1.pix;
            short[] pix2 = (short[])taggedImage2.pix;
            if (pix1.length != pix2.length) return false;
            for (int i = 0; i < pix1.length; ++i) {
                if (pix1[i] != pix2[i]) return false;
            }
            return true;
        }
        if (taggedImage1.pix instanceof float[] && taggedImage2.pix instanceof float[]) {
            float[] pix1 = (float[])taggedImage1.pix;
            float[] pix2 = (float[])taggedImage2.pix;
            if (pix1.length != pix2.length) return false;
            for (int i = 0; i < pix1.length; ++i) {
                if (pix1[i] != pix2[i]) return false;
            }
            return true;
        }
        return false;
    }

    public static boolean compareImages(ImagePlus im1, ImagePlus im2) {
        int[][] im1s = im1.getProcessor().getIntArray();
        int[][] im2s = im2.getProcessor().getIntArray();
        if (im1s.length != im2s.length) return false;
        for (int x=0; x<im1s.length; ++x) {
            if (im1s[x].length != im2s[x].length) return false;
            for (int y=0; y<im1s[x].length; ++y) {
                if (im1s[x][y] != im2s[x][y]) return false;
            }
        }
        return true;
    }
    
    @Override
    public Status run(Task task, Map<String,Config> conf, final Logger logger) {
        Dao<WorkflowModule> wmDao = workflowRunner.getWorkflow();
        Dao<Slide> slideDao = workflowRunner.getWorkflowDb().table(Slide.class);
        Dao<Image> imageDao = workflowRunner.getWorkflowDb().table(Image.class);
        Dao<TaskConfig> taskConfigDao = workflowRunner.getWorkflowDb().table(TaskConfig.class);

    	logger.fine(String.format("Running task: %s", task));
    	for (Config c : conf.values()) {
    		logger.fine(String.format("Using configuration: %s", c));
    	}
    	
        Date startAcquisition = new Date();
        this.workflowRunner.getTaskConfig().insertOrUpdate(
                new TaskConfig(task.getId(),
                        "startAcquisition", 
                        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(startAcquisition)), 
                "id", "key");
            
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
        catch (IOException e) {throw new RuntimeException(e);}
        
        // get the survey folder
        Config surveyFolderConf = conf.get("surveyFolder");
        if (surveyFolderConf == null) throw new RuntimeException(String.format(
                "%s: surveyFolder config not found!", task.getName(this.workflowRunner.getWorkflow())));
        File surveyFolder = new File(surveyFolderConf.getValue());
        if (!Paths.get(surveyFolder.getPath()).isAbsolute()) {
            surveyFolder = Paths.get(workflowRunner.getWorkflowDir().getPath()).resolve(Paths.get(surveyFolder.getPath())).toFile();
        }
        logger.fine(String.format("Survey folder: %s", surveyFolder));
        surveyFolder.mkdirs();
        
        // load configs
        Config slideIdConf = conf.get("slideId");
        if (slideIdConf == null) throw new RuntimeException("Undefined conf value for slideId!");
        Integer slideId = Integer.parseInt(slideIdConf.getValue());
        Slide slide = slideDao.selectOneOrDie(where("id", slideId));

        Config imageScaleFactorConf = conf.get("imageScaleFactor");
        if (imageScaleFactorConf == null) throw new RuntimeException("Undefined conf value for scaleFactor!");
        Double imageScaleFactor = Double.parseDouble(imageScaleFactorConf.getValue());
        
        Config pixelSizeConf = conf.get("pixelSize");
        if (pixelSizeConf == null) throw new RuntimeException("Undefined conf value for pixelSize!");
        Double pixelSize = Double.parseDouble(pixelSizeConf.getValue());

        Config invertXAxisConf = conf.get("invertXAxis");
        if (invertXAxisConf == null) throw new RuntimeException("Undefined conf value for invertXAxis!");
        Boolean invertXAxis = invertXAxisConf.getValue().equals("yes");

        Config invertYAxisConf = conf.get("invertYAxis");
        if (invertYAxisConf == null) throw new RuntimeException("Undefined conf value for invertYAxis!");
        Boolean invertYAxis = invertYAxisConf.getValue().equals("yes");
        
        Config postprocessingMacroConf = conf.get("postprocessingMacro");
        String postprocessingMacro = postprocessingMacroConf != null? postprocessingMacroConf.getValue() : "";

        ImagePlus slideThumb;
        Double minX = null, minY = null, maxX = null, maxY = null; 
        CMMCore core = this.workflowRunner.getOpenHiCAMM().getApp().getCMMCore();
        try {
            if (core.isSequenceRunning()) {
                core.stopSequenceAcquisition();
                Thread.sleep(1000);
            }

            // close all open acquisition windows
            MMStudio.getInstance().getDisplayManager().closeAllDisplayWindows(false);

            // get the dimensions of a normal image
            core.snapImage();
            TaggedImage taggedImage = core.getTaggedImage();
            if (taggedImage == null) {
                throw new RuntimeException("Could not get image dimensions!");
            }
            org.micromanager.data.Image mmimage = new DefaultImage(taggedImage);
            int hiresImageWidth = mmimage.getWidth();
            int hiresImageHeight = mmimage.getHeight();
            
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

            // determine the width/height of a live view image
            TaggedImage img0 = null;
            ImageProcessor ip0 = null;
            try { img0 = core.getLastTaggedImage(); }
            catch (Throwable e) { /* do nothing */ }
            if (img0 != null) ip0 = ImageUtils.makeProcessor(img0);
            while (img0 == null || ip0 == null) {
                Thread.sleep(10);
                try { img0 = core.getLastTaggedImage(); }
                catch (Throwable e) { /* do nothing */ }
                if (img0 != null) ip0 = ImageUtils.makeProcessor(img0);
            }
            Integer imageWidth = ip0.getWidth(), imageHeight = ip0.getHeight();
            
            // compute the live view pixel sizes
            double liveViewPixelSizeX = pixelSize * (double)hiresImageWidth / (double)imageWidth;
            double liveViewPixelSizeY = pixelSize * (double)hiresImageHeight / (double)imageHeight;

            // determine the bounds of the stage coordinates
            for (MultiStagePosition msp : positionList.getPositions()) {
                if (minX == null || msp.getX() < minX) minX = msp.getX();
                if (maxX == null || msp.getX() > maxX) maxX = msp.getX();
                if (minY == null || msp.getY() < minY) minY = msp.getY();
                if (maxY == null || msp.getY() > maxY) maxY = msp.getY();
            }
            logger.fine(String.format("minX = %s, minY = %s, maxX = %s, maxY = %s, imageWidth = %s, imageHeight = %s", 
                    minX, minY, maxX, maxY, imageWidth, imageHeight));
            if (minX == null || maxX == null || minY == null || maxY == null || imageWidth == null || imageHeight == null) {
                throw new RuntimeException(String.format(
                        "Could not determine bounds of slide! minX = %s, minY = %s, maxX = %s, maxY = %s, imageWidth = %s, imageHeight = %s", 
                        minX, minY, maxX, maxY, imageWidth, imageHeight));
            }

            double slideWidthPx = ((maxX - minX) / liveViewPixelSizeX) + (double)imageWidth;
            double slideHeightPx = ((maxY - minY) / liveViewPixelSizeY) + (double)imageHeight;
            logger.fine(String.format("slideWidthPx = %s, slideHeightPx = %s", slideWidthPx, slideHeightPx));
            
            logger.fine(String.format("scaleFactor = %s", imageScaleFactor));
            double scaledSlideWidth = imageScaleFactor * slideWidthPx;
            logger.fine(String.format("slidePreviewWidth = %s", scaledSlideWidth));
            double scaledSlideHeight = imageScaleFactor * slideHeightPx;
            logger.fine(String.format("slidePreviewHeight = %s", scaledSlideHeight));
            
            // set the initial Z Position
            if (conf.containsKey("initialZPos")) {
                Double initialZPos = Double.parseDouble(conf.get("initialZPos").getValue());
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

            logger.info(String.format("Now acquiring %s survey images for slide %s...", 
                    positionList.getNumberOfPositions(),
                    slide.getName()));

            // create the empty large slide image
            slideThumb = NewImage.createRGBImage(String.format("%s.%s.%s", workflowModule.getName(), task.getName(wmDao), slide.getName()), 
                    (int)Math.round(scaledSlideWidth), 
                    (int)Math.round(scaledSlideHeight), 
                    1, NewImage.FILL_WHITE);
            
            // iterate through the position list, imaging using live mode to build up the large slide image
            TaggedImage lastimg = null;
            for (int i=0; i<positionList.getNumberOfPositions(); ++i) {
                MultiStagePosition msp = positionList.getPosition(i);

                logger.fine(String.format("Acquired survey image for slide %s: %s [%s/%s images]", 
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
                    ip = null;
                    try { img = core.getLastTaggedImage(); }
                    catch (Throwable e) { /* do nothing */ }
                    if (img != null) {
                        //logger.info(String.format("Image %s/%s tags: %s", i+1, positionList.getNumberOfPositions(), img.tags.toString()));
                        ip = ImageUtils.makeProcessor(img);
                    }
                    if (ip != null && ip.getPixels() != null) {
                        imp = new ImagePlus(String.format("%s.%s.%s.x%s.y%s", 
                                this.workflowModule.getName(), task.getName(wmDao), slide.getName(), msp.getX(), msp.getY()), 
                                ip);
                        // do a bitwise check to make sure this image is not the same as the last one
                        if (lastimg != null && compareImages(img, lastimg)) {
                            logger.warning(String.format("Detected same images from live view, retrying..."));
                            img = null;
                            imp = null;
                            ip = null;
                            // re-start live mode and try again
                            try { core.stopSequenceAcquisition(); } 
                            catch (Exception e) { /* do nothing */ }
                            Thread.sleep(5000);
                            core.clearCircularBuffer();
                            core.startContinuousSequenceAcquisition(0);
                            Thread.sleep(5000);
                            continue;
                        }
                        lastimg = img;
                        break;
                    }
                    Thread.sleep(1000);
                }

                int width = imp.getWidth(), height = imp.getHeight();
                logger.fine(String.format("Image width: %s, height: %s", width, height));
                imp.getProcessor().setInterpolationMethod(ImageProcessor.BILINEAR);
                imp.setProcessor(imp.getTitle(), imp.getProcessor().resize(
                        (int)Math.round(imp.getWidth() * imageScaleFactor), 
                        (int)Math.round(imp.getHeight() * imageScaleFactor)));
                logger.fine(String.format("Resized image width: %s, height: %s", imp.getWidth(), imp.getHeight()));
                
                double xloc = (x_stage_new - minX) / liveViewPixelSizeX;
                double xlocInvert = invertXAxis? slideWidthPx - (xloc + width) : xloc;
                double xlocScale = xlocInvert * imageScaleFactor;
                logger.fine(String.format("xloc = %s, xlocInvert = %s, xlocScale = %s", xloc, xlocInvert, xlocScale));
                double yloc = (y_stage_new - minY) / liveViewPixelSizeY;
                double ylocInvert = invertYAxis? slideHeightPx - (yloc + height) : yloc;
                double ylocScale = ylocInvert * imageScaleFactor;
                logger.fine(String.format("yloc = %s, ylocInvert = %s, ylocScale = %s", yloc, ylocInvert, ylocScale));

                // draw the thumbnail image
                slideThumb.getProcessor().copyBits(imp.getProcessor(), 
                        (int)Math.round(xlocScale), 
                        (int)Math.round(ylocScale), 
                        Blitter.COPY);
            }

            // save the unprocessed stitched image to the stitched folder using the stitch group as the 
            // file name.
            {
                FileSaver fileSaver = new FileSaver(slideThumb);
                File imageFile = new File(surveyFolder, String.format("%s.unprocessed.tif", slideThumb.getTitle()));
                fileSaver.saveAsTiff(imageFile.getPath());
            }

            // perform any necessary image postprocessing on slideThumb
            if (postprocessingMacro != null && !postprocessingMacro.replaceAll("\\s+","").isEmpty()) {
                slideThumb.show();
                logger.info(String.format("Running postprocessing macro:%n%s", postprocessingMacro));
                IJ.runMacro(postprocessingMacro);
                ImagePlus modifiedImage1 = WindowManager.getImage(slideThumb.getTitle());
                if (modifiedImage1 != null) {
                    modifiedImage1.changes = false;
                    modifiedImage1.close();
                }
                else {
                    slideThumb.changes = false;
                    slideThumb.close();
                }
            }
            
            // save the stitched image to the stitched folder using the stitch group as the 
            // file name.
            FileSaver fileSaver = new FileSaver(slideThumb);
            File imageFile = new File(surveyFolder, String.format("%s.tif", slideThumb.getTitle()));
            fileSaver.saveAsTiff(imageFile.getPath());

            // create necessary DB records so that ROIFinder can work on the large slide image
            String imagePath = Paths.get(workflowRunner.getWorkflowDir().getPath()).
                    relativize(Paths.get(imageFile.getPath())).toString();
            Image image = new Image(imagePath, slideId);
            imageDao.delete(image, "path", "slideId");
            imageDao.insert(image);
            logger.fine(String.format("Inserted image: %s", image));
            imageDao.reload(image, "path","slideId");
            
            // Store the Image ID as a Task Config variable
            TaskConfig imageIdConf = new TaskConfig(
                    task.getId(),
                    "imageId",
                    Integer.toString(image.getId()));
            taskConfigDao.insertOrUpdate(imageIdConf,"id","key");
            conf.put(imageIdConf.getKey(), imageIdConf);
            logger.fine(String.format("Inserted/Updated imageId config: %s", imageIdConf));
                                    
            // Store the pixel Size X/Y conf
            TaskConfig pixelSizeXConf = new TaskConfig(
                    task.getId(),
                    "pixelSizeX",
                    Double.toString(liveViewPixelSizeX));
            taskConfigDao.insertOrUpdate(pixelSizeXConf,"id","key");
            conf.put(pixelSizeXConf.getKey(), pixelSizeXConf);
            logger.fine(String.format("Inserted/Updated pixelSizeUmX config: %s", pixelSizeXConf));
                                    
            TaskConfig pixelSizeYConf = new TaskConfig(
                    task.getId(),
                    "pixelSizeY",
                    Double.toString(liveViewPixelSizeY));
            taskConfigDao.insertOrUpdate(pixelSizeYConf,"id","key");
            conf.put(pixelSizeYConf.getKey(), pixelSizeYConf);
            logger.fine(String.format("Inserted/Updated pixelSizeUmY config: %s", pixelSizeYConf));

            Date endAcquisition = new Date();
            this.workflowRunner.getTaskConfig().insertOrUpdate(
                    new TaskConfig(task.getId(),
                            "endAcquisition", 
                            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(endAcquisition)), 
                    "id", "key");

            this.workflowRunner.getTaskConfig().insertOrUpdate(
                    new TaskConfig(task.getId(),
                            "acquisitionDuration", 
                            Long.toString(endAcquisition.getTime() - startAcquisition.getTime())), 
                    "id", "key");

            return Status.SUCCESS;
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
    }

    private File createSurveyImageFolder() {
        String rootDir = this.workflowRunner.getWorkflowDir().getPath();
        int count = 1;
        File surveyImageFolder = new File(rootDir, String.format("%s_%s", SURVEY_IMAGE_DIRECTORY_PREFIX, count));
        while (!surveyImageFolder.mkdirs()) {
            ++count;
            surveyImageFolder = new File(rootDir, String.format("%s_%s", SURVEY_IMAGE_DIRECTORY_PREFIX, count));
        }
        return surveyImageFolder;
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
                if (!slideSurveyorDialog.postprocessingMacro.getText().replaceAll("\\s+","").isEmpty()) {
                    configs.add(new Config(workflowModule.getId(), "postprocessingMacro", slideSurveyorDialog.postprocessingMacro.getText()));
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
                    slideSurveyorDialog.pixelSize.setValue(Double.parseDouble(conf.get("pixelSize").getValue()));
                }
                if (conf.containsKey("imageScaleFactor")) {
                    slideSurveyorDialog.imageScaleFactor.setValue(Double.parseDouble(conf.get("imageScaleFactor").getValue()));
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
                    slideSurveyorDialog.initialZPos.setValue(Double.parseDouble(conf.get("initialZPos").getValue()));
                }
                else {
                    slideSurveyorDialog.setInitZPosNo.setSelected(true);
                    slideSurveyorDialog.initialZPos.setValue(0.0);
                }
                if (conf.containsKey("postprocessingMacro")) {
                    slideSurveyorDialog.postprocessingMacro.setText(conf.get("postprocessingMacro").getValue());
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
            	slide = slideDao.selectOneOrDie(where("id",Integer.parseInt(parentTaskConf.get("slideId").getValue())));
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
            config.put("slideId", new Config(this.workflowModule.getId(), "slideId", Integer.toString(slide.getId())));

            // Create task record
            Task task = new Task(this.workflowModule.getId(), Status.NEW);
            workflowRunner.getTaskStatus().insert(task);
            tasks.add(task);
            workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Created task record: %s", 
                    this.workflowModule.getName(), task));
            
            // add the surveyFolder task config
            String surveyFolderPath = surveyFolder.getPath();
            if (Paths.get(surveyFolderPath).isAbsolute()) {
                surveyFolderPath = Paths.get(workflowRunner.getWorkflowDir().getPath()).relativize(Paths.get(surveyFolderPath)).toString();
            }
            TaskConfig surveyFolderConf = new TaskConfig(
                    task.getId(),
                    "surveyFolder", surveyFolderPath);
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
            catch (IOException e1) { throw new RuntimeException(e1); }

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
                    Double.toString(XPositionUm));
            taskConfigDao.insertOrUpdate(XPositionUmConf,"id","key");
            logger.fine(String.format("Inserted/Updated XPositionUm config: %s", XPositionUm));

            double YPositionUm = ((maxY - minY) / 2.0) + minY;
            TaskConfig YPositionUmConf = new TaskConfig(
                    task.getId(),
                    "YPositionUm",
                    Double.toString(YPositionUm));
            taskConfigDao.insertOrUpdate(YPositionUmConf,"id","key");
            logger.fine(String.format("Inserted/Updated YPositionUm config: %s", YPositionUm));
            
            // Store the MSP value as a JSON string
            CMMCore core = this.workflowRunner.getOpenHiCAMM().getApp().getCMMCore();
            String xyStage = core.getXYStageDevice();
            String focus = core.getFocusDevice();
            try {
                PositionList mspPosList = new PositionList();
                MultiStagePosition msp = new MultiStagePosition(xyStage, XPositionUm, YPositionUm, focus, 0.0);
                mspPosList.addPosition(msp);
                String mspJson = new JSONObject(mspPosList.toPropertyMap().toJSON()).
                        getJSONArray("POSITIONS").getJSONObject(0).toString();
                TaskConfig mspConf = new TaskConfig(
                        task.getId(), "MSP", mspJson);
                taskConfigDao.insert(mspConf);
                config.put("MSP", mspConf);
                workflowRunner.getLogger().fine(String.format(
                        "Inserted MultiStagePosition config: %s", mspJson));
            } 
            catch (JSONException e) {throw new RuntimeException(e);}

            // create taskConfig record for the slide ID
            TaskConfig slideId = new TaskConfig(
                    task.getId(),
                    "slideId", 
                    Integer.toString(slide.getId()));
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
        if (task.getStatus() != Status.SUCCESS) {
            return Status.NEW;
        }
        // try to find the parent slide loader task
        // if slide loader parent task needs to be re-run, then re-run this as well.
        List<TaskDispatch> tds = this.workflowRunner.getTaskDispatch().select(where("taskId", task.getId()));
        while (!tds.isEmpty()) {
            List<TaskDispatch> parentTds = new ArrayList<>();
            for (TaskDispatch td : tds) {
                Task parentTask = this.workflowRunner.getTaskStatus().selectOneOrDie(where("id", td.getParentTaskId()));
                if (this.workflowRunner.getModuleConfig().selectOne(
                        where("id", parentTask.getModuleId()).
                        and("key", "canLoadSlides").
                        and("value", "yes")) != null) 
                {
                    if (parentTask.getStatus() == Status.NEW) {
                        return Status.NEW;
                    }
                    else {
                        return null;
                    }
                }
                parentTds.addAll(this.workflowRunner.getTaskDispatch().select(where("taskId", parentTask.getId())));
            }
            tds = parentTds;
        }
        return null;
    }
}
