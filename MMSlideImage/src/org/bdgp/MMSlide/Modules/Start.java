package org.bdgp.MMSlide.Modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import org.bdgp.MMSlide.Logger;
import org.bdgp.MMSlide.Util;
import org.bdgp.MMSlide.ValidationError;
import org.bdgp.MMSlide.WorkflowRunner;
import org.bdgp.MMSlide.DB.Config;
import org.bdgp.MMSlide.DB.Task;
import org.bdgp.MMSlide.DB.Task.Status;
import org.bdgp.MMSlide.Modules.Interfaces.Configuration;
import org.bdgp.MMSlide.Modules.Interfaces.Module;

import static org.bdgp.MMSlide.Util.map;

public class Start implements Module {
    WorkflowRunner workflowRunner;
    String moduleId;

    @Override
    public void initialize(WorkflowRunner workflowRunner, String moduleId) { 
        this.workflowRunner = workflowRunner;
        this.moduleId = moduleId;
    }

    @Override
    public Status run(Task task, Map<String,Config> config, Logger logger) {
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
            public List<Config> retrieve() {
                return new ArrayList<Config>();
            }
            @Override
            public JPanel display(List<Config> configs) {
                return new JPanel();
            }
            @Override
            public ValidationError[] validate() {
                return null;
            }};
    }

    @Override
    public void createTaskRecords() {
        Task task = new Task(moduleId, workflowRunner.getInstance().getStorageLocation(), Status.NEW);
        workflowRunner.getTaskStatus().insert(task);
        task.update(workflowRunner.getTaskStatus());
    }

    @Override
    public Map<String, Integer> getResources() {
        return map("cpu",1);
    }
}
