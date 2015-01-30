package org.bdgp.MMSlide.Modules;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mmcorej.TaggedImage;

import org.bdgp.MMSlide.Dao;
import org.bdgp.MMSlide.Logger;
import org.bdgp.MMSlide.MMSlide;
import org.bdgp.MMSlide.ValidationError;
import org.bdgp.MMSlide.WorkflowRunner;
import org.bdgp.MMSlide.DB.Acquisition;
import org.bdgp.MMSlide.DB.Config;
import org.bdgp.MMSlide.DB.Image;
import org.bdgp.MMSlide.DB.ROI;
import org.bdgp.MMSlide.DB.Slide;
import org.bdgp.MMSlide.DB.SlidePos;
import org.bdgp.MMSlide.DB.SlidePosList;
import org.bdgp.MMSlide.DB.Task;
import org.bdgp.MMSlide.DB.Task.Status;
import org.bdgp.MMSlide.DB.TaskDispatch;
import org.bdgp.MMSlide.DB.WorkflowModule;
import org.bdgp.MMSlide.Modules.Interfaces.Configuration;
import org.bdgp.MMSlide.Modules.Interfaces.Module;
import org.json.JSONException;
import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.api.ImageCache;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;

import static org.bdgp.MMSlide.Util.where;

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
        MMSlide mmslide = workflowRunner.getMMSlide();
        this.script = mmslide.getApp();
    }

    @Override
    public Status run(Task task, Map<String,Config> config, Logger logger) {
        // Get the Image record
    	Config imageIdConf = config.get("imageId");
    	if (imageIdConf == null) throw new RuntimeException("No imageId task configuration was set by the slide imager!");
        Integer imageId = new Integer(imageIdConf.getValue());
        Dao<Image> imageDao = workflowRunner.getInstanceDb().table(Image.class);
        Image image = imageDao.selectOneOrDie(where("id",imageId));
        
        Dao<Slide> slideDao = workflowRunner.getInstanceDb().table(Slide.class);
        logger.info(String.format("Getting slide ID for image: %s", image));
        Slide slide = slideDao.selectOneOrDie(where("id",image.getSlideId()));
        
        // Get the taggedImage object
        TaggedImage taggedImage;
        if (!config.containsKey("taggedImage")) {
            // Initialize the acquisition
            Dao<Acquisition> acqDao = workflowRunner.getInstanceDb().table(Acquisition.class);
            Acquisition acquisition = acqDao.selectOneOrDie(where("id",image.getAcquisitionId()));
            MMAcquisition mmacquisition = acquisition.getAcquisition();
            try { mmacquisition.initialize(); } 
            catch (MMScriptException e) {throw new RuntimeException(e);}
            // Get the image cache object
            ImageCache imageCache = mmacquisition.getImageCache();
            if (imageCache == null) throw new RuntimeException("Acquisition was not initialized; imageCache is null!");
            // Get the tagged image from the image cache
            taggedImage = image.getImage(imageCache);
        }
        else {
        	taggedImage = (TaggedImage)config.get("taggedImage").getObject();
        }

    	try {
			int positionIndex = MDUtils.getPositionIndex(taggedImage.tags);
			double pixelSizeUm = MDUtils.getPixelSizeUm(taggedImage.tags);
			logger.info(String.format("Processed image at position %d", positionIndex));

			// Fill in list of ROIs
			List<ROI> rois = process(image, taggedImage, logger);

            // Convert the ROIs into a PositionList
			PositionList posList = new PositionList();
			for (ROI roi : rois) {
				posList.addPosition(new MultiStagePosition(
						"xyStage", 
						((roi.getX1()+roi.getX2())/2.0)*pixelSizeUm, 
						((roi.getY1()+roi.getY2())/2.0)*pixelSizeUm, 
						"zStage", 0.0));
			}

			Dao<SlidePosList> slidePosListDao = workflowRunner.getInstanceDb().table(SlidePosList.class);
            Dao<SlidePos> slidePosDao = workflowRunner.getInstanceDb().table(SlidePos.class);

			// First, delete any old slidepos and slideposlist records
			List<SlidePosList> oldSlidePosLists = slidePosListDao.select(
					where("slideId",slide.getId()).and("moduleId",this.moduleId));
			for (SlidePosList oldSlidePosList : oldSlidePosLists) {
				slidePosDao.delete(where("slidePosListId",oldSlidePosList.getId()));
			}
			slidePosListDao.delete(where("slideId",slide.getId()).and("moduleId",this.moduleId));

			// Create SlidePosList and SlidePos DB records
			SlidePosList slidePosList = new SlidePosList(this.moduleId, slide, posList);
			slidePosListDao.insert(slidePosList);

			MultiStagePosition[] msps = posList.getPositions();
			for (int i=0; i<msps.length; ++i) {
				slidePosDao.insert(new SlidePos(slidePosList.getId(), i, rois.get(i).getId()));
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
    public void createTaskRecords() {
        WorkflowModule module = workflowRunner.getWorkflow().selectOneOrDie(where("id",moduleId));
        if (module.getParentId() != null) {
            List<Task> parentTasks = workflowRunner.getTaskStatus().select(where("moduleId",module.getParentId()));
            for (Task parentTask : parentTasks) {
                Task task = new Task(moduleId, Status.NEW);
                workflowRunner.getTaskStatus().insert(task);
                task.createStorageLocation(
                		parentTask.getStorageLocation(), 
                		new File(workflowRunner.getWorkflowDir(),
                				workflowRunner.getInstance().getStorageLocation()).getPath());
                workflowRunner.getTaskStatus().update(task,"id");
                
                TaskDispatch dispatch = new TaskDispatch(task.getId(), parentTask.getId());
                workflowRunner.getTaskDispatch().insert(dispatch);
            }
        }
    }

	@Override
	public TaskType getTaskType() {
		return Module.TaskType.PARALLEL;
	}

	@Override public void cleanup(Task task) { }
}
