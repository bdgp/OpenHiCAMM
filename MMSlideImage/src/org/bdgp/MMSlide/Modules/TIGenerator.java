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

public class TIGenerator implements Module {

    @Override
    public boolean requiresDataAcquisitionMode() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Status run(WorkflowRunner workflow, Task task, Map<String,Config> config, Logger logger) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getTitle() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getDescription() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JPanel configure(Configuration config) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void createTaskRecords(WorkflowRunner workflow, String moduleId) {
        // TODO Auto-generated method stub
        
    }

}
