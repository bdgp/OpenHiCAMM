package org.bdgp.OpenHiCAMM.Modules;

import java.awt.Component;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import mmcorej.TaggedImage;

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
import org.bdgp.OpenHiCAMM.DB.TaskDispatch;
import org.bdgp.OpenHiCAMM.DB.WorkflowModule;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Configuration;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.ImageLogger;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Module;
import org.json.JSONException;
import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.api.ImageCache;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.api.StagePosition;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMSerializationException;

import static org.bdgp.OpenHiCAMM.Util.where;

/**
 * Return x/y/len/width of bounding box surrounding the ROI
 */
public abstract class ROIFinder implements Module, ImageLogger {
    protected WorkflowRunner workflowRunner;
    protected WorkflowModule workflowModule;
    protected ScriptInterface script;

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
        Integer imageId = new Integer(imageIdConf.getValue());
        Dao<Image> imageDao = workflowRunner.getInstanceDb().table(Image.class);
        final Image image = imageDao.selectOneOrDie(where("id",imageId));

        // get the tagged image
        TaggedImage taggedImage = getTaggedImage(image, logger);

        // get the image label and position name
        String positionName = null;
        try { positionName = MDUtils.getPositionName(taggedImage.tags); } 
        catch (JSONException e) {throw new RuntimeException(e);} 
        String imageLabel = image.getLabel();
        String label = String.format("%s (%s)", positionName, imageLabel); 
        
        logger.fine(String.format("%s: Using image: %s", label, image));
        logger.fine(String.format("%s: Using image ID: %d", label, imageId));
        
        Dao<Slide> slideDao = workflowRunner.getInstanceDb().table(Slide.class);
        Slide slide = slideDao.selectOneOrDie(where("id",image.getSlideId()));
        logger.fine(String.format("%s: Using slide: %s", label, slide));
        
    	try {
			double pixelSize = new Double(config.get("pixelSize").getValue());
			logger.fine(String.format("%s: Using pixelSize: %f", label, pixelSize));
			
			double hiResPixelSize = new Double(config.get("hiResPixelSize").getValue());
			logger.fine(String.format("%s: Using hiResPixelSize: %f", label, hiResPixelSize));

			double roiMarginPct = new Double(config.get("roiMarginPct").getValue());
			logger.fine(String.format("%s: Using ROI margin size in percent of image size: %f", label, roiMarginPct));

			int positionIndex = MDUtils.getPositionIndex(taggedImage.tags);
			logger.fine(String.format("%s: Using positionIndex: %d", label, positionIndex));

			double overlapPct = new Double(config.get("overlapPct").getValue());
			logger.fine(String.format("%s: Using percentage overlap: %f", label, overlapPct));

            // get invertXAxis and invertYAxis conf values
            Config invertXAxisConf = config.get("invertXAxis");
            boolean invertXAxis = invertXAxisConf == null || "yes".equals(invertXAxisConf.getValue());
            Config invertYAxisConf = config.get("invertYAxis");
            boolean invertYAxis = invertYAxisConf == null || "yes".equals(invertYAxisConf.getValue());

			int imageWidth = MDUtils.getWidth(taggedImage.tags);
			int imageHeight = MDUtils.getHeight(taggedImage.tags);
			
			double x_stage = MDUtils.getXPositionUm(taggedImage.tags);
			double y_stage = MDUtils.getYPositionUm(taggedImage.tags);
			
			// Delete any old ROI records
            Dao<ROI> roiDao = this.workflowRunner.getInstanceDb().table(ROI.class);
			int deleted = roiDao.delete(where("imageId", imageId));
			logger.fine(String.format("%s: Deleted %d old ROI records.", label, deleted));
			
			// Fill in list of ROIs
			logger.fine(String.format("%s: Running process() to get list of ROIs", label));
			List<ROI> rois = new ArrayList<ROI>();
			try {
                rois = process(image, taggedImage, logger, new ImageLog.NullImageLogRunner(), config);
			}
			catch (Exception e) {
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

			    // increase the ROI dimensions to add the margins
			    int roiMarginWidth = (int)Math.floor((roiMarginPct / 100.0) * imageWidth * hiResPixelSize / pixelSize);
			    int roiMarginHeight = (int)Math.floor((roiMarginPct / 100.0) * imageHeight * hiResPixelSize / pixelSize);
			    int roiX1 = roi.getX1() - roiMarginWidth;
			    int roiY1 = roi.getY1() - roiMarginHeight;
			    int roiX2 = roi.getX2() + roiMarginWidth;
			    int roiY2 = roi.getY2() + roiMarginHeight;
			    
			    // We need to potentially create a grid of Stage positions in order to capture all of the 
			    // ROI.
			    int roiWidth = roiX2-roiX1+1;
			    int roiHeight = roiY2-roiY1+1;

			    int tileWidth = (int)Math.floor(((double)imageWidth * hiResPixelSize) / pixelSize);
			    int tileHeight = (int)Math.floor(((double)imageHeight * hiResPixelSize) / pixelSize);

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
                        StagePosition sp = new StagePosition();
                        sp.numAxes = 2;
                        sp.stageName = "XYStage";
                        sp.x = x_stage+((tileX-(double)imageWidth/2.0)*pixelSize)*(invertXAxis? -1.0 : 1.0);
                        sp.y = y_stage+((tileY-(double)imageHeight/2.0)*pixelSize)*(invertYAxis? -1.0 : 1.0);
                        String mspLabel = String.format("%s.%s.ROI%d.X%d.Y%d", 
                                positionName, imageLabel, roi.getId(), x, y);
                        msp.setProperty("stitchGroup", "ROI"+roi.getId());
                        msp.setProperty("ROI", new Integer(roi.getId()).toString());
                        msp.setProperty("tileX", new Integer(x).toString());
                        msp.setProperty("tileY", new Integer(y).toString());
                        msp.setLabel(mspLabel);
                        msp.add(sp);
                        msp.setDefaultXYStage("XYStage");
                        posList.addPosition(msp);
                        roiMap.put(msp, roi);
                        logger.info(String.format("%s: Storing StagePosition(label=%s, numAxes: %d, stageName: %s, x=%.2f, y=%.2f, tileX=%d, tileY=%d)",
                                label, Util.escape(mspLabel), sp.numAxes, Util.escape(sp.stageName), sp.x, sp.y, tileX, tileY));
                    }
			    }
			}
			if (posList.getNumberOfPositions() > 0) {
			    logger.info(String.format("%s: Produced position list of %d ROIs", label, posList.getNumberOfPositions())); 
                try { logger.fine(String.format("%s: Position List:%n%s", label, posList.serialize())); } 
                catch (MMSerializationException e) {throw new RuntimeException(e);}
			}
			else {
			    logger.info(String.format("%s: ROIFinder did not produce any positions for this image", label));
			}

			Dao<SlidePosList> slidePosListDao = workflowRunner.getInstanceDb().table(SlidePosList.class);
            Dao<SlidePos> slidePosDao = workflowRunner.getInstanceDb().table(SlidePos.class);

			// First, delete any old slidepos and slideposlist records
			logger.fine(String.format("Deleting old position lists"));
			List<SlidePosList> oldSlidePosLists = slidePosListDao.select(
					where("slideId",slide.getId()).and("moduleId",this.workflowModule.getId()).and("taskId", task.getId()));
			for (SlidePosList oldSlidePosList : oldSlidePosLists) {
				slidePosDao.delete(where("slidePosListId",oldSlidePosList.getId()));
			}
			slidePosListDao.delete(where("slideId",slide.getId()).and("moduleId",this.workflowModule.getId()).and("taskId", task.getId()));

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
    	catch (JSONException e) { throw new RuntimeException(e); }
    }
    
    public TaggedImage getTaggedImage(Image image, Logger logger) {
        String label = image.getLabel();

        // Initialize the acquisition
        Dao<Acquisition> acqDao = workflowRunner.getInstanceDb().table(Acquisition.class);
        Acquisition acquisition = acqDao.selectOneOrDie(where("id",image.getAcquisitionId()));
        logger.fine(String.format("%s: Using acquisition: %s", label, acquisition));
        int tries = 0;
        final int MAX_TRIES = 30;
        MMAcquisition mmacquisition = null;
        while (tries++ < MAX_TRIES) {
            try {
                mmacquisition = acquisition.getAcquisition(acqDao);
                if (mmacquisition != null) break;
            }
            catch (Throwable e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                logger.warning(String.format("Could not open acquisition %s, trying again...: \n%s", acquisition, sw.toString()));
            }
            try { Thread.sleep(1000); } 
            catch (InterruptedException e) { }
        }
        if (mmacquisition == null) {
            throw new RuntimeException(String.format("Could not open acquisition %s!", acquisition));
        }

        // Get the image cache object
        ImageCache imageCache = mmacquisition.getImageCache();
        if (imageCache == null) throw new RuntimeException("Acquisition was not initialized; imageCache is null!");
        logger.fine(String.format("%s: ImageCache has following images: %s", label, imageCache.imageKeys()));
        logger.fine(String.format("%s: Attempting to grab image %s from imageCache", label, image));
        // Get the tagged image from the image cache
        TaggedImage taggedImage = image.getImage(imageCache);
        if (taggedImage == null) throw new RuntimeException(String.format("Acqusition %s, Image %s is not in the image cache!",
                acquisition, image));
        logger.fine(String.format("%s: Got taggedImage from ImageCache: %s", label, taggedImage));

        return taggedImage;
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
            TaggedImage taggedImage, 
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
                return configs.toArray(new Config[0]);
            }
            @Override
            public Component display(Config[] configs) {
            	Map<String,Config> confs = new HashMap<String,Config>();
            	for (Config c : configs) {
            		confs.put(c.getKey(), c);
            	}

            	if (confs.containsKey("hiResPixelSize")) {
            	    dialog.hiResPixelSize.setValue(new Double(confs.get("hiResPixelSize").getValue()));
            	}
            	if (confs.containsKey("minRoiArea")) {
            	    dialog.minRoiArea.setValue(new Double(confs.get("minRoiArea").getValue()));
            	}
            	if (confs.containsKey("overlapPct")) {
            	    dialog.overlapPct.setValue(new Double(confs.get("overlapPct").getValue()));
            	}
            	if (confs.containsKey("roiMarginPct")) {
            	    dialog.roiMarginPct.setValue(new Double(confs.get("roiMarginPct").getValue()));
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
                return errors.toArray(new ValidationError[0]);
            }
        };
    }

    @Override
    public List<Task> createTaskRecords(List<Task> parentTasks, Map<String,Config> config, Logger logger) {
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
                    Integer imageId = new Integer(imageIdConf.getValue());

                    logger.info(String.format("Using image ID: %d", imageId));
                    Dao<Image> imageDao = workflowRunner.getInstanceDb().table(Image.class);
                    final Image image = imageDao.selectOneOrDie(where("id",imageId));
                    logger.info(String.format("Using image: %s", image));

                    // Initialize the acquisition
                    Dao<Acquisition> acqDao = workflowRunner.getInstanceDb().table(Acquisition.class);
                    Acquisition acquisition = acqDao.selectOneOrDie(where("id",image.getAcquisitionId()));
                    logger.info(String.format("Using acquisition: %s", acquisition));
                    MMAcquisition mmacquisition = acquisition.getAcquisition(acqDao);

                    // Get the image cache object
                    ImageCache imageCache = mmacquisition.getImageCache();
                    if (imageCache == null) throw new RuntimeException("Acquisition was not initialized; imageCache is null!");
                    // Get the tagged image from the image cache
                    TaggedImage taggedImage = image.getImage(imageCache);
                    logger.info(String.format("Got taggedImage from ImageCache: %s", taggedImage));

                    ImageLogRunner imageLogRunner = new ImageLogRunner(task.getName(workflowRunner.getWorkflow()));
                    ROIFinder.this.process(image, taggedImage, logger, imageLogRunner, config);
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
                        where("id", task.getModuleId()).
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
