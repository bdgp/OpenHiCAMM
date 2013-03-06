package org.bdgp.MMSlide.Modules.Interfaces;

import java.util.Map;

import javax.swing.JPanel;

import org.bdgp.MMSlide.Configuration;
import org.bdgp.MMSlide.Logger;
import org.bdgp.MMSlide.WorkflowRunner;
import org.bdgp.MMSlide.Dao.Config;
import org.bdgp.MMSlide.Dao.Task;
import org.bdgp.MMSlide.Dao.Task.Status;

/**
 * Interface for workflow modules.
 */
public interface Module {
    /**
     * @return True or false depending on whether this module must be
     * run in data acquisition mode.
     */
    public boolean requiresDataAcquisitionMode();
    
    /**
     * Run the module configuration dialog and return the configuration.
     * @return
     */
    public JPanel configure(Configuration config);
    
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
}
