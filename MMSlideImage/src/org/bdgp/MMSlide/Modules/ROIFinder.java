package org.bdgp.MMSlide.Modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import org.bdgp.MMSlide.Connection;
import org.bdgp.MMSlide.Logger;
import org.bdgp.MMSlide.Util;
import org.bdgp.MMSlide.WorkflowRunner;
import org.bdgp.MMSlide.DB.Config;
import org.bdgp.MMSlide.DB.Task;
import org.bdgp.MMSlide.DB.Task.Status;
import org.bdgp.MMSlide.DB.TaskDispatch;
import org.bdgp.MMSlide.DB.WorkflowModule;
import org.bdgp.MMSlide.Modules.Interfaces.Configuration;
import org.bdgp.MMSlide.Modules.Interfaces.Module;

import static org.bdgp.MMSlide.Util.where;
import static org.bdgp.MMSlide.Util.map;

/**
 * Return x/y/len/width of bounding box surrounding the ROI
 */
public class ROIFinder implements Module {
    @Override
    public void initialize(WorkflowRunner workflow) {
        
    }

    @Override
    public Status run(WorkflowRunner workflow, Task task, Map<String,Config> config, Logger logger) {
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
    public Configuration configure(Connection connection) {
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
            public String[] validate() {
                return null;
            }
        };
    }

    @Override
    public void createTaskRecords(WorkflowRunner workflow, String moduleId) {
        WorkflowModule module = workflow.getWorkflow().selectOneOrDie(where("id",moduleId));
        if (module.getParentId() != null) {
            List<Task> parentTasks = workflow.getTaskStatus().select(where("moduleId",module.getParentId()));
            for (Task parentTask : parentTasks) {
                Task task = new Task(moduleId, parentTask.getStorageLocation(), Status.NEW);
                workflow.getTaskStatus().insert(task);
                task.update(workflow.getTaskStatus());
                
                TaskDispatch dispatch = new TaskDispatch(task.getId(), parentTask.getId());
                workflow.getTaskDispatch().insert(dispatch);
            }
        }
        else {
            Task task = new Task(moduleId, workflow.getInstance().getStorageLocation(), Status.NEW);
            workflow.getTaskStatus().insert(task);
            task.update(workflow.getTaskStatus());
        }
    }

    @Override
    public Map<String, Integer> getResources() {
        return map("cpu",1);
    }

    @Override
    public String[] validate(WorkflowRunner workflow) {
        return null;
    }

}
