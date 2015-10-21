package org.bdgp.OpenHiCAMM.Modules;

import java.awt.Component;
import java.awt.geom.Point2D;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bdgp.OpenHiCAMM.Dao;
import org.bdgp.OpenHiCAMM.Logger;
import org.bdgp.OpenHiCAMM.ValidationError;
import org.bdgp.OpenHiCAMM.WorkflowRunner;
import org.bdgp.OpenHiCAMM.DB.Acquisition;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.Image;
import org.bdgp.OpenHiCAMM.DB.ModuleConfig;
import org.bdgp.OpenHiCAMM.DB.SlidePosList;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.Task.Status;
import org.bdgp.OpenHiCAMM.DB.TaskConfig;
import org.bdgp.OpenHiCAMM.DB.TaskDispatch;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Configuration;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Module;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.api.StagePosition;

import ij.ImagePlus;
import mmcorej.CMMCore;

import static org.bdgp.OpenHiCAMM.Util.where;

public abstract class PosCalibrator implements Module {
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
        Dao<Image> imageDao = workflow.getInstanceDb().table(Image.class);
        Dao<Acquisition> acqDao = workflow.getInstanceDb().table(Acquisition.class);
        Dao<SlidePosList> slidePosListDao = workflow.getInstanceDb().table(SlidePosList.class);

        // get The reference image from the reference module
        Config refSlideImagerModuleConf = config.get("refSlideImagerModule");
        if (refSlideImagerModuleConf == null) throw new RuntimeException("Config refSlideImagerModule not found!");
        List<ImagePlus> refImages = new ArrayList<>();
        List<Task> refTasks = workflow.getTaskStatus().select(
                where("moduleId", refSlideImagerModuleConf.getValue()));
        for (Task t : refTasks) {
            TaskConfig imageId = workflow.getTaskConfig().selectOne(
                    where("id", t.getId()).
                    and("key", "imageId"));
            if (imageId != null) {
                Image image = imageDao.selectOneOrDie(where("id", imageId.getValue()));
                ImagePlus imp = image.getImagePlus(acqDao);
                refImages.add(imp);
            }
        }
        if (refImages.size() == 0) throw new RuntimeException(String.format(
                "No images found for module %s", refSlideImagerModuleConf.getValue()));
        if (refImages.size() > 1) throw new RuntimeException(String.format(
                "More than one image found for module %s: %d", 
                refSlideImagerModuleConf.getValue(), refImages.size()));
        ImagePlus refImage = refImages.get(0);
        
        // get the comparison image(s) from the comparison module
        Config compareSlideImagerModuleConf = config.get("compareSlideImagerModule");
        if (compareSlideImagerModuleConf == null) throw new RuntimeException("Config compareSlideImagerModule not found!");
        List<ImagePlus> compareImages = new ArrayList<>();
        List<Task> compareTasks = workflow.getTaskStatus().select(
                where("moduleId", compareSlideImagerModuleConf.getValue()));
        for (Task t : compareTasks) {
            TaskConfig imageId = workflow.getTaskConfig().selectOne(
                    where("id", t.getId()).
                    and("key", "imageId"));
            if (imageId != null) {
                Image image = imageDao.selectOneOrDie(where("id", imageId.getValue()));
                ImagePlus imp = image.getImagePlus(acqDao);
                compareImages.add(imp);
            }
        }

        // pass the reference image and comparison image(s) into the process() function to get the translation matrix
        Point2D.Double translateImage = process(refImage, compareImages);
        
        // convert between image coordinates and stage coordinates
        ModuleConfig pixelSizeConf = workflow.getModuleConfig().selectOne(
                where("id", refSlideImagerModuleConf.getValue()).
                and("key", "pixelSize"));
        if (pixelSizeConf == null) throw new RuntimeException("pixelSize conf not found for ref imager!");
        Double pixelSize = new Double(pixelSizeConf.getValue());

        ModuleConfig invertXAxisConf = workflow.getModuleConfig().selectOne(
                where("id", refSlideImagerModuleConf.getValue()).
                and("key", "invertXAxis"));
        if (invertXAxisConf == null) throw new RuntimeException("invertXAxis conf not found for ref imager!");
        ModuleConfig invertXAxisConf2 = workflow.getModuleConfig().selectOne(
                where("id", compareSlideImagerModuleConf.getValue()).
                and("key", "invertXAxis"));
        if (invertXAxisConf2 == null) throw new RuntimeException("invertXAxis conf not found for compare imager!");
        ModuleConfig invertYAxisConf = workflow.getModuleConfig().selectOne(
                where("id", refSlideImagerModuleConf.getValue()).
                and("key", "invertYAxis"));
        if (invertYAxisConf == null) throw new RuntimeException("invertYAxis conf not found for ref imager!");
        ModuleConfig invertYAxisConf2 = workflow.getModuleConfig().selectOne(
                where("id", compareSlideImagerModuleConf.getValue()).
                and("key", "invertYAxis"));
        if (invertYAxisConf2 == null) throw new RuntimeException("invertYAxis conf not found for compare imager!");
        
        if (!Objects.equals(invertXAxisConf.getValue(), invertXAxisConf2.getValue()))
            throw new RuntimeException("inconsistent values for invertXAxis between ref and compare imagers!");
        if (!Objects.equals(invertYAxisConf.getValue(), invertYAxisConf2.getValue()))
            throw new RuntimeException("inconsistent values for invertYAxis between ref and compare imagers!");
        Double invertXAxis = "yes".equals(invertXAxisConf.getValue())? -1.0 : 1.0;
        Double invertYAxis = "yes".equals(invertYAxisConf.getValue())? -1.0 : 1.0;
        
        Point2D.Double translateStage = new Point2D.Double(
                translateImage.getX() * pixelSize * invertXAxis, 
                translateImage.getY() * pixelSize * invertYAxis);

        // get the position lists
        Config roiFinderModuleConf = config.get("roiFinderModule");
        if (roiFinderModuleConf == null) throw new RuntimeException("Config roiFinderModule not found!");
        List<SlidePosList> slidePosLists = slidePosListDao.select(
                where("moduleId", roiFinderModuleConf.getValue()));

        // apply the translation matrix to the position list to derive a new position list
        for (SlidePosList spl : slidePosLists) {
            PositionList posList = spl.getPositionList();
            MultiStagePosition[] msps = posList.getPositions();
            for (MultiStagePosition msp : msps) {
                for (int i=0; i<msp.size(); ++i) {
                    StagePosition sp = msp.get(i);
                    if (sp.numAxes == 2 && sp.stageName.compareTo(msp.getDefaultXYStage()) == 0) {
                        sp.x += translateStage.getX();
                        sp.y += translateStage.getY();
                        break;
                    }
                }
            }
            posList.setPositions(msps);

            // store the new position list into the database
            SlidePosList slidePosList = new SlidePosList(this.moduleId, spl.getSlideId(), task.getId(), posList);
            slidePosListDao.insert(slidePosList);
        }

        return Status.SUCCESS;
    }
    
    /**
     * Given a reference image and a set of comparison images, produce a stage-position translation (x,y) pair.
     * @param ref The reference image
     * @param compare The list of comparison images
     * @return a Point2D.Double containing the translation (x,y) pair.
     */
    abstract Point2D.Double process(ImagePlus ref, List<ImagePlus> compare);

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
