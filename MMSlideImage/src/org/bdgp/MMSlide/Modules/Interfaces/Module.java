package org.bdgp.MMSlide.Modules.Interfaces;

import java.util.List;
import java.util.Map;

import org.bdgp.MMSlide.Logger;
import org.bdgp.MMSlide.WorkflowRunner;
import org.bdgp.MMSlide.DB.Config;
import org.bdgp.MMSlide.DB.Task;
import org.bdgp.MMSlide.DB.Task.Status;

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
    public List<Task> createTaskRecords(List<Task> parentTasks);
    
    /**
     * Run the task.
     */
    public Status run(Task task, Map<String,Config> config, Logger logger);
    
    /**
     * Return the title of this module.
     * @return
     */
    public String getTitle();
    
    /**
     * Return the module's description text.
     * @return
     */
    public String getDescription();
    
    /**
     *  Return the module's task type (Serial or Parallel).
     */
    public TaskType getTaskType();

    /**
     * cleanup routine to be run after each task
     */
    public void cleanup(Task task);
}
