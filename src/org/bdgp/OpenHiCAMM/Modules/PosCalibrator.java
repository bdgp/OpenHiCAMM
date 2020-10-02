package org.bdgp.OpenHiCAMM.Modules;

import java.awt.Component;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bdgp.OpenHiCAMM.Dao;
import org.bdgp.OpenHiCAMM.Logger;
import org.bdgp.OpenHiCAMM.ValidationError;
import org.bdgp.OpenHiCAMM.WorkflowRunner;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.Image;
import org.bdgp.OpenHiCAMM.DB.ModuleConfig;
import org.bdgp.OpenHiCAMM.DB.SlidePosList;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.Task.Status;
import org.bdgp.OpenHiCAMM.DB.TaskConfig;
import org.bdgp.OpenHiCAMM.DB.TaskDispatch;
import org.bdgp.OpenHiCAMM.DB.WorkflowModule;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Configuration;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Module;
import org.micromanager.MMPlugin;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;
import org.micromanager.Studio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

import bdgp.org.hough.GHT_Rawmatch;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ImageConverter;

import static org.bdgp.OpenHiCAMM.Util.where;

@Plugin(type=MMPlugin.class)
public class PosCalibrator implements Module, SciJavaPlugin, MMPlugin {
    WorkflowModule workflowModule;
    WorkflowRunner workflow;

    @Override public void initialize(WorkflowRunner workflow, WorkflowModule workflowModule) {
        this.workflow = workflow;
        this.workflowModule = workflowModule;
        
        workflow.getModuleConfig().insertOrUpdate(
                new ModuleConfig(this.workflowModule.getId(), "canProduceROIs", "yes"), 
                "id", "key");
    }

    @Override public Configuration configure() {
        PosCalibratorDialog dialog = new PosCalibratorDialog(this.workflow);
        return new Configuration() {
            @Override public Config[] retrieve() {
                List<Config> configs = new ArrayList<Config>();
                configs.add(new Config(workflowModule.getId(), 
                        "refSlideImagerModule", 
                        dialog.refSlideImagerModule.getSelectedItem().toString()));
                configs.add(new Config(workflowModule.getId(), 
                        "compareSlideImagerModule", 
                        dialog.compareSlideImagerModule.getSelectedItem().toString()));
                configs.add(new Config(workflowModule.getId(), 
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
                    errors.add(new ValidationError(workflowModule.getName(), "No reference slide imager module selected!"));
                }
                if (dialog.compareSlideImagerModule.getSelectedIndex() == 0) {
                    errors.add(new ValidationError(workflowModule.getName(), "No comparison slide imager module selected!"));
                }
                if (dialog.refSlideImagerModule.getSelectedItem() != null && 
                    dialog.refSlideImagerModule.getSelectedItem().equals(dialog.compareSlideImagerModule.getSelectedItem())) 
                {
                    errors.add(new ValidationError(workflowModule.getName(), "Reference and comparison imagers cannot be the same!"));
                }
                if (dialog.roiFinderModule.getSelectedIndex() == 0) {
                    errors.add(new ValidationError(workflowModule.getName(), "No ROI finder module selected!"));
                }
                return errors.toArray(new ValidationError[]{});
            }};
    }

    @Override public List<Task> createTaskRecords(List<Task> parentTasks, Map<String,Config> config, Logger logger) {
        Dao<SlidePosList> slidePosListDao = workflow.getWorkflowDb().table(SlidePosList.class);

        List<Task> tasks = new ArrayList<Task>();
        for (Task parentTask : parentTasks.size()>0? parentTasks.toArray(new Task[]{}) : new Task[]{null}) {
            Task task = new Task(this.workflowModule.getId(), Status.NEW);
            workflow.getTaskStatus().insert(task);
            tasks.add(task);
            
            if (parentTask != null) {
                workflow.getTaskDispatch().insert(new TaskDispatch(task.getId(), parentTask.getId()));
                
                // re-export the slideId task config
                TaskConfig slideIdConf = this.workflow.getTaskConfig().selectOne(
                        where("id", parentTask.getId()).
                        and("key", "slideId"));
                if (slideIdConf != null) {
                    this.workflow.getTaskConfig().insert(new TaskConfig(
                            task.getId(), "slideId", slideIdConf.getValue()));
                }

                // get the position lists
                Config roiFinderModuleConf = this.workflow.getModuleConfig().selectOne(
                        where("id", this.workflowModule.getId()).
                        and("key","roiFinderModule"));
                if (roiFinderModuleConf == null) throw new RuntimeException("Config roiFinderModule not found!");
                WorkflowModule roiFinderModule = this.workflow.getWorkflow().selectOneOrDie(
                        where("name", roiFinderModuleConf.getValue()));
                List<SlidePosList> slidePosLists = slidePosListDao.select(
                        where("moduleId", roiFinderModule.getId()).
                        and("slideId", Integer.parseInt(slideIdConf.getValue())));
                
                // remove old slide pos lists
                slidePosListDao.delete(
                        where("moduleId", this.workflowModule.getId()).
                        and("slideId", Integer.parseInt(slideIdConf.getValue())));

                // save the original X and Y positions as property values
                for (SlidePosList spl : slidePosLists) {
                    PositionList posList = spl.getPositionList();
                    MultiStagePosition[] msps = posList.getPositions();
                    for (MultiStagePosition msp : msps) {
                        for (int i=0; i<msp.size(); ++i) {
                            StagePosition sp = msp.get(i);
                            if (sp.getNumberOfStageAxes() == 2 && sp.getStageDeviceLabel().compareTo(msp.getDefaultXYStage()) == 0) {
                                msp.setProperty("origX", Double.toString(sp.get2DPositionX()));
                                msp.setProperty("origY", Double.toString(sp.get2DPositionY()));
                                break;
                            }
                        }
                    }
                    posList.setPositions(msps);

                    // store the translated position list into the database
                    SlidePosList slidePosList = new SlidePosList(this.workflowModule.getId(), spl.getSlideId(), null, posList);
                    slidePosListDao.insert(slidePosList);
                }
            }
        }

        return tasks;
    }

    @Override public void runInitialize() { }

    @Override public Status run(Task task, Map<String, Config> config, Logger logger) {
        Dao<Image> imageDao = workflow.getWorkflowDb().table(Image.class);
        Dao<SlidePosList> slidePosListDao = workflow.getWorkflowDb().table(SlidePosList.class);

        // get the slide ID conf
        Config slideIdConf = config.get("slideId");
        if (slideIdConf == null) throw new RuntimeException("slideId conf not defined!");

        // get The reference image from the reference module
        Config refSlideImagerModuleConf = config.get("refSlideImagerModule");
        if (refSlideImagerModuleConf == null) throw new RuntimeException("Config refSlideImagerModule not found!");
        Map<ImagePlus,Point2D.Double> refImages = new LinkedHashMap<>();
        WorkflowModule refSlideImagerModule = this.workflow.getWorkflow().selectOneOrDie(
                where("name",refSlideImagerModuleConf.getValue()));
        List<Task> refTasks = workflow.getTaskStatus().select(
                where("moduleId", refSlideImagerModule.getId()));
        for (Task t : refTasks) {
            TaskConfig refSlideIdConf = workflow.getTaskConfig().selectOne(
                    where("id", t.getId()).
                    and("key", "slideId"));
            if ((slideIdConf == null && refSlideIdConf == null) || Objects.equals(slideIdConf.getValue(), refSlideIdConf.getValue())) {
                TaskConfig imageId = workflow.getTaskConfig().selectOne(
                        where("id", t.getId()).
                        and("key", "imageId"));
                if (imageId != null) {
                    Image image = imageDao.selectOneOrDie(where("id", imageId.getValue()));
                    ImagePlus imp = image.getImagePlus(workflow);
                    new ImageConverter(imp).convertToGray8();
                    org.micromanager.data.Image mmimage = image.getImage(workflow);
                    Double xPos = mmimage.getMetadata().getXPositionUm();
                    Double yPos = mmimage.getMetadata().getYPositionUm();
                    refImages.put(imp, new Point2D.Double(xPos, yPos));
                }
            }
        }
        if (refImages.size() == 0) {
          throw new RuntimeException(String.format("No images found for module %s", refSlideImagerModuleConf.getValue()));  
        }
        if (refImages.size() > 1) {
            throw new RuntimeException(String.format(
                    "More than one image found for module %s: %d", 
                    refSlideImagerModuleConf.getValue(), refImages.size()));
        }
        ImagePlus refImage = new ArrayList<>(refImages.keySet()).get(0);
        Point2D.Double refCoords = refImages.get(refImage);
        
        // get the comparison image(s) from the comparison module
        Config compareSlideImagerModuleConf = config.get("compareSlideImagerModule");
        if (compareSlideImagerModuleConf == null) throw new RuntimeException("Config compareSlideImagerModule not found!");
        WorkflowModule compareSlideImagerModule = this.workflow.getWorkflow().selectOneOrDie(
                where("name", compareSlideImagerModuleConf.getValue()));
        Map<ImagePlus,Point2D.Double> compareImages = new LinkedHashMap<>();
        List<Task> compareTasks = workflow.getTaskStatus().select(
                where("moduleId", compareSlideImagerModule.getId()));
        for (Task t : compareTasks) {
            TaskConfig compareSlideIdConf = workflow.getTaskConfig().selectOne(
                    where("id", t.getId()).
                    and("key", "slideId"));
            if ((slideIdConf == null && compareSlideIdConf == null) || Objects.equals(slideIdConf.getValue(), compareSlideIdConf.getValue())) {
                TaskConfig imageId = workflow.getTaskConfig().selectOne(
                        where("id", t.getId()).
                        and("key", "imageId"));
                if (imageId != null) {
                    Image image = imageDao.selectOneOrDie(where("id", imageId.getValue()));
                    ImagePlus imp = image.getImagePlus(workflow);
                    new ImageConverter(imp).convertToGray8();
                    org.micromanager.data.Image mmimage = image.getImage(workflow);
                    Double xPos = mmimage.getMetadata().getXPositionUm();
                    Double yPos = mmimage.getMetadata().getYPositionUm();
                    compareImages.put(imp, new Point2D.Double(xPos, yPos));
                }
            }
        }

        // pass the reference image and comparison image(s) into the GHT_Rawmatch.match() function 
        // to get the translation matrix
        GHT_Rawmatch matcher = new GHT_Rawmatch();
        matcher.intIP = false; // Show all intermediate ImagePlus images
        matcher.doLog = false; // Log results

        Roi roi = matcher.match(refImage, new ArrayList<>(compareImages.keySet()));
        logger.info(String.format("Got ROI from matcher: %s", roi));
        
        if (matcher.badRef) {
            logger.warning("GHT_Rawmatch returned image that doesn't match QC and is unlikely to yield useful results!");
        }
        if (matcher.badMatch) {
            logger.warning("GHT_Rawmatch returned image that had to use a very low threshold!");
        }
        // get the matched reference image's stage coordinates
        ImagePlus matchedReference = matcher.matched_reference;
        if (matchedReference == null) {
            logger.warning("No matched reference was found, skipping position calibration step.");
            return Status.SUCCESS;
        }

        Point2D.Double matchedRefCoords = compareImages.get(matchedReference);
        if (matchedRefCoords == null) throw new RuntimeException(
                String.format("Could not find matched reference image %s in comparison list!", 
                        matchedReference.getTitle()));
        logger.info(String.format("Using input image %s for position calibration", matchedReference.getTitle()));
        
        // convert between image coordinates and stage coordinates
        ModuleConfig pixelSizeConf = workflow.getModuleConfig().selectOne(
                where("id", refSlideImagerModule.getId()).
                and("key", "pixelSize"));
        if (pixelSizeConf == null) throw new RuntimeException("pixelSize conf not found for ref imager!");
        Double pixelSize = Double.parseDouble(pixelSizeConf.getValue());

        ModuleConfig hiResPixelSizeConf = workflow.getModuleConfig().selectOne(
                where("id", compareSlideImagerModule.getId()).
                and("key", "pixelSize"));
        if (hiResPixelSizeConf == null) throw new RuntimeException("hires pixelSize conf not found for compare imager!");
        Double hiResPixelSize = Double.parseDouble(hiResPixelSizeConf.getValue());

        ModuleConfig invertXAxisConf = workflow.getModuleConfig().selectOne(
                where("id", refSlideImagerModule.getId()).
                and("key", "invertXAxis"));
        if (invertXAxisConf == null) throw new RuntimeException("invertXAxis conf not found for ref imager!");
        ModuleConfig invertXAxisConf2 = workflow.getModuleConfig().selectOne(
                where("id", compareSlideImagerModule.getId()).
                and("key", "invertXAxis"));
        if (invertXAxisConf2 == null) throw new RuntimeException("invertXAxis conf not found for compare imager!");
        ModuleConfig invertYAxisConf = workflow.getModuleConfig().selectOne(
                where("id", refSlideImagerModule.getId()).
                and("key", "invertYAxis"));
        if (invertYAxisConf == null) throw new RuntimeException("invertYAxis conf not found for ref imager!");
        ModuleConfig invertYAxisConf2 = workflow.getModuleConfig().selectOne(
                where("id", compareSlideImagerModule.getId()).
                and("key", "invertYAxis"));
        if (invertYAxisConf2 == null) throw new RuntimeException("invertYAxis conf not found for compare imager!");
        
        if (!Objects.equals(invertXAxisConf.getValue(), invertXAxisConf2.getValue()))
            throw new RuntimeException("inconsistent values for invertXAxis between ref and compare imagers!");
        if (!Objects.equals(invertYAxisConf.getValue(), invertYAxisConf2.getValue()))
            throw new RuntimeException("inconsistent values for invertYAxis between ref and compare imagers!");
        Double invertXAxis = "yes".equals(invertXAxisConf.getValue())? -1.0 : 1.0;
        Double invertYAxis = "yes".equals(invertYAxisConf.getValue())? -1.0 : 1.0;
        
        // translate = actual - expected
        // where actual is: the stage coordinates of the comparison image from the position list
        // and expected is: the center of the fitted ROI - the center of the reference image,
        //     scaled (multiplied) by the pixel size and by the invert axis value (the stage coordinates scale factor)
        //     translated (added) to the reference stage coordinates,
        Point2D.Double translateStage = roi == null? 
            new Point2D.Double(0.0, 0.0) :
            new Point2D.Double(
                matchedRefCoords.getX()
                    - ((((roi.getXBase() + roi.getFloatWidth() / 2.0) 
                          - (double)refImage.getWidth() / 2.0) 
                              * pixelSize * invertXAxis) 
                        + refCoords.getX()),
                matchedRefCoords.getY()
                    - ((((roi.getYBase() + roi.getFloatHeight() / 2.0) 
                          - (double)refImage.getHeight() / 2.0) 
                              * pixelSize * invertYAxis) 
                        + refCoords.getY()));
        
        if ((matcher.badRef || matcher.badMatch) && 
            (matchedReference == null ||
             Math.abs(translateStage.getX()) >= matchedReference.getWidth() * hiResPixelSize / 2.0 || 
             Math.abs(translateStage.getY()) >= matchedReference.getHeight() * hiResPixelSize / 2.0)) 
        {
            logger.info(String.format("badRef/badMatch is set and translation is large, so we will not translate"));
            logger.info(String.format("badRef=%s, badMatch=%s, translateStage=%s, matchedReference=%s",
                    matcher.badRef, matcher.badMatch, translateStage, matchedReference));
            translateStage = new Point2D.Double(0.0, 0.0);
        }

        logger.info(String.format("Computed stage translation: %s", translateStage));
        // store translated stage coordinates as task config
        this.workflow.getTaskConfig().insertOrUpdate(
                new TaskConfig(task.getId(),
                        "translateStageX", Double.toString(translateStage.getX())), "id", "key");
        this.workflow.getTaskConfig().insertOrUpdate(
                new TaskConfig(task.getId(),
                        "translateStageY", Double.toString(translateStage.getY())), "id", "key");

        // get the position lists
        List<SlidePosList> slidePosLists = slidePosListDao.select(
                where("moduleId", this.workflowModule.getId()).
                and("slideId", Integer.parseInt(slideIdConf.getValue())));
        
        // apply the translation matrix to the position list to derive a new position list
        for (SlidePosList spl : slidePosLists) {
            PositionList posList = spl.getPositionList();
            MultiStagePosition[] msps = posList.getPositions();
            for (MultiStagePosition msp : msps) {
                if (!msp.hasProperty("origX")) throw new RuntimeException(String.format("MSP property origX is missing!: %s", msp));
                Double origX = Double.parseDouble(msp.getProperty("origX"));
                if (!msp.hasProperty("origY")) throw new RuntimeException(String.format("MSP property origY is missing!: %s", msp));
                Double origY = Double.parseDouble(msp.getProperty("origY"));

                for (int i=0; i<msp.size(); ++i) {
                    StagePosition sp = msp.get(i);
                    if (sp.getNumberOfStageAxes() == 2 && sp.getStageDeviceLabel().compareTo(msp.getDefaultXYStage()) == 0) {
                    	sp.set2DPosition(sp.getStageDeviceLabel(), origX+translateStage.getX(), origY+translateStage.getY());
                        break;
                    }
                }
            }
            posList.setPositions(msps);

            // update the translated position list into the database
            slidePosListDao.update(spl, "id");
        }

        return Status.SUCCESS;
    }
    
    @Override public TaskType getTaskType() {
        return TaskType.SERIAL;
    }

    public Status setTaskStatusOnResume(Task task) {
        if (task.getStatus() != Status.SUCCESS) {
            return Status.NEW;
        }
        // try to find the parent slide loader task
        // if slide loader parent task needs to be re-run, then re-run this as well.
        List<TaskDispatch> tds = this.workflow.getTaskDispatch().select(where("taskId", task.getId()));
        while (!tds.isEmpty()) {
            List<TaskDispatch> parentTds = new ArrayList<>();
            for (TaskDispatch td : tds) {
                Task parentTask = this.workflow.getTaskStatus().selectOneOrDie(where("id", td.getParentTaskId()));
                if (this.workflow.getModuleConfig().selectOne(
                        where("id", parentTask.getModuleId()).
                        and("key", "canLoadSlides").
                        and("value", "yes")) != null) 
                {
                    if (parentTask.getStatus() == Status.NEW) {
                        return Status.NEW;
                    }
                    else {
                        return null;
                    }
                }
                parentTds.addAll(this.workflow.getTaskDispatch().select(where("taskId", parentTask.getId())));
            }
            tds = parentTds;
        }
        return null;
    }

	@Override
	public void setContext(Studio studio) { }

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public String getHelpText() {
		return "";
	}

	@Override
	public String getVersion() {
		return "1.0";
	}

	@Override
	public String getCopyright() {
		return "";
	}
}
