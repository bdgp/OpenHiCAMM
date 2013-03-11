package org.bdgp.MMSlide.Modules;

import java.util.Map;

import javax.swing.JPanel;

import org.bdgp.MMSlide.Configuration;
import org.bdgp.MMSlide.Logger;
import org.bdgp.MMSlide.WorkflowRunner;
import org.bdgp.MMSlide.Dao.Config;
import org.bdgp.MMSlide.Dao.Task;
import org.bdgp.MMSlide.Dao.Task.Status;
import org.bdgp.MMSlide.Modules.Interfaces.Module;

public class Start implements Module {
    @Override
    public JPanel configure(Configuration config) {
        return null;
    }

    @Override
    public Status run(WorkflowRunner workflow, Task task,
            Map<String, Config> config, Logger logger) {
        return null;
    }

    @Override
    public String getTitle() {
        return null;
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public void createTaskRecords(WorkflowRunner workflow, String moduleId) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public Map<String, Integer> getResources() {
        // TODO Auto-generated method stub
        return null;
    }
}
