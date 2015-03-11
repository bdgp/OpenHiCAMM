package org.bdgp.OpenHiCAMM.Modules;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;

import org.bdgp.OpenHiCAMM.Dao;
import org.bdgp.OpenHiCAMM.Logger;
import org.bdgp.OpenHiCAMM.OpenHiCAMM;
import org.bdgp.OpenHiCAMM.Util;
import org.bdgp.OpenHiCAMM.ValidationError;
import org.bdgp.OpenHiCAMM.WorkflowRunner;
import org.bdgp.OpenHiCAMM.DB.Acquisition;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.Image;
import org.bdgp.OpenHiCAMM.DB.ROI;
import org.bdgp.OpenHiCAMM.DB.Slide;
import org.bdgp.OpenHiCAMM.DB.SlidePos;
import org.bdgp.OpenHiCAMM.DB.SlidePosList;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.Task.Status;
import org.bdgp.OpenHiCAMM.DB.TaskDispatch;
import org.bdgp.OpenHiCAMM.DB.WorkflowModule;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Configuration;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Module;
import org.json.JSONException;
import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.api.ImageCache;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.api.StagePosition;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.MMSerializationException;

import static org.bdgp.OpenHiCAMM.Util.where;

/**
 * Return x/y/len/width of bounding box surrounding the ROI
 */
public class ROIFinder implements Module {
    WorkflowRunner workflowRunner;
    String moduleId;
    ScriptInterface script;

    @Override
    public void initialize(WorkflowRunner workflowRunner, String moduleId) {
        this.workflowRunner = workflowRunner;
        this.moduleId = moduleId;
        OpenHiCAMM mmslide = workflowRunner.getOpenHiCAMM();
        this.script = mmslide.getApp();
    }

    @Override
    public Status run(Task task, Map<String,Config> config, Logger logger) {
    	logger.info(String.format("Running task: %s", task));
    	for (Config c : config.values()) {
            logger.info(String.format("Using config: %s", c));
    	}

        // Get the Image record
    	Config imageIdConf = config.get("imageId");
    	if (imageIdConf == null) throw new RuntimeException("No imageId task configuration was set by the slide imager!");
        Integer imageId = new Integer(imageIdConf.getValue());
        logger.info(String.format("Using image ID: %d", imageId));

        Dao<Image> imageDao = workflowRunner.getInstanceDb().table(Image.class);
        Image image = imageDao.selectOneOrDie(where("id",imageId));
        logger.info(String.format("Using image: %s", image));
        
        Dao<Slide> slideDao = workflowRunner.getInstanceDb().table(Slide.class);
        Slide slide = slideDao.selectOneOrDie(where("id",image.getSlideId()));
        logger.info(String.format("Using slide: %s", slide));
        
        // Get the taggedImage object
        TaggedImage taggedImage;
        if (!config.containsKey("taggedImage")) {
        	logger.info("Running in offline mode");
            // Initialize the acquisition
            Dao<Acquisition> acqDao = workflowRunner.getInstanceDb().table(Acquisition.class);
            Acquisition acquisition = acqDao.selectOneOrDie(where("id",image.getAcquisitionId()));
        	logger.info(String.format("Using acquisition: %s", acquisition));
            MMAcquisition mmacquisition = acquisition.getAcquisition();
            try { mmacquisition.initialize(); } 
            catch (MMScriptException e) {throw new RuntimeException(e);}
        	logger.info(String.format("Initialized acquisition"));

            // Get the image cache object
            ImageCache imageCache = mmacquisition.getImageCache();
            if (imageCache == null) throw new RuntimeException("Acquisition was not initialized; imageCache is null!");
            // Get the tagged image from the image cache
            taggedImage = image.getImage(imageCache);
        }
        else {
        	logger.info("Running in online mode");
        	taggedImage = (TaggedImage)config.get("taggedImage").getObject();
        }
        logger.info(String.format("Got taggedImage: %s", taggedImage));

    	try {
			int positionIndex = MDUtils.getPositionIndex(taggedImage.tags);
			logger.info(String.format("Using positionIndex: %d", positionIndex));

			double pixelSizeUm = MDUtils.getPixelSizeUm(taggedImage.tags);
			logger.info(String.format("Using pixedSizeUm: %f", pixelSizeUm));
			
			int imageWidth = MDUtils.getWidth(taggedImage.tags);
			int imageHeight = MDUtils.getHeight(taggedImage.tags);

			// Fill in list of ROIs
			logger.info(String.format("Running process() to get list of ROIs"));
			List<ROI> rois = process(image, taggedImage, logger);
			
			// get the current stage position
            double[] x_stage = new double[] {0.0};
            double[] y_stage = new double[] {0.0};
            CMMCore core = workflowRunner.getOpenHiCAMM().getApp().getMMCore();
            String xyStage = core.getXYStageDevice();
            try { core.getXYPosition(xyStage, x_stage, y_stage); } 
            catch (Exception e) { throw new RuntimeException(e); }

            // Convert the ROIs into a PositionList
			PositionList posList = new PositionList();
			for (ROI roi : rois) {
				MultiStagePosition msp = new MultiStagePosition();
				StagePosition sp = new StagePosition();
				sp.numAxes = 2;
				sp.stageName = "XYStage";
				sp.x = x_stage[0]+(((((double)roi.getX1()+(double)roi.getX2())/2.0)-(double)imageWidth)*pixelSizeUm);
				sp.y = y_stage[0]+(((((double)roi.getY1()+(double)roi.getY2())/2.0)-(double)imageHeight)*pixelSizeUm);
				msp.setLabel(roi.toString());
				msp.add(sp);
				msp.setDefaultXYStage("XYStage");
				posList.addPosition(msp);
				logger.info(String.format("Storing StagePosition(numAxes: %d, stageName: %s, x=%.2f, y=%.2f)",
						sp.numAxes, Util.escape(sp.stageName), sp.x, sp.y));
			}
			try { logger.info(String.format("Produced position list of ROIs:%n%s", posList.serialize())); } 
			catch (MMSerializationException e) {throw new RuntimeException(e);}

			Dao<SlidePosList> slidePosListDao = workflowRunner.getInstanceDb().table(SlidePosList.class);
            Dao<SlidePos> slidePosDao = workflowRunner.getInstanceDb().table(SlidePos.class);

			// First, delete any old slidepos and slideposlist records
			logger.info(String.format("Deleting old position lists"));
			List<SlidePosList> oldSlidePosLists = slidePosListDao.select(
					where("slideId",slide.getId()).and("moduleId",this.moduleId));
			for (SlidePosList oldSlidePosList : oldSlidePosLists) {
				slidePosDao.delete(where("slidePosListId",oldSlidePosList.getId()));
			}
			slidePosListDao.delete(where("slideId",slide.getId()).and("moduleId",this.moduleId));

			// Create SlidePosList and SlidePos DB records
			SlidePosList slidePosList = new SlidePosList(this.moduleId, slide, posList);
			slidePosListDao.insert(slidePosList);
			logger.info(String.format("Created new SlidePosList: %s", slidePosList));

			MultiStagePosition[] msps = posList.getPositions();
			for (int i=0; i<msps.length; ++i) {
				SlidePos slidePos = new SlidePos(slidePosList.getId(), i, rois.get(i).getId());
				slidePosDao.insert(slidePos);
                logger.info(String.format("Created new SlidePos record for position %d: %s", i, slidePos));
			}
			return Status.SUCCESS;
		} 
    	catch (JSONException e) { throw new RuntimeException(e); }
    }
    
    public List<ROI> process(Image image, TaggedImage taggedImage, Logger logger) {
    	List<ROI> rois = new ArrayList<ROI>();
    	try {
            int width = MDUtils.getWidth(taggedImage.tags);
            int height = MDUtils.getHeight(taggedImage.tags);
            logger.info(String.format("Image dimensions: (%d,%d)", width, height));

    		int roiCount = (int)(Math.random() * 10.0 + 10.0);
    		for (int i=0; i<roiCount; ++i) {
                int roiWidth = (int)(Math.random() * 100.0 + 150.0);
                int roiHeight = (int)(Math.random() * 100.0 + 150.0);
                int roiX = (int)(Math.random() * (width-roiWidth));
                int roiY = (int)(Math.random() * (height-roiHeight));
                ROI roi = new ROI(image.getId(), roiX, roiY, roiX+roiWidth, roiY+roiHeight);
                logger.info(String.format("Created new ROI record: %s", roi));
                rois.add(roi);
    		}
    	} catch (JSONException e) {throw new RuntimeException(e);}
    	return rois;
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
            ROIFinderDialog dialog = new ROIFinderDialog();
            @Override
            public Config[] retrieve() {
            	List<Config> configs = new ArrayList<Config>();
                return configs.toArray(new Config[0]);
            }
            @Override
            public Component display(Config[] configs) {
            	Map<String,Config> confs = new HashMap<String,Config>();
            	for (Config c : configs) {
            		confs.put(c.getKey(), c);
            	}
                return dialog;
            }
            @Override
            public ValidationError[] validate() {
            	List<ValidationError> errors = new ArrayList<ValidationError>();
                return errors.toArray(new ValidationError[0]);
            }
        };
    }

    @Override
    public List<Task> createTaskRecords(List<Task> parentTasks) {
        WorkflowModule module = workflowRunner.getWorkflow().selectOneOrDie(where("id",moduleId));
        List<Task> tasks = new ArrayList<Task>();
        if (module.getParentId() != null) {
            for (Task parentTask : parentTasks) {
            	workflowRunner.getLogger().info(String.format("%s: createTaskRecords: attaching to parent Task: %s",
            			this.moduleId, parentTask));
                Task task = new Task(moduleId, Status.NEW);
                workflowRunner.getTaskStatus().insert(task);
                task.createStorageLocation(
                		parentTask.getStorageLocation(), 
                		new File(workflowRunner.getWorkflowDir(),
                				workflowRunner.getInstance().getStorageLocation()).getPath());
                workflowRunner.getTaskStatus().update(task,"id");
                tasks.add(task);
            	workflowRunner.getLogger().info(String.format("%s: createTaskRecords: Created new task record: %s",
            			this.moduleId, task));
                
                TaskDispatch dispatch = new TaskDispatch(task.getId(), parentTask.getId());
                workflowRunner.getTaskDispatch().insert(dispatch);
            	workflowRunner.getLogger().info(String.format("%s: createTaskRecords: Created new task dispatch record: %s",
            			this.moduleId, dispatch));
            }
        }
        return tasks;
    }

	@Override
	public TaskType getTaskType() {
		return Module.TaskType.PARALLEL;
	}

	@Override public void cleanup(Task task) { }
}
