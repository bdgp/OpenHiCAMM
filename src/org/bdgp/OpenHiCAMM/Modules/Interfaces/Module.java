package org.bdgp.OpenHiCAMM.Modules.Interfaces;

import java.util.List;
import java.util.Map;

import org.bdgp.OpenHiCAMM.Logger;
import org.bdgp.OpenHiCAMM.WorkflowRunner;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.Task.Status;
import org.bdgp.OpenHiCAMM.DB.WorkflowModule;
import org.micromanager.MMPlugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Interface for workflow modules.
 */
public interface Module extends MMPlugin, SciJavaPlugin {
    public static enum TaskType {SERIAL, PARALLEL};
    
    /**
     * Initialize this module instance.
     */
    public void initialize(WorkflowRunner workflow, WorkflowModule workflowModule);

    /**
     * Run the module configuration dialog and return the configuration.
     */
    public Configuration configure();
    
    /**
     * Create the task records before running the workflow.
     * @return The list of created tasks
     */
    public List<Task> createTaskRecords(List<Task> parentTasks, Map<String,Config> config, Logger logger);
    
    /**
     * Perform some initialization before starting the run.
     */
    public default void runInitialize() {}
    
    /**
     * Run the task.
     */
    public Status run(Task task, Map<String,Config> config, Logger logger);
    
    /**
     * Return the title of this module.
     * @return
     */
    public default String getTitle() {
        return this.getClass().getName();
    }
    
    /**
     * Return the module's description text.
     * @return
     */
    public default String getDescription() {
        return this.getClass().getName();
    }
    
    /**
     *  Return the module's task type (Serial or Parallel).
     */
    public TaskType getTaskType();

    /**
     * cleanup routine to be run after each task
     */
    public default void cleanup(Task task, Map<String,Config> config, Logger logger) {}

    /**
     * If resumimg a workflow, should this task's status be reset?
     * @param task
     */
    public default Status setTaskStatusOnResume(Task task) {
        if (task.getStatus() == Status.DEFER || 
            task.getStatus() == Status.IN_PROGRESS ||
            task.getStatus() == Status.ERROR) 
        {
            return Task.Status.NEW;
        }
        return null;
    }
    
    /***
     * How long is each task expected to run? Used for computing ETA.
     * @return duration in seconds
     */
    public default Long getExpectedDuration(Task task, Map<String,Config> config) {
    	return getMaxAllowedDuration(task, config);
    }

    /***
     * How long is a task allowed to run before it is re-started?
     * @return duration in seconds
     */
    public default Long getMaxAllowedDuration(Task task, Map<String,Config> config) {
    	return null;
    }
    
    /***
     * Should Fiji/Micro-Manager/OpenHiCAMM process be re-started if a task times out?
     * @return duration in seconds
     */
    public default boolean restartProcessIfTimeout() {
    	return false;
    }
    
    /***
     * How many task run timeouts before re-starting process?
     * @return duration in seconds
     */
    public default int numTimeoutsBeforeRestart() {
    	return 1;
    }
}
