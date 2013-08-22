package org.bdgp.MMSlide.Modules.Interfaces;

import java.util.Map;

import org.bdgp.MMSlide.Connection;
import org.bdgp.MMSlide.Logger;
import org.bdgp.MMSlide.WorkflowRunner;
import org.bdgp.MMSlide.DB.Config;
import org.bdgp.MMSlide.DB.Task;
import org.bdgp.MMSlide.DB.Task.Status;

/**
 * Interface for workflow modules.
 */
public interface Module {
    /**
     * Run the module configuration dialog and return the configuration.
     * @return
     */
    public Configuration configure(Connection connection);
    
    /**
     * Create the task records before running the workflow.
     */
    public void createTaskRecords(WorkflowRunner workflow, String moduleId);
    
    /**
     * Run the task.
     */
    public Status run(WorkflowRunner workflow, Task task, Map<String,Config> config, Logger logger);
    
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
     * Return a map of the resources this module requires.
     */
    public Map<String,Integer> getResources();
}
