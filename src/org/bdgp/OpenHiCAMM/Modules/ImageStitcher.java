package org.bdgp.OpenHiCAMM.Modules;

import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import org.bdgp.OpenHiCAMM.Dao;
import org.bdgp.OpenHiCAMM.Logger;
import org.bdgp.OpenHiCAMM.ValidationError;
import org.bdgp.OpenHiCAMM.WorkflowRunner;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.Task.Status;
import org.bdgp.OpenHiCAMM.DB.TaskConfig;
import org.bdgp.OpenHiCAMM.DB.TaskDispatch;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Configuration;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Module;
import org.json.JSONArray;
import org.json.JSONException;

import static org.bdgp.OpenHiCAMM.Util.where;

public class ImageStitcher implements Module {
    WorkflowRunner workflowRunner;
    String moduleId;

    @Override
    public void initialize(WorkflowRunner workflowRunner, String moduleId) {
        this.workflowRunner = workflowRunner;
        this.moduleId = moduleId;
    }
    
    public static class TaskTile {
        public Task task;
        public Map<String,TaskConfig> taskConfig;
        int tileX;
        int tileY;
        public TaskTile(Task task, Map<String,TaskConfig> taskConfig, int tileX, int tileY) {
            this.task = task;
            this.taskConfig = taskConfig;
            this.tileX = tileX;
            this.tileY = tileY;
        }
    }

    @Override
    public Status run(Task task, Map<String,Config> config, Logger logger) {
    	logger.fine(String.format("Running task %s: %s", task.getName(), task));
    	
    	// get the stitch group name
    	Config stitchGroupConf = config.get("stitchGroup");
    	if (stitchGroupConf == null) throw new RuntimeException(String.format(
    	        "%s: stitchGroup config not found!", task.getName()));
    	String stitchGroup = stitchGroupConf.getValue();
    	logger.fine(String.format("Stitching group: %s", stitchGroup));
    	
    	Config stitchTaskIdsConf = config.get("stitchTaskIds");
    	if (stitchTaskIdsConf == null) throw new RuntimeException(String.format(
    	        "%s: stitchTaskIds config not found!", task.getName()));
    	
    	// get the list of stitch task IDs from the JSON array
    	List<Integer> stitchTaskIds = new ArrayList<Integer>();
    	try {
            JSONArray stitchTaskJson = new JSONArray(stitchTaskIdsConf.getValue());
            for (int i=0; i<stitchTaskJson.length(); ++i) {
                Integer stitchTaskId = stitchTaskJson.getInt(i);
                stitchTaskIds.add(stitchTaskId);
            }
    	}
    	catch (JSONException e) {throw new RuntimeException(e);}

        // Organize the tasks into a grid of TaskTiles
        Integer gridWidth = null;
        Integer gridHeight = null;
        List<TaskTile> taskTiles = new ArrayList<TaskTile>();
        for (Integer stitchTaskId : stitchTaskIds) {
            Task stitchTask = this.workflowRunner.getTaskStatus().selectOne(where("id", stitchTaskId));
            if (stitchTask == null) throw new RuntimeException(String.format(
                    "%s: Stitch task with ID %d not found in database!", 
                    task.getName(), stitchTaskId));
            
            // get the task config for each stitch task
            List<TaskConfig> taskConfigs = this.workflowRunner.getTaskConfig().select(where("id", stitchTask.getId()));
            Map<String,TaskConfig> taskConfig = new HashMap<String,TaskConfig>();
            for (TaskConfig tc : taskConfigs) {
                taskConfig.put(tc.getKey(), tc);
            }
            TaskConfig tileXConf = taskConfig.get("tileX");
            if (tileXConf == null) throw new RuntimeException(String.format("Task %s: tileX is null!", stitchTask));
            Integer tileX = new Integer(tileXConf.getValue());
            TaskConfig tileYConf = taskConfig.get("tileY");
            if (tileYConf == null) throw new RuntimeException(String.format("Task %s: tileY is null!", stitchTask));
            Integer tileY = new Integer(tileYConf.getValue());
            if (gridWidth == null || gridWidth < tileX) gridWidth = tileX;
            if (gridHeight == null || gridHeight < tileY) gridHeight = tileX;
            
            TaskTile taskTile = new TaskTile(task, taskConfig, tileX, tileY);
            taskTiles.add(taskTile);
        }
        TaskTile[][] taskGrid = new TaskTile[gridWidth][gridHeight];
        for (TaskTile taskTile : taskTiles) {
            taskGrid[taskTile.tileY][taskTile.tileX] = taskTile;
        }
        
        stitchGrid(taskGrid);
    	
        return Status.SUCCESS;
    }
    
    public void stitchGrid(TaskTile[][] taskGrid) {
        if (taskGrid.length > 1 || taskGrid[0].length > 1) {
            TaskTile[][] grid1;
            TaskTile[][] grid2;
            if (taskGrid.length < taskGrid[0].length) {
                int splitPoint = (int)Math.floor(taskGrid[0].length / 2);
                grid1 = new TaskTile[splitPoint][taskGrid.length];
                for (int r=0; r<splitPoint; ++r) {
                    for (int c=0; c<taskGrid.length; ++c) {
                        grid1[r][c] = taskGrid[r][c];
                    }
                }
                grid2 = new TaskTile[splitPoint][taskGrid.length];
                for (int r=0; r<splitPoint; ++r) {
                    for (int c=0; c<taskGrid.length; ++c) {
                        grid1[r][c] = taskGrid[r][c];
                    }
                }
            }
            else {
                
            }
        }
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
    public List<Task> createTaskRecords(List<Task> parentTasks) {
        Dao<Task> taskDao = this.workflowRunner.getTaskStatus();
        Dao<TaskConfig> taskConfigDao = this.workflowRunner.getTaskConfig();
        Dao<TaskDispatch> taskDispatchDao = this.workflowRunner.getTaskDispatch();

        // Group the parent tasks by stitchGroup name
        Map<String,List<Task>> stitchGroups = new HashMap<String,List<Task>>();
        for (Task parentTask : parentTasks) {
            TaskConfig stitchGroup = taskConfigDao.selectOne(where("id", parentTask.getId()).and("key", "stitchGroup"));
            if (stitchGroup != null) {
                // make sure tileX and tileY are set
                TaskConfig tileX = taskConfigDao.selectOne(where("id", parentTask.getId()).and("key", "tileX"));
                TaskConfig tileY = taskConfigDao.selectOne(where("id", parentTask.getId()).and("key", "tileY"));
                if (tileX == null || tileY == null) {
                    throw new RuntimeException(String.format("tileX and tileY not defined for task: %s", parentTask));
                }
                // add the task to the stitch group
                if (!stitchGroups.containsKey(stitchGroup.getValue())) {
                    stitchGroups.put(stitchGroup.getValue(), new ArrayList<Task>());
                }
                stitchGroups.get(stitchGroup.getValue()).add(parentTask);
            }
        }

        List<Task> tasks = new ArrayList<Task>();
        for (Map.Entry<String, List<Task>> entry : stitchGroups.entrySet()) {
            String stitchGroup = entry.getKey();
            List<Task> stitchTasks = entry.getValue();
            
            // insert the task record
            Task task = new Task(this.moduleId, Status.NEW);
            taskDao.insert(task);
            tasks.add(task);
            
            // add the stitchGroup task config
            TaskConfig stitchGroupConf = new TaskConfig(
                    new Integer(task.getId()).toString(), 
                    "stitchGroup", stitchGroup);
            taskConfigDao.insert(stitchGroupConf);

            // add the stitchTaskIds task config, a JSONArray of stitch task IDs.
            JSONArray stitchTaskIds = new JSONArray();
            for (Task stitchTask : stitchTasks) {
                stitchTaskIds.put(stitchTask.getId());
            }
            TaskConfig stitchTaskIdsConf = new TaskConfig(
                    new Integer(task.getId()).toString(), 
                    "stitchTaskIds", stitchTaskIds.toString());
            taskConfigDao.insert(stitchTaskIdsConf);
                
            // add the task dispatch records
            for (Task stitchTask : stitchTasks) {
                TaskDispatch td = new TaskDispatch(task.getId(), stitchTask.getId());
                taskDispatchDao.insert(td);
            }
        }
        return tasks;
    }

	@Override
	public TaskType getTaskType() {
		return Module.TaskType.PARALLEL;
	}

	@Override public void cleanup(Task task) { }

    @Override
    public void runIntialize() { }
}
