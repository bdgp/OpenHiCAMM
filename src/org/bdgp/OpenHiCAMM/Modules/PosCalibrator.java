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
import org.json.JSONException;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.api.StagePosition;
import org.micromanager.utils.MDUtils;

import bdgp.org.hough.GHT_Rawmatch;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ImageConverter;
import mmcorej.TaggedImage;

import static org.bdgp.OpenHiCAMM.Util.where;

public class PosCalibrator implements Module {
    String moduleId;
    WorkflowRunner workflow;

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

    @Override public List<Task> createTaskRecords(List<Task> parentTasks, Map<String,Config> config, Logger logger) {
        Dao<SlidePosList> slidePosListDao = workflow.getInstanceDb().table(SlidePosList.class);

        List<Task> tasks = new ArrayList<Task>();
        for (Task parentTask : parentTasks.size()>0? parentTasks.toArray(new Task[]{}) : new Task[]{null}) {
            Task task = new Task(this.moduleId, Status.NEW);
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
                            new Integer(task.getId()).toString(), "slideId", slideIdConf.getValue()));
                }

                // get the position lists
                Config roiFinderModuleConf = this.workflow.getModuleConfig().selectOne(
                        where("id", this.moduleId).
                        and("key","roiFinderModule"));
                if (roiFinderModuleConf == null) throw new RuntimeException("Config roiFinderModule not found!");
                List<SlidePosList> slidePosLists = slidePosListDao.select(
                        where("moduleId", roiFinderModuleConf.getValue()).
                        and("slideId", new Integer(slideIdConf.getValue())));
                
                // remove old slide pos lists
                slidePosListDao.delete(
                        where("moduleId", this.moduleId).
                        and("slideId", new Integer(slideIdConf.getValue())));

                // save the original X and Y positions as property values
                for (SlidePosList spl : slidePosLists) {
                    PositionList posList = spl.getPositionList();
                    MultiStagePosition[] msps = posList.getPositions();
                    for (MultiStagePosition msp : msps) {
                        for (int i=0; i<msp.size(); ++i) {
                            StagePosition sp = msp.get(i);
                            if (sp.numAxes == 2 && sp.stageName.compareTo(msp.getDefaultXYStage()) == 0) {
                                msp.setProperty("origX", new Double(sp.x).toString());
                                msp.setProperty("origY", new Double(sp.y).toString());
                                break;
                            }
                        }
                    }
                    posList.setPositions(msps);

                    // store the translated position list into the database
                    SlidePosList slidePosList = new SlidePosList(this.moduleId, spl.getSlideId(), null, posList);
                    slidePosListDao.insert(slidePosList);
                }
            }
        }

        return tasks;
    }

    @Override public void runIntialize() { }

    @Override public Status run(Task task, Map<String, Config> config, Logger logger) {
        Dao<Image> imageDao = workflow.getInstanceDb().table(Image.class);
        Dao<Acquisition> acqDao = workflow.getInstanceDb().table(Acquisition.class);
        Dao<SlidePosList> slidePosListDao = workflow.getInstanceDb().table(SlidePosList.class);

        // get the slide ID conf
        Config slideIdConf = config.get("slideId");
        if (slideIdConf == null) throw new RuntimeException("slideId conf not defined!");

        // get The reference image from the reference module
        Config refSlideImagerModuleConf = config.get("refSlideImagerModule");
        if (refSlideImagerModuleConf == null) throw new RuntimeException("Config refSlideImagerModule not found!");
        Map<ImagePlus,Point2D.Double> refImages = new LinkedHashMap<>();
        List<Task> refTasks = workflow.getTaskStatus().select(
                where("moduleId", refSlideImagerModuleConf.getValue()));
        for (Task t : refTasks) {
            TaskConfig slideId = workflow.getTaskConfig().selectOne(
                    where("id", t.getId()).
                    and("key", "slideId"));
            if ((slideIdConf == null && slideId == null) || Objects.equals(slideIdConf.getValue(), slideId.getValue())) {
                TaskConfig imageId = workflow.getTaskConfig().selectOne(
                        where("id", t.getId()).
                        and("key", "imageId"));
                if (imageId != null) {
                    Image image = imageDao.selectOneOrDie(where("id", imageId.getValue()));
                    ImagePlus imp = image.getImagePlus(acqDao);
                    new ImageConverter(imp).convertToGray8();
                    TaggedImage taggedImage = image.getTaggedImage(acqDao);
                    try {
                        Double xPos = MDUtils.getXPositionUm(taggedImage.tags);
                        Double yPos = MDUtils.getYPositionUm(taggedImage.tags);
                        refImages.put(imp, new Point2D.Double(xPos, yPos));
                    }
                    catch (JSONException e) { throw new RuntimeException(e); }
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
        Map<ImagePlus,Point2D.Double> compareImages = new LinkedHashMap<>();
        List<Task> compareTasks = workflow.getTaskStatus().select(
                where("moduleId", compareSlideImagerModuleConf.getValue()));
        for (Task t : compareTasks) {
            TaskConfig imageId = workflow.getTaskConfig().selectOne(
                    where("id", t.getId()).
                    and("key", "imageId"));
            if (imageId != null) {
                Image image = imageDao.selectOneOrDie(where("id", imageId.getValue()));
                ImagePlus imp = image.getImagePlus(acqDao);
                new ImageConverter(imp).convertToGray8();
                TaggedImage taggedImage = image.getTaggedImage(acqDao);
                try {
                    Double xPos = MDUtils.getXPositionUm(taggedImage.tags);
                    Double yPos = MDUtils.getYPositionUm(taggedImage.tags);
                    compareImages.put(imp, new Point2D.Double(xPos, yPos));
                } 
                catch (JSONException e) {throw new RuntimeException(e);}
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
        Point2D.Double matchedRefCoords = compareImages.get(matchedReference);
        if (matchedRefCoords == null) throw new RuntimeException(
                String.format("Could not find matched reference image %s in comparison list!", 
                        matchedReference.getTitle()));
        logger.info(String.format("Using input image %s for position calibration", matchedReference.getTitle()));
        
        // convert between image coordinates and stage coordinates
        ModuleConfig pixelSizeConf = workflow.getModuleConfig().selectOne(
                where("id", refSlideImagerModuleConf.getValue()).
                and("key", "pixelSize"));
        if (pixelSizeConf == null) throw new RuntimeException("pixelSize conf not found for ref imager!");
        Double pixelSize = new Double(pixelSizeConf.getValue());

        ModuleConfig hiResPixelSizeConf = workflow.getModuleConfig().selectOne(
                where("id", compareSlideImagerModuleConf.getValue()).
                and("key", "pixelSize"));
        if (hiResPixelSizeConf == null) throw new RuntimeException("hires pixelSize conf not found for compare imager!");
        Double hiResPixelSize = new Double(hiResPixelSizeConf.getValue());

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
        
        // translate = actual - expected
        // where actual is: the center of the fitted ROI - the center of the reference image,
        //     scaled (multiplied) by the pixel size and by the invert axis value (the stage coordinates scale factor)
        //     translated (added) to the reference stage coordinates,
        // and expected is: the stage coordinates of the comparison image from the position list
        Point2D.Double translateStage = roi == null? 
            new Point2D.Double(0.0, 0.0) :
            new Point2D.Double(
                (((roi.getXBase() + roi.getFloatWidth() / 2.0) 
                    - (double)refImage.getWidth() / 2.0) 
                        * pixelSize * invertXAxis) 
                + refCoords.getX() 
                - matchedRefCoords.getX(), 
                (((roi.getYBase() + roi.getFloatHeight() / 2.0) 
                    - (double)refImage.getHeight() / 2.0) 
                        * pixelSize * invertYAxis) 
                + refCoords.getY() 
                - matchedRefCoords.getY());
        
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

        // get the position lists
        List<SlidePosList> slidePosLists = slidePosListDao.select(
                where("moduleId", this.moduleId).
                and("slideId", new Integer(slideIdConf.getValue())));
        
        // apply the translation matrix to the position list to derive a new position list
        for (SlidePosList spl : slidePosLists) {
            PositionList posList = spl.getPositionList();
            MultiStagePosition[] msps = posList.getPositions();
            for (MultiStagePosition msp : msps) {
                if (!msp.hasProperty("origX")) throw new RuntimeException(String.format("MSP property origX is missing!: %s", msp));
                Double origX = new Double(msp.getProperty("origX"));
                if (!msp.hasProperty("origY")) throw new RuntimeException(String.format("MSP property origY is missing!: %s", msp));
                Double origY = new Double(msp.getProperty("origY"));

                for (int i=0; i<msp.size(); ++i) {
                    StagePosition sp = msp.get(i);
                    if (sp.numAxes == 2 && sp.stageName.compareTo(msp.getDefaultXYStage()) == 0) {
                        sp.x = origX + translateStage.getX();
                        sp.y = origY + translateStage.getY();
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
    
    @Override public String getTitle() {
        return this.getClass().getSimpleName();
    }

    @Override public String getDescription() {
        return this.getClass().getSimpleName();
    }

    @Override public TaskType getTaskType() {
        return TaskType.SERIAL;
    }

    @Override public void cleanup(Task task, Map<String,Config> config, Logger logger) { }
}
