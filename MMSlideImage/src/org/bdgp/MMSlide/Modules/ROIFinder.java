package org.bdgp.MMSlide.Modules;

import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bdgp.MMSlide.Dao;
import org.bdgp.MMSlide.Logger;
import org.bdgp.MMSlide.MMSlide;
import org.bdgp.MMSlide.ValidationError;
import org.bdgp.MMSlide.WorkflowRunner;
import org.bdgp.MMSlide.DB.Config;
import org.bdgp.MMSlide.DB.Image;
import org.bdgp.MMSlide.DB.ModuleConfig;
import org.bdgp.MMSlide.DB.ROI;
import org.bdgp.MMSlide.DB.SlidePos;
import org.bdgp.MMSlide.DB.SlidePosList;
import org.bdgp.MMSlide.DB.Task;
import org.bdgp.MMSlide.DB.Task.Status;
import org.bdgp.MMSlide.DB.TaskDispatch;
import org.bdgp.MMSlide.DB.WorkflowModule;
import org.bdgp.MMSlide.Modules.Interfaces.Configuration;
import org.bdgp.MMSlide.Modules.Interfaces.Module;
import org.json.JSONException;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.MDUtils;

import static org.bdgp.MMSlide.Util.where;
import static org.bdgp.MMSlide.Util.map;

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

    	try {
			int positionIndex = MDUtils.getPositionIndex(image.getTags());
			double pixelSizeUm = MDUtils.getPixelSizeUm(image.getTags());
			String fileName = image.getPath();
			logger.info(String.format("Processed image at position %d, filename %s",positionIndex,fileName));

			// get the name of the SlidePosList we're writing to
			ModuleConfig slidePosListNameConf = workflowRunner.getModuleConfig().selectOne(
					where("id",this.moduleId).and("key","slidePosListName"));
			String slidePosListName = slidePosListNameConf != null? slidePosListNameConf.getValue() : "default";
			
			// Fill in list of ROIs
			List<ROI> rois = process(image);

            // Convert the ROIs into a PositionList
			PositionList posList = new PositionList();
			for (ROI roi : rois) {
				posList.addPosition(new MultiStagePosition(
						"xyStage", 
						((roi.getX1()+roi.getX2())/2.0)*pixelSizeUm, 
						((roi.getY1()+roi.getY2())/2.0)*pixelSizeUm, 
						"zStage", 0.0));
			}

			// Create SlidePosList and SlidePos DB records
			Dao<SlidePosList> slidePosListDao = workflowRunner.getInstanceDb().table(SlidePosList.class);
			slidePosListDao.delete(where("name",slidePosListName));
			SlidePosList slidePosList = new SlidePosList(slidePosListName, posList);
			slidePosListDao.insert(slidePosList);

			MultiStagePosition[] msps = posList.getPositions();
            Dao<SlidePos> slidePosDao = workflowRunner.getInstanceDb().table(SlidePos.class);
			for (int i=0; i<msps.length; ++i) {
				slidePosDao.insert(new SlidePos(slidePosList.getId(), i, rois.get(i).getId()));
			}
			return Status.SUCCESS;
		} 
    	catch (JSONException e) { throw new RuntimeException(e); }
    }
    
    public List<ROI> process(Image image) {
    	List<ROI> rois = new ArrayList<ROI>();
    	String imagePath = image.getPath();
    	// TODO: fill in ROI list here
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
            	if (dialog.posListName.getText().length()>0) {
            		configs.add(new Config(ROIFinder.this.moduleId, "posListName",dialog.posListName.getText()));
            	}
                return configs.toArray(new Config[0]);
            }
            @Override
            public Component display(Config[] configs) {
            	Map<String,Config> confs = new HashMap<String,Config>();
            	for (Config c : configs) {
            		confs.put(c.getKey(), c);
            	}
            	if (confs.containsKey("posListName")) {
            		String posListName = confs.get("posListName").getValue();
                    dialog.posListName.setText(posListName);
            	}
                return dialog;
            }
            @Override
            public ValidationError[] validate() {
            	List<ValidationError> errors = new ArrayList<ValidationError>();
            	if (dialog.posListName.getText().length() == 0) {
            		errors.add(new ValidationError(ROIFinder.this.moduleId, 
            				"A position list name must be entered."));
            	}
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
                task.createStorageLocation(parentTask.getStorageLocation(), workflowRunner.getInstance().getStorageLocation());
                workflowRunner.getTaskStatus().update(task,"id");
                
                TaskDispatch dispatch = new TaskDispatch(task.getId(), parentTask.getId());
                workflowRunner.getTaskDispatch().insert(dispatch);
            }
        }
    }

    @Override
    public Map<String, Integer> getResources() {
        return map("cpu",1);
    }

	@Override
	public TaskType getTaskType() {
		return Module.TaskType.PARALLEL;
	}
}
