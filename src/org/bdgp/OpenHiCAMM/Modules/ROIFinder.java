package org.bdgp.OpenHiCAMM.Modules;

import java.awt.Component;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import org.bdgp.OpenHiCAMM.Dao;
import org.bdgp.OpenHiCAMM.ImageLog;
import org.bdgp.OpenHiCAMM.ImageLog.ImageLogRecord;
import org.bdgp.OpenHiCAMM.ImageLog.ImageLogRunner;
import org.bdgp.OpenHiCAMM.Logger;
import org.bdgp.OpenHiCAMM.OpenHiCAMM;
import org.bdgp.OpenHiCAMM.Util;
import org.bdgp.OpenHiCAMM.ValidationError;
import org.bdgp.OpenHiCAMM.WorkflowRunner;
import org.bdgp.OpenHiCAMM.DB.Acquisition;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.Image;
import org.bdgp.OpenHiCAMM.DB.ModuleConfig;
import org.bdgp.OpenHiCAMM.DB.ROI;
import org.bdgp.OpenHiCAMM.DB.Slide;
import org.bdgp.OpenHiCAMM.DB.SlidePos;
import org.bdgp.OpenHiCAMM.DB.SlidePosList;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.Task.Status;
import org.bdgp.OpenHiCAMM.DB.TaskConfig;
import org.bdgp.OpenHiCAMM.DB.TaskDispatch;
import org.bdgp.OpenHiCAMM.DB.WorkflowModule;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Configuration;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.ImageLogger;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Module;
import org.micromanager.data.Datastore;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;
import org.micromanager.data.Coords;
import org.micromanager.MMPlugin;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.Studio;
import org.micromanager.StagePosition;

import static org.bdgp.OpenHiCAMM.Util.where;

/**
 * Return x/y/len/width of bounding box surrounding the ROI
 */
@Plugin(type=MMPlugin.class)
public abstract class ROIFinder implements Module, ImageLogger, SciJavaPlugin, MMPlugin {
    protected WorkflowRunner workflowRunner;
    protected WorkflowModule workflowModule;
    protected Studio script;

    @Override
    public void initialize(WorkflowRunner workflowRunner, WorkflowModule workflowModule) {
        this.workflowRunner = workflowRunner;
        this.workflowModule = workflowModule;
        OpenHiCAMM mmslide = workflowRunner.getOpenHiCAMM();
        this.script = mmslide.getApp();
        
        // set initial configs
        workflowRunner.getModuleConfig().insertOrUpdate(
                new ModuleConfig(this.workflowModule.getId(), "canProduceROIs", "yes"), 
                "id", "key");
    }

    @Override
    public Status run(Task task, Map<String,Config> config, final Logger logger) {
    	logger.fine(String.format("Running task: %s", task));
    	for (Config c : config.values()) {
            logger.fine(String.format("Using config: %s", c));
    	}

        // Get the Image record
    	Config imageIdConf = config.get("imageId");
    	if (imageIdConf == null) throw new RuntimeException("No imageId task configuration was set by the slide imager!");
        Integer imageId = Integer.parseInt(imageIdConf.getValue());
        Dao<Image> imageDao = workflowRunner.getWorkflowDb().table(Image.class);
        final Image image = imageDao.selectOneOrDie(where("id",imageId));

        // get the tagged image
        org.micromanager.data.Image mmimage = getMMImage(image, logger);
        int taggedImageWidth; 
        int taggedImageHeight; 
        String positionName;
        taggedImageWidth = mmimage.getWidth();
        taggedImageHeight = mmimage.getHeight();
        positionName = mmimage.getMetadata().getPositionName(Integer.toString(mmimage.getCoords().getStagePosition()));

        // get the image label and position name
        String imageLabel = image.getLabel();
        String label = String.format("%s (%s)", positionName, imageLabel); 
        
        logger.fine(String.format("%s: Using image: %s", label, image));
        logger.fine(String.format("%s: Using image ID: %d", label, imageId));
        
        Dao<Slide> slideDao = workflowRunner.getWorkflowDb().table(Slide.class);
        Slide slide = slideDao.selectOneOrDie(where("id",image.getSlideId()));
        logger.fine(String.format("%s: Using slide: %s", label, slide));

        Config imageScaleFactorConf = config.get("imageScaleFactor");
        double imageScaleFactor = imageScaleFactorConf != null? Double.parseDouble(imageScaleFactorConf.getValue()) : 1.0;

        Config pixelSizeConf = config.get("pixelSize");
        Double pixelSize = pixelSizeConf != null? Double.parseDouble(pixelSizeConf.getValue()) : null;

        Config pixelSizeXConf = config.get("pixelSizeX");
        Double pixelSizeX = pixelSizeXConf != null? Double.parseDouble(pixelSizeXConf.getValue()) : pixelSize;
        logger.fine(String.format("%s: Using pixelSizeX: %f", label, pixelSizeX));

        Config pixelSizeYConf = config.get("pixelSizeY");
        Double pixelSizeY = pixelSizeYConf != null? Double.parseDouble(pixelSizeYConf.getValue()) : pixelSize;
        logger.fine(String.format("%s: Using pixelSizeY: %f", label, pixelSizeY));

        double hiResPixelSize = Double.parseDouble(config.get("hiResPixelSize").getValue());
        logger.fine(String.format("%s: Using hiResPixelSize: %f", label, hiResPixelSize));

        double roiMarginPct = Double.parseDouble(config.get("roiMarginPct").getValue());
        logger.fine(String.format("%s: Using ROI margin size in percent of image size: %f", label, roiMarginPct));

        int positionIndex = mmimage.getCoords().getStagePosition();
        logger.fine(String.format("%s: Using positionIndex: %d", label, positionIndex));

        double overlapPct = Double.parseDouble(config.get("overlapPct").getValue());
        logger.fine(String.format("%s: Using percentage overlap: %f", label, overlapPct));

        // get invertXAxis and invertYAxis conf values
        Config invertXAxisConf = config.get("invertXAxis");
        boolean invertXAxis = invertXAxisConf == null || "yes".equals(invertXAxisConf.getValue());
        Config invertYAxisConf = config.get("invertYAxis");
        boolean invertYAxis = invertYAxisConf == null || "yes".equals(invertYAxisConf.getValue());

        Config imageWidthConf = config.get("imageWidth");
        if (imageWidthConf == null) throw new RuntimeException("Config value imageWidth was not found!");
        Integer imageWidth = Integer.parseInt(imageWidthConf.getValue());
        logger.fine(String.format("%s: Using imageWidth: %s", label, imageWidth));
        
        Config imageHeightConf = config.get("imageHeight");
        if (imageHeightConf == null) throw new RuntimeException("Config value imageHeight was not found!");
        Integer imageHeight = Integer.parseInt(imageHeightConf.getValue());
        logger.fine(String.format("%s: Using imageHeight: %s", label, imageHeight));

        Config XPositionUmConf = config.get("XPositionUm");
        double x_stage = XPositionUmConf == null? 
                mmimage.getMetadata().getXPositionUm() : 
                Double.parseDouble(XPositionUmConf.getValue());
        
        Config YPositionUmConf = config.get("YPositionUm");
        double y_stage = YPositionUmConf == null?
                mmimage.getMetadata().getYPositionUm() :
                Double.parseDouble(YPositionUmConf.getValue());
        
        // Delete any old ROI records
        Dao<ROI> roiDao = this.workflowRunner.getWorkflowDb().table(ROI.class);
        int deleted = roiDao.delete(where("imageId", imageId));
        logger.fine(String.format("%s: Deleted %d old ROI records.", label, deleted));
        
        // Fill in list of ROIs
        logger.fine(String.format("%s: Running process() to get list of ROIs", label));
        List<ROI> rois = new ArrayList<ROI>();
        try {
            rois = process(image, mmimage, logger, new ImageLog.NullImageLogRunner(), config);
        }
        catch (Throwable e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            logger.warning(String.format("Exception during ROI processing: %s", sw.toString()));
            return Status.ERROR;
        }

        // Convert the ROIs into a PositionList
        Map<MultiStagePosition,ROI> roiMap = new HashMap<MultiStagePosition,ROI>();
        PositionList posList = new PositionList();
        for (ROI roi : rois) {
            // insert the ROI record
            roiDao.insert(roi);

            logger.info(String.format("%s: Created new ROI record with width=%s, height=%s, area=%s: %s", 
                    label, roi.getX2()-roi.getX1()+1, roi.getY2()-roi.getY1()+1, 
                    (roi.getX2()-roi.getX1()+1)*(roi.getY2()-roi.getY1()+1), roi));

            // increase the ROI dimensions to add the margins
            double roiMarginWidth = (roiMarginPct / 100.0) * imageWidth * hiResPixelSize / (pixelSizeX / imageScaleFactor);
            double roiMarginHeight = (roiMarginPct / 100.0) * imageHeight * hiResPixelSize / (pixelSizeY / imageScaleFactor);
            int roiX1 = (int)Math.floor(roi.getX1() - roiMarginWidth);
            int roiY1 = (int)Math.floor(roi.getY1() - roiMarginHeight);
            int roiX2 = (int)Math.floor(roi.getX2() + roiMarginWidth);
            int roiY2 = (int)Math.floor(roi.getY2() + roiMarginHeight);
            
            // We need to potentially create a grid of Stage positions in order to capture all of the 
            // ROI.
            int roiWidth = roiX2-roiX1+1;
            int roiHeight = roiY2-roiY1+1;

            int tileWidth = (int)Math.floor((imageWidth * hiResPixelSize) / (pixelSizeX / imageScaleFactor));
            int tileHeight = (int)Math.floor((imageHeight * hiResPixelSize) / (pixelSizeY / imageScaleFactor));

            int tileXOverlap = (int)Math.floor((overlapPct / 100.0) * tileWidth);
            int tileYOverlap = (int)Math.floor((overlapPct / 100.0) * tileHeight);

            int tileXCount = (int)Math.ceil((double)(roiWidth - tileXOverlap) / (double)(tileWidth - tileXOverlap));
            int tileYCount = (int)Math.ceil((double)(roiHeight - tileYOverlap) / (double)(tileHeight - tileYOverlap));

            int tileSetWidth = (tileXCount * (tileWidth - tileXOverlap)) + tileXOverlap;
            int tileSetHeight = (tileYCount * (tileHeight - tileYOverlap)) + tileYOverlap;

            int tileXOffset = (int)Math.floor(
                    (roiX1 + ((double)roiWidth / 2.0)) 
                    - ((double)tileSetWidth / 2.0) 
                    + ((double)tileWidth / 2.0));
            int tileYOffset = (int)Math.floor(
                    (roiY1 + ((double)roiHeight / 2.0)) 
                    - ((double)tileSetHeight / 2.0) 
                    + ((double)tileHeight / 2.0));

            for (int x=0; x < tileXCount; ++x) {
                for (int y=0; y < tileYCount; ++y) {
                    int tileX = (x*(tileWidth - tileXOverlap)) + tileXOffset;
                    int tileY = (y*(tileHeight - tileYOverlap)) + tileYOffset;
                    MultiStagePosition msp = new MultiStagePosition();
                    double sp_x = x_stage+((tileX-taggedImageWidth/2.0)*(pixelSizeX/imageScaleFactor))*(invertXAxis? -1.0 : 1.0);
                    logger.info(String.format("x_stage=%s, tileX=%s, taggedImageWidth=%s, pixelSizeX=%s, imageScaleFactor=%s, invertXAxis=%s, sp.x=%s", 
                            x_stage, tileX, taggedImageWidth, pixelSizeX, imageScaleFactor, invertXAxis, sp_x));
                    double sp_y = y_stage+((tileY-taggedImageHeight/2.0)*(pixelSizeY/imageScaleFactor))*(invertYAxis? -1.0 : 1.0);
                    logger.info(String.format("y_stage=%s, tileY=%s, taggedImageHeight=%s, pixelSizeY=%s, imageScaleFactor=%s, invertYAxis=%s, sp.y=%s", 
                            y_stage, tileY, taggedImageHeight, pixelSizeY, imageScaleFactor, invertYAxis, sp_y));
                    StagePosition sp = StagePosition.create2D("XYStage", sp_x, sp_y);

                    String mspLabel = String.format("%s.%s.ROI%d.X%d.Y%d", 
                            positionName, imageLabel, roi.getId(), x, y);
                    msp.setProperty("stitchGroup", "ROI"+roi.getId());
                    msp.setProperty("ROI", Integer.toString(roi.getId()));
                    msp.setProperty("tileX", Integer.toString(x));
                    msp.setProperty("tileY", Integer.toString(y));
                    msp.setLabel(mspLabel);
                    msp.add(sp);
                    msp.setDefaultXYStage("XYStage");
                    posList.addPosition(msp);
                    roiMap.put(msp, roi);
                    logger.info(String.format("%s: Storing StagePosition(label=%s, numAxes: %d, stageName: %s, x=%.2f, y=%.2f, tileX=%d, tileY=%d)",
                            label, Util.escape(mspLabel), sp.getNumberOfStageAxes(), Util.escape(sp.getStageDeviceLabel()), sp.get2DPositionX(), sp.get2DPositionY(), tileX, tileY));
                }
            }
        }
        if (posList.getNumberOfPositions() > 0) {
            logger.info(String.format("%s: Produced position list of %d ROIs", label, posList.getNumberOfPositions())); 
            logger.fine(String.format("%s: Position List:%n%s", label, posList.toPropertyMap().toJSON()));
        }
        else {
            logger.info(String.format("%s: ROIFinder did not produce any positions for this image", label));
        }

        Dao<SlidePosList> slidePosListDao = workflowRunner.getWorkflowDb().table(SlidePosList.class);
        Dao<SlidePos> slidePosDao = workflowRunner.getWorkflowDb().table(SlidePos.class);

        // First, delete any old slidepos and slideposlist records
        logger.fine(String.format("Deleting old position lists"));
        List<SlidePosList> oldSlidePosLists = slidePosListDao.select(
                where("slideId",slide.getId()).
                and("moduleId",this.workflowModule.getId()).
                and("taskId", task.getId()));
        for (SlidePosList oldSlidePosList : oldSlidePosLists) {
            slidePosDao.delete(where("slidePosListId",oldSlidePosList.getId()));
        }
        slidePosListDao.delete(where("slideId",slide.getId()).and("moduleId",this.workflowModule.getId()));

        // Create/Update SlidePosList and SlidePos DB records
        SlidePosList slidePosList = new SlidePosList(this.workflowModule.getId(), slide.getId(), task.getId(), posList);
        slidePosListDao.insert(slidePosList);
        logger.fine(String.format("%s: Created new SlidePosList: %s", label, slidePosList));

        MultiStagePosition[] msps = posList.getPositions();
        for (int i=0; i<msps.length; ++i) {
            ROI roi = roiMap.get(msps[i]);
            if (roi == null) throw new RuntimeException(
                    String.format("Error: could not get ROI for MSP %s using roiMap", msps[i]));

            SlidePos slidePos = new SlidePos(slidePosList.getId(), i, roi.getId());
            slidePosDao.insert(slidePos);
            logger.fine(String.format("%s: Created new SlidePos record for position %d: %s", label, i, slidePos));
        }
        return Status.SUCCESS;
    }
    
    public org.micromanager.data.Image getMMImage(Image image, Logger logger) {
        String label = image.getLabel();

        // if this image is not in an acquisition, then load it as a file
        if (image.getAcquisitionId() == 0 && image.getPath() != null) {
            org.micromanager.data.Image mmimage = image.getImage(workflowRunner);
            return mmimage;
        }

        // Initialize the acquisition
        Dao<Acquisition> acqDao = workflowRunner.getWorkflowDb().table(Acquisition.class);
        Acquisition acquisition = acqDao.selectOneOrDie(where("id",image.getAcquisitionId()));
        logger.fine(String.format("%s: Using acquisition: %s", label, acquisition));
        int tries = 0;
        final int MAX_TRIES = 30;
        Datastore datastore = null;
        while (tries++ < MAX_TRIES) {
            try {
                datastore = acquisition.getDatastore();
                if (datastore != null) break;
            }
            catch (Throwable e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                logger.warning(String.format("Could not open acquisition %s, trying again...: \n%s", acquisition, sw.toString()));
            }
            try { Thread.sleep(1000); } 
            catch (InterruptedException e) { }
        }
        if (datastore == null) {
            throw new RuntimeException(String.format("Could not open datastore for acqusition %s!", acquisition));
        }

        // Get the image cache object
        Set<String> imageLabels = new TreeSet<>();
        for (Coords coords: datastore.getUnorderedImageCoords()) {
        	imageLabels.add(Image.generateLabel(coords));
        }
        logger.fine(String.format("%s: ImageCache has following images: %s", label, imageLabels));
        logger.fine(String.format("%s: Attempting to grab image %s from imageCache", label, image));
        // Get the tagged image from the image cache
        org.micromanager.data.Image mmimage = image.getImage(datastore);
        if (mmimage == null) throw new RuntimeException(String.format("Acqusition %s, Image %s is not in the image cache!",
                acquisition, image));
        logger.fine(String.format("%s: Got mmimage from Datastore: %s", label, mmimage));

        return mmimage;
    }
    
    /**
     * The process() method must be overridden by a subclass. This method holds the logic for ROI finding. It takes an image
     * and returns a list of ROIs.
     * @param image The input image DB record.
     * @param taggedImage The taggedImage object
     * @param logger A logger object
     * @param imageLog An image logger object
     * @param config A set of configuration key/value pairs
     * @return The list of ROI records.
     */
    public abstract List<ROI> process(
            Image image, 
            org.micromanager.data.Image mmimage, 
            Logger logger, 
            ImageLogRunner imageLog, 
            Map<String,Config> config);
    
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
            ROIFinderDialog dialog = new ROIFinderDialog(ROIFinder.this);
            @Override
            public Config[] retrieve() {
            	List<Config> configs = new ArrayList<Config>();

            	Double hiResPixelSize = (Double)dialog.hiResPixelSize.getValue();
            	if (hiResPixelSize != null) {
            	    configs.add(new Config(ROIFinder.this.workflowModule.getId(), "hiResPixelSize", hiResPixelSize.toString()));
            	}

            	Double minRoiArea = (Double)dialog.minRoiArea.getValue();
            	if (minRoiArea != null) {
            	    configs.add(new Config(ROIFinder.this.workflowModule.getId(), "minRoiArea", minRoiArea.toString()));
            	}
            	Double overlapPct = (Double)dialog.overlapPct.getValue();
            	if (overlapPct != null) {
            	    configs.add(new Config(ROIFinder.this.workflowModule.getId(), "overlapPct", overlapPct.toString()));
            	}
            	Double roiMarginPct = (Double)dialog.roiMarginPct.getValue();
            	if (roiMarginPct != null) {
            	    configs.add(new Config(ROIFinder.this.workflowModule.getId(), "roiMarginPct", roiMarginPct.toString()));
            	}
            	Double roiImageScaleFactor = (Double)dialog.roiImageScaleFactor.getValue();
            	if (roiImageScaleFactor != null) {
            	    configs.add(new Config(ROIFinder.this.workflowModule.getId(), "roiImageScaleFactor", roiImageScaleFactor.toString()));
            	}
            	Integer imageWidth = (Integer)dialog.imageWidth.getValue();
            	if (imageWidth != null) {
            	    configs.add(new Config(ROIFinder.this.workflowModule.getId(), "imageWidth", imageWidth.toString()));
            	}
            	Integer imageHeight = (Integer)dialog.imageHeight.getValue();
            	if (imageHeight != null) {
            	    configs.add(new Config(ROIFinder.this.workflowModule.getId(), "imageHeight", imageHeight.toString()));
            	}
                return configs.toArray(new Config[0]);
            }
            @Override
            public Component display(Config[] configs) {
            	Map<String,Config> confs = new HashMap<String,Config>();
            	for (Config c : configs) {
            		confs.put(c.getKey(), c);
            	}

            	if (confs.containsKey("hiResPixelSize")) {
            	    dialog.hiResPixelSize.setValue(Double.parseDouble(confs.get("hiResPixelSize").getValue()));
            	}
            	if (confs.containsKey("minRoiArea")) {
            	    dialog.minRoiArea.setValue(Double.parseDouble(confs.get("minRoiArea").getValue()));
            	}
            	if (confs.containsKey("overlapPct")) {
            	    dialog.overlapPct.setValue(Double.parseDouble(confs.get("overlapPct").getValue()));
            	}
            	if (confs.containsKey("roiMarginPct")) {
            	    dialog.roiMarginPct.setValue(Double.parseDouble(confs.get("roiMarginPct").getValue()));
            	}
            	if (confs.containsKey("roiImageScaleFactor")) {
            	    dialog.roiImageScaleFactor.setValue(Double.parseDouble(confs.get("roiImageScaleFactor").getValue()));
            	}
            	if (confs.containsKey("imageWidth")) {
            	    dialog.imageWidth.setValue(Integer.parseInt(confs.get("imageWidth").getValue()));
            	}
            	if (confs.containsKey("imageHeight")) {
            	    dialog.imageHeight.setValue(Integer.parseInt(confs.get("imageHeight").getValue()));
            	}
                return dialog;
            }
            @Override
            public ValidationError[] validate() {
            	List<ValidationError> errors = new ArrayList<ValidationError>();

            	Double hiResPixelSize = (Double)dialog.hiResPixelSize.getValue();
            	if (hiResPixelSize == null || hiResPixelSize == 0.0) {
            	    errors.add(new ValidationError(ROIFinder.this.workflowModule.getName(), "Please enter a nonzero value for hiResPixelSize"));
            	}

            	Double minRoiArea = (Double)dialog.minRoiArea.getValue();
            	if (minRoiArea == null || minRoiArea == 0.0) {
            	    errors.add(new ValidationError(ROIFinder.this.workflowModule.getName(), "Please enter a nonzero value for Min ROI Area"));
            	}
            	Double overlapPct = (Double)dialog.overlapPct.getValue();
            	if (overlapPct == null || overlapPct < 0.0 || overlapPct > 100.0) {
            	    errors.add(new ValidationError(ROIFinder.this.workflowModule.getName(), 
            	            "Please enter a value between 0 and 100 for Tile Percent Overlap"));
            	}
            	Double roiMarginPct = (Double)dialog.roiMarginPct.getValue();
            	if (roiMarginPct == null || roiMarginPct < 0.0 || roiMarginPct > 100.0) {
            	    errors.add(new ValidationError(ROIFinder.this.workflowModule.getName(), 
            	            "Please enter a value between 0 and 100 for ROI Margin Percent"));
            	}
            	Double roiImageScaleFactor = (Double)dialog.roiImageScaleFactor.getValue();
            	if (roiImageScaleFactor == null || roiImageScaleFactor <= 0.0) {
            	    errors.add(new ValidationError(ROIFinder.this.workflowModule.getName(), 
            	            "Please enter a value greater than 0.0 ROI Image Scale Factor"));
            	}
            	Integer imageWidth = (Integer)dialog.imageWidth.getValue();
            	if (imageWidth == null || imageWidth <= 0.0) {
            	    errors.add(new ValidationError(ROIFinder.this.workflowModule.getName(), 
            	            "Please enter a value greater than 0 for the HiRes Image Width"));
            	}
            	Integer imageHeight = (Integer)dialog.imageHeight.getValue();
            	if (imageHeight == null || imageHeight <= 0.0) {
            	    errors.add(new ValidationError(ROIFinder.this.workflowModule.getName(), 
            	            "Please enter a value greater than 0 for the HiRes Image Height"));
            	}
                return errors.toArray(new ValidationError[0]);
            }
        };
    }

    @Override
    public List<Task> createTaskRecords(List<Task> parentTasks, Map<String,Config> config, Logger logger) {
        Dao<TaskConfig> taskConfigDao = workflowRunner.getTaskConfig();
        Dao<SlidePosList> slidePosListDao = workflowRunner.getWorkflowDb().table(SlidePosList.class);
        Dao<SlidePos> slidePosDao = workflowRunner.getWorkflowDb().table(SlidePos.class);
        Dao<Slide> slideDao = workflowRunner.getWorkflowDb().table(Slide.class);

        WorkflowModule module = workflowRunner.getWorkflow().selectOneOrDie(where("id",this.workflowModule.getId()));
        List<Task> tasks = new ArrayList<Task>();
        if (module.getParentId() != null) {
            for (Task parentTask : parentTasks) {
            	workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: attaching to parent Task: %s",
            			this.workflowModule.getName(), parentTask));
                Task task = new Task(workflowModule.getId(), Status.NEW);
                workflowRunner.getTaskStatus().insert(task);
                tasks.add(task);
            	workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Created new task record: %s",
            			this.workflowModule.getName(), task));
            	
            	// pass along the slideId conf to any child tasks
            	TaskConfig slideIdConf = taskConfigDao.selectOne(
            	        where("id",parentTask.getId()).
            	        and("key", "slideId"));
            	if (slideIdConf != null) {
                    // create taskConfig record for the slide ID
                    TaskConfig slideId = new TaskConfig(
                            task.getId(),
                            "slideId", 
                            slideIdConf.getValue());
                    taskConfigDao.insert(slideId);
                    workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Created task config: %s", 
                            this.workflowModule.getName(), slideId));
                    
                    // delete any old slidepos and slideposlist records
                    Slide slide = slideDao.selectOneOrDie(where("id",Integer.parseInt(slideIdConf.getValue())));
                    logger.fine(String.format("Deleting old position lists"));
                    List<SlidePosList> oldSlidePosLists = slidePosListDao.select(
                            where("slideId",slide.getId()).
                            and("moduleId",this.workflowModule.getId()));
                    for (SlidePosList oldSlidePosList : oldSlidePosLists) {
                        slidePosDao.delete(where("slidePosListId",oldSlidePosList.getId()));
                    }
                    slidePosListDao.delete(where("slideId",slide.getId()).and("moduleId",this.workflowModule.getId()));
            	}

                TaskDispatch dispatch = new TaskDispatch(task.getId(), parentTask.getId());
                workflowRunner.getTaskDispatch().insert(dispatch);
            	workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Created new task dispatch record: %s",
            			this.workflowModule.getName(), dispatch));
            }
        }
        return tasks;
    }

	@Override
	public TaskType getTaskType() {
		return Module.TaskType.PARALLEL;
	}

	@Override public void cleanup(Task task, Map<String,Config> config, Logger logger) { }

    @Override
    public List<ImageLogRecord> logImages(final Task task, final Map<String,Config> config, final Logger logger) {
        List<ImageLogRecord> imageLogRecords = new ArrayList<ImageLogRecord>();

        // Add an image logger instance to the workflow runner for this module
        imageLogRecords.add(new ImageLogRecord(task.getName(workflowRunner.getWorkflow()), task.getName(workflowRunner.getWorkflow()),
                new FutureTask<ImageLogRunner>(new Callable<ImageLogRunner>() {
            @Override public ImageLogRunner call() throws Exception {
                // Get the Image record
                Config imageIdConf = config.get("imageId");
                if (imageIdConf != null) {
                    Integer imageId = Integer.parseInt(imageIdConf.getValue());

                    logger.info(String.format("Using image ID: %d", imageId));
                    Dao<Image> imageDao = workflowRunner.getWorkflowDb().table(Image.class);
                    final Image image = imageDao.selectOneOrDie(where("id",imageId));
                    logger.info(String.format("Using image: %s", image));
                    
                    org.micromanager.data.Image mmimage;
                    if (image.getPath() != null) {
                        mmimage = image.getImage(workflowRunner);
                    }
                    else {
                        // Initialize the acquisition
                        Dao<Acquisition> acqDao = workflowRunner.getWorkflowDb().table(Acquisition.class);
                        Acquisition acquisition = acqDao.selectOneOrDie(where("id",image.getAcquisitionId()));
                        logger.info(String.format("Using acquisition: %s", acquisition));
                        Datastore datastore = acquisition.getDatastore();

                        // Get the image cache object
                        if (datastore == null) throw new RuntimeException("Acquisition was not initialized; imageCache is null!");
                        // Get the tagged image from the image cache
                        mmimage = image.getImage(datastore);
                        logger.info(String.format("Got mmimage from Datastore: %s", mmimage));
                    }
                    ImageLogRunner imageLogRunner = new ImageLogRunner(task.getName(workflowRunner.getWorkflow()));
                    ROIFinder.this.process(image, mmimage, logger, imageLogRunner, config);
                    return imageLogRunner;
                }
                return null;
            }
        })));
        return imageLogRecords;
    }

    @Override
    public void runInitialize() { }
    
    @Override
    public Status setTaskStatusOnResume(Task task) {
        if (task.getStatus() != Status.SUCCESS) {
            return Task.Status.NEW;
        }
        List<TaskDispatch> tds = this.workflowRunner.getTaskDispatch().select(where("taskId", task.getId()));
        while (!tds.isEmpty()) {
            List<TaskDispatch> newTds = new ArrayList<>();
            for (TaskDispatch td : tds) {
                Task t = this.workflowRunner.getTaskStatus().selectOneOrDie(where("id", td.getParentTaskId()));
                if (t.getStatus() != Status.SUCCESS) {
                    return Status.NEW;
                }
                if (this.workflowRunner.getModuleConfig().selectOne(
                        where("id", t.getModuleId()).
                        and("key", "canImageSlides").
                        and("value", "yes")) == null) 
                {
                    newTds.addAll(this.workflowRunner.getTaskDispatch().select(where("taskId", t.getId())));
                }
            }
            tds.clear();
            tds.addAll(newTds);
        }
        return null;
    }
}
