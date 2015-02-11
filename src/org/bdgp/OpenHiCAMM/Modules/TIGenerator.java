package org.bdgp.OpenHiCAMM.Modules;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import org.bdgp.OpenHiCAMM.Logger;
import org.bdgp.OpenHiCAMM.Util;
import org.bdgp.OpenHiCAMM.ValidationError;
import org.bdgp.OpenHiCAMM.WorkflowRunner;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.Task.Status;
import org.bdgp.OpenHiCAMM.DB.TaskDispatch;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Configuration;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Module;

public class TIGenerator implements Module {
    WorkflowRunner workflow;
    String moduleId;

    @Override
    public void initialize(WorkflowRunner workflow, String moduleId) {
        this.workflow = workflow;
        this.moduleId = moduleId;
    }

    @Override
    public Status run(Task task, Map<String,Config> config, Logger logger) {
    	logger.info(String.format("Running task %s: %s", task.getName(), task));
    	logger.info(String.format("This is a *stub* module. Sleeping..."));
        Util.sleep();
        return Status.SUCCESS;
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
            }};
    }

    @Override
    public List<Task> createTaskRecords(List<Task> parentTasks) {
        List<Task> tasks = new ArrayList<Task>();
        for (Task parentTask : parentTasks.size()>0? parentTasks.toArray(new Task[0]) : new Task[]{null}) 
        {
            Task task = new Task(moduleId, Status.NEW);
            workflow.getTaskStatus().insert(task);
            task.createStorageLocation(
                    parentTask != null? parentTask.getStorageLocation() : null, 
                    new File(workflow.getWorkflowDir(), 
                            workflow.getInstance().getStorageLocation()).getPath());
            workflow.getTaskStatus().update(task,"id");
            tasks.add(task);
            workflow.getLogger().info(String.format("%s: createTaskRecords: Created new task record: %s", this.moduleId, task));
            
            if (parentTask != null) {
                TaskDispatch dispatch = new TaskDispatch(task.getId(), parentTask.getId());
                workflow.getTaskDispatch().insert(dispatch);
                workflow.getLogger().info(String.format("%s: createTaskRecords: Created new task dispatch record: %s", 
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
