package org.bdgp.MMSlide.Modules;

import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.bdgp.MMSlide.DB.TaskDispatch;
import org.bdgp.MMSlide.DB.WorkflowModule;
import org.bdgp.MMSlide.Modules.Interfaces.Configuration;
import org.bdgp.MMSlide.Modules.Interfaces.Module;
import org.micromanager.PositionListDlg;
import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.MMSerializationException;

import static org.bdgp.MMSlide.Util.where;
import static org.bdgp.MMSlide.Util.map;

public class SlideImager implements Module {
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
            PositionListDlg posListDlg;
            PositionList posList;
            @Override
            public Config[] retrieve() {
            	List<Config> configs = new ArrayList<Config>();
            	try {
            		if (posList != null) {
                        configs.add(new Config(moduleId, "positionList", posList.serialize()));
            		}
				} catch (MMSerializationException e) {throw new RuntimeException(e);}
                return configs.toArray(new Config[0]);
            }
            @Override
            public Component display(Config[] configs) {
                Map<String,Config> conf = new HashMap<String,Config>();
                for (Config config : configs) {
                    conf.put(config.getKey(), config);
                }
                // Positions (what to image: entire slide, previously defined positions)
                ScriptInterface script = workflowRunner.getMMSlide().getApp();
                if (script != null) {
                    posListDlg = script.getXYPosListDlg();
                    posList = new PositionList();
                    if (conf.containsKey("positionList")) {
                    	try {
							posList.restore(conf.get("positionList").getValue());
						} catch (MMSerializationException e) {throw new RuntimeException(e);}
                    }
                    posListDlg.setPositionList(posList);
                    return posListDlg;
                }
                return new JPanel();
            }
            @Override
            public ValidationError[] validate() {
                return null;
            }
        };
    }

    @Override
    public void createTaskRecords() {
        WorkflowModule module = workflowRunner.getWorkflow().selectOneOrDie(where("id",moduleId));
        if (module.getParentId() != null) {
            List<Task> parentTasks = workflowRunner.getTaskStatus().select(where("moduleId",module.getParentId()));
            for (Task parentTask : parentTasks) {
                Task task = new Task(moduleId, Status.NEW);
                workflowRunner.getTaskStatus().insert(task);
                task.createStorageLocation(parentTask.getStorageLocation(), workflowRunner.getWorkflowDirectory().getPath());
                workflowRunner.getTaskStatus().update(task,"id");
                
                TaskDispatch dispatch = new TaskDispatch(task.getId(), parentTask.getId());
                workflowRunner.getTaskDispatch().insert(dispatch);
            }
        }
        else {
            Task task = new Task(moduleId, Status.NEW);
            workflowRunner.getTaskStatus().insert(task);
            task.createStorageLocation(workflowRunner.getInstance().getStorageLocation(), workflowRunner.getWorkflowDirectory().getPath());
            workflowRunner.getTaskStatus().update(task,"id");
        }
    }

    @Override
    public Map<String, Integer> getResources() {
        return map("cpu",1);
    }
}
