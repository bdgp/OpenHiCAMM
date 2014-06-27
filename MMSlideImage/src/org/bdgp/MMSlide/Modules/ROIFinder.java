package org.bdgp.MMSlide.Modules;

import java.awt.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import mmcorej.TaggedImage;

import org.bdgp.MMSlide.Dao;
import org.bdgp.MMSlide.Logger;
import org.bdgp.MMSlide.MMSlide;
import org.bdgp.MMSlide.ValidationError;
import org.bdgp.MMSlide.WorkflowRunner;
import org.bdgp.MMSlide.DB.Config;
import org.bdgp.MMSlide.DB.Task;
import org.bdgp.MMSlide.DB.Task.Status;
import org.bdgp.MMSlide.DB.TaskConfig;
import org.bdgp.MMSlide.DB.TaskDispatch;
import org.bdgp.MMSlide.DB.WorkflowModule;
import org.bdgp.MMSlide.Modules.Interfaces.Configuration;
import org.bdgp.MMSlide.Modules.Interfaces.Module;
import org.json.JSONException;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.api.ImageCache;
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
    	Dao<TaskDispatch> taskDispatchDao = this.workflowRunner.getInstanceDb().table(TaskDispatch.class);
    	Dao<Task> taskDao = this.workflowRunner.getInstanceDb().table(Task.class);
    	Dao<TaskConfig> taskConfigDao = this.workflowRunner.getInstanceDb().table(TaskConfig.class);
    	List<TaskDispatch> parentTasks = taskDispatchDao.select(where("taskId",task.getId()));
    	for (TaskDispatch parent : parentTasks) {
    		Task parentTask = taskDao.selectOneOrDie(where("id",parent.getParentTaskId()));
    		// get a map of the parent's task configuration
    		Map<String,Config> configs = new HashMap<String,Config>();
    		for (TaskConfig c : taskConfigDao.select(where("id",parentTask.getId()))) {
    			configs.put(c.getKey(), c);
    		}
    		// Get the imageLabel from the Task Configuration, then convert it into the channel, slice,
    		// frame, and position indices. These indices are used to get the TaggedImage out of the
    		// ImageCache.
            ImageCache imageCache = ((MMStudioMainFrame)this.script).getAcquisitionEngine().getImageCache();
    		String imageLabel = configs.get("imageLabel").getValue();
    		int[] indices = MDUtils.getIndices(imageLabel);
            TaggedImage taggedImage = imageCache.getImage(indices[0], indices[1], indices[2], indices[3]);
            Status status = process(task, config, logger, taggedImage);
            if (status != Status.SUCCESS) return status;
    	}
        return Status.SUCCESS;
    }
    
    // TOOD: Fill in image processing code here.
    public Status process(Task task, Map<String,Config> config, Logger logger, TaggedImage taggedImage) {
    	try {
			int positionIndex = MDUtils.getPositionIndex(taggedImage.tags);
			String fileName = MDUtils.getFileName(taggedImage.tags);
			logger.info(String.format("Processed image at position %d, filename %s",positionIndex,fileName));
			return Status.SUCCESS;
		} 
    	catch (JSONException e) { throw new RuntimeException(e); }
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
            @Override
            public Config[] retrieve() {
                return new Config[0];
            }
            @Override
            public Component display(Config[] configs) {
                return new JPanel();
            }
            @Override
            public ValidationError[] validate() {
                return null;
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
                task.createStorageLocation(parentTask.getStorageLocation(), workflowRunner.getWorkflowDirectory().getPath());
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
