package org.bdgp.OpenHiCAMM.Modules.Interfaces;

import java.util.List;
import java.util.Map;

import org.bdgp.OpenHiCAMM.Logger;
import org.bdgp.OpenHiCAMM.WorkflowRunner;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.Task.Status;

/**
 * Interface for workflow modules.
 */
public interface Module {
    public static enum TaskType {SERIAL, PARALLEL};
    
    /**
     * Initialize this module instance.
     */
    public void initialize(WorkflowRunner workflow, String moduleId);

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
}
