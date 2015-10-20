package org.bdgp.OpenHiCAMM.Modules;

import java.awt.Component;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bdgp.OpenHiCAMM.Logger;
import org.bdgp.OpenHiCAMM.ValidationError;
import org.bdgp.OpenHiCAMM.WorkflowRunner;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.ModuleConfig;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.Task.Status;
import org.bdgp.OpenHiCAMM.DB.TaskDispatch;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Configuration;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Module;

import mmcorej.CMMCore;

public class PosCalibrator implements Module {
    String moduleId;
    WorkflowRunner workflow;

    public PosCalibrator() { }

    @Override public void initialize(WorkflowRunner workflow, String moduleId) {
        this.workflow = workflow;
        this.moduleId = moduleId;
        
        workflow.getModuleConfig().insertOrUpdate(
                new ModuleConfig(this.moduleId, "canProduceROIs", "yes"), 
                "id", "key");
    }

    @Override public Configuration configure() {
        PosCalibratorDialog dialog = new PosCalibratorDialog(this.workflow);
        return new Configuration() {
            @Override public Config[] retrieve() {
                List<Config> configs = new ArrayList<Config>();
                configs.add(new Config(moduleId, 
                        "refSlideImagerModule", 
                        dialog.refSlideImagerModule.getSelectedItem().toString()));
                configs.add(new Config(moduleId, 
                        "compareSlideImagerModule", 
                        dialog.compareSlideImagerModule.getSelectedItem().toString()));
                configs.add(new Config(moduleId, 
                        "roiFinderModule", 
                        dialog.roiFinderModule.getSelectedItem().toString()));
                return configs.toArray(new Config[]{});
            }
            @Override public Component display(Config[] configs) {
                Map<String,Config> config = new HashMap<String,Config>();
                for (Config c : configs) {
                    config.put(c.getKey(), c);
                }
                if (config.containsKey("refSlideImagerModule")) {
                    dialog.refSlideImagerModule.setSelectedItem(config.get("refSlideImagerModule").getValue());
                }
                if (config.containsKey("compareSlideImagerModule")) {
                    dialog.compareSlideImagerModule.setSelectedItem(config.get("compareSlideImagerModule").getValue());
                }
                if (config.containsKey("roiFinderModule")) {
                    dialog.roiFinderModule.setSelectedItem(config.get("roiFinderModule").getValue());
                }
                return dialog;
            }
            @Override public ValidationError[] validate() {
                List<ValidationError> errors = new ArrayList<ValidationError>();
                if (dialog.refSlideImagerModule.getSelectedIndex() == 0) {
                    errors.add(new ValidationError(moduleId, "No reference slide imager module selected!"));
                }
                if (dialog.compareSlideImagerModule.getSelectedIndex() == 0) {
                    errors.add(new ValidationError(moduleId, "No comparison slide imager module selected!"));
                }
                if (dialog.refSlideImagerModule.getSelectedItem() != null && 
                    dialog.refSlideImagerModule.getSelectedItem().equals(dialog.compareSlideImagerModule.getSelectedItem())) 
                {
                    errors.add(new ValidationError(moduleId, "Reference and comparison imagers cannot be the same!"));
                }
                if (dialog.roiFinderModule.getSelectedIndex() == 0) {
                    errors.add(new ValidationError(moduleId, "No ROI finder module selected!"));
                }
                return errors.toArray(new ValidationError[]{});
            }};
    }

    @Override public List<Task> createTaskRecords(List<Task> parentTasks) {
        List<Task> tasks = new ArrayList<Task>();
        for (Task parentTask : parentTasks.size()>0? parentTasks.toArray(new Task[]{}) : new Task[]{null}) {
            Task task = new Task(this.moduleId, Status.NEW);
            workflow.getTaskStatus().insert(task);
            tasks.add(task);
            
            if (parentTask != null) {
                workflow.getTaskDispatch().insert(new TaskDispatch(task.getId(), parentTask.getId()));
            }
        }
        return tasks;
    }

    @Override public void runIntialize() { }

    @Override public Status run(Task task, Map<String, Config> config, Logger logger) {

        return Status.SUCCESS;
    }

    @Override public String getTitle() {
        return this.getClass().getSimpleName();
    }

    @Override public String getDescription() {
        return this.getClass().getSimpleName();
    }

    @Override public TaskType getTaskType() {
        return TaskType.SERIAL;
    }

    @Override public void cleanup(Task task) { }

    public static void moveStage(CMMCore core, double x, double y) {
    	core.setTimeoutMs(10000);
        String xyStage = core.getXYStageDevice();

        try {
            double[] x_stage = new double[] {0.0};
            double[] y_stage = new double[] {0.0};
            core.getXYPosition(xyStage, x_stage, y_stage);

            final long MAX_WAIT = 10000000000L; // 10 seconds
            long startTime = System.nanoTime();
            core.setXYPosition(xyStage, x, y);
            // wait for the stage to finish moving
            while (core.deviceBusy(xyStage)) {
                if (MAX_WAIT < System.nanoTime() - startTime) {
                    // If it's taking too long to move the stage, 
                    // try re-sending the stage movement command.
                    core.stop(xyStage);
                    Thread.sleep(500);
                    startTime = System.nanoTime();
                    core.setXYPosition(xyStage, x, y);
                }
                Thread.sleep(500);
            }
            
            double[] x_stage_new = new double[] {0.0};
            double[] y_stage_new = new double[] {0.0};
            core.getXYPosition(xyStage, x_stage_new, y_stage_new);
            final double EPSILON = 1000;
            if (!(Math.abs(x_stage_new[0]-x) < EPSILON && Math.abs(y_stage_new[0]-y) < EPSILON)) {
            	throw new RuntimeException(String.format("Stage moved to wrong coordinates: (%.2f,%.2f)",
            			x_stage_new[0], y_stage_new[0]));
            }
		} 
        catch (Throwable e) { 
        	StringWriter sw = new StringWriter();
        	e.printStackTrace(new PrintWriter(sw));
        	throw new RuntimeException(e);
        }
    }
}
