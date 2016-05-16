package org.bdgp.OpenHiCAMM.Modules;

import java.awt.Component;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;

import org.bdgp.OpenHiCAMM.Dao;
import org.bdgp.OpenHiCAMM.ImageLog.ImageLogRecord;
import org.bdgp.OpenHiCAMM.ImageLog.ImageLogRunner;
import org.bdgp.OpenHiCAMM.Logger;
import org.bdgp.OpenHiCAMM.OpenHiCAMM;
import org.bdgp.OpenHiCAMM.Util;
import org.bdgp.OpenHiCAMM.ValidationError;
import org.bdgp.OpenHiCAMM.WorkflowRunner;
import org.bdgp.OpenHiCAMM.DB.Acquisition;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.Image;
import org.bdgp.OpenHiCAMM.DB.ModuleConfig;
import org.bdgp.OpenHiCAMM.DB.PoolSlide;
import org.bdgp.OpenHiCAMM.DB.Slide;
import org.bdgp.OpenHiCAMM.DB.SlidePosList;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.Task.Status;
import org.bdgp.OpenHiCAMM.DB.TaskConfig;
import org.bdgp.OpenHiCAMM.DB.TaskDispatch;
import org.bdgp.OpenHiCAMM.DB.WorkflowModule;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Configuration;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.ImageLogger;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Module;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.dialogs.AcqControlDlg;
import org.micromanager.events.DisplayCreatedEvent;
import org.micromanager.events.EventManager;
import org.micromanager.MMOptions;
import org.micromanager.MMStudio;
import org.micromanager.acquisition.AcquisitionWrapperEngine;
import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.api.Autofocus;
import org.micromanager.api.ImageCache;
import org.micromanager.api.ImageCacheListener;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.MMSerializationException;

import com.google.common.eventbus.Subscribe;

import static org.bdgp.OpenHiCAMM.Util.set;
import static org.bdgp.OpenHiCAMM.Util.where;

public class SlideImager implements Module, ImageLogger {
	private static final long DUMMY_SLEEP = 500;

    private static final boolean SANITY_CHECK = false;

	WorkflowRunner workflowRunner;
    WorkflowModule workflowModule;
    AcqControlDlg acqControlDlg;
    ScriptInterface script;
    AcquisitionWrapperEngine engine;

    @Override
    public void initialize(WorkflowRunner workflowRunner, WorkflowModule workflowModule) {
        this.workflowRunner = workflowRunner;
        this.workflowModule = workflowModule;
        OpenHiCAMM openhicamm = workflowRunner.getOpenHiCAMM();
        this.script = openhicamm.getApp();

        Preferences prefs = Preferences.userNodeForPackage(this.script.getClass());
        MMOptions options = new MMOptions();
        options.loadSettings();
        if (this.script != null) {
            this.engine = MMStudio.getInstance().getAcquisitionEngine();
            this.acqControlDlg = new AcqControlDlg(this.engine, prefs, this.script, options);
        }

        // set initial configs
        workflowRunner.getModuleConfig().insertOrUpdate(
                new ModuleConfig(this.workflowModule.getId(), "canImageSlides", "yes"), 
                "id", "key");
    }
    
    /**
     * Load the acquisition settings and get the position list from the DB
     * @param conf The module configuration
     * @param logger The logging module
     * @return the position list
     */
    public PositionList loadPositionList(Map<String,Config> conf, Logger logger) {
        // first try to load a position list from the posListId conf
        // otherwise, try loading from the posListModuleId conf
    	PositionList positionList;
        if (conf.containsKey("posListModule")) {
            // get a sorted list of all the SlidePosList records for the posListModuleId module
            Config posListModuleConf = conf.get("posListModule");
            Dao<SlidePosList> posListDao = workflowRunner.getInstanceDb().table(SlidePosList.class);
            Integer slideId = new Integer(conf.get("slideId").getValue());
            WorkflowModule posListModule = this.workflowRunner.getWorkflow().selectOneOrDie(
                    where("name",posListModuleConf.getValue()));
            List<SlidePosList> posLists = posListDao.select(
                    where("slideId", slideId).
                    and("moduleId", posListModule.getId()));
            Collections.sort(posLists, new Comparator<SlidePosList>() {
                @Override public int compare(SlidePosList a, SlidePosList b) {
                    return a.getId()-b.getId();
                }});
            if (posLists.isEmpty()) {
                throw new RuntimeException("Position list from module \""+posListModuleConf.getValue()+"\" not found in database");
            }
            // Merge the list of PositionLists into a single positionList
            positionList = posLists.get(0).getPositionList();
            for (int i=1; i<posLists.size(); ++i) {
                SlidePosList spl = posLists.get(i);
                PositionList pl = spl.getPositionList();
                for (int j=0; j<pl.getNumberOfPositions(); ++j) {
                    MultiStagePosition msp = pl.getPosition(j);
                    // sanitize the MSP label. This will be used as the directory name for each image
                    String label = msp.getLabel().replaceAll("[ ():,]+", ".").replaceAll("[=]+", "");
                    msp.setLabel(label);
                    positionList.addPosition(msp);
                }
            }
            // log the position list to the console
            try {
				logger.fine(String.format("%s: Read position list from module %s:%n%s", 
						this.workflowModule.getName(), conf.get("posListModule"), positionList.serialize()));
			} 
            catch (MMSerializationException e) {throw new RuntimeException(e);}
            // load the position list into the acquisition engine
            try { this.script.setPositionList(positionList); } 
            catch (MMScriptException e) {throw new RuntimeException(e);}
            this.engine.setPositionList(positionList);
        }
        // otherwise, load a position list from a file
        else if (conf.containsKey("posListFile")) {
            Config posList = conf.get("posListFile");
            logger.fine(String.format("%s: Loading position list from file: %s", this.workflowModule.getName(), Util.escape(posList.getValue())));
            File posListFile = new File(posList.getValue());
            if (!posListFile.exists()) {
                throw new RuntimeException("Cannot find position list file "+posListFile.getPath());
            }
            try {
                positionList = new PositionList();
                positionList.load(posListFile.getPath());
                try { this.script.setPositionList(positionList); } 
                catch (MMScriptException e) {throw new RuntimeException(e);}
                this.engine.setPositionList(positionList);
            } 
            catch (MMException e) {throw new RuntimeException(e);}
        }
        else {
        	throw new RuntimeException("No position list was given!");
        }

        // Load the settings and position list from the settings files
        if (conf.containsKey("acqSettingsFile")) {
            Config acqSettings = conf.get("acqSettingsFile");
            logger.fine(String.format("%s: Loading acquisition settings from file: %s", this.workflowModule.getName(), Util.escape(acqSettings.getValue())));
            File acqSettingsFile = new File(acqSettings.getValue());
            if (!acqSettingsFile.exists()) {
                throw new RuntimeException("Cannot find acquisition settings file "+acqSettingsFile.getPath());
            }
            try { acqControlDlg.loadAcqSettingsFromFile(acqSettingsFile.getPath()); } 
            catch (MMScriptException e) {throw new RuntimeException(e);}
        }
        return positionList;
    }
    
    @Subscribe public void showDisplay(DisplayCreatedEvent e) {
        e.getDisplayWindow().setVisible(true);
    }

    @Override
    public Status run(Task task, final Map<String,Config> conf, final Logger logger) {
    	logger.fine(String.format("Running task: %s", task));
    	for (Config c : conf.values()) {
    		logger.fine(String.format("Using configuration: %s", c));
    	}

    	Config imageLabelConf = conf.get("imageLabel");
    	if (imageLabelConf == null) throw new RuntimeException(String.format(
    	        "Could not find imageLabel conf for task %s!", task));
    	String imageLabel = imageLabelConf.getValue();
        logger.fine(String.format("Using imageLabel: %s", imageLabel));
        int[] indices = MDUtils.getIndices(imageLabel);
        if (indices == null || indices.length < 4) throw new RuntimeException(String.format(
                "Invalid indices parsed from imageLabel %s", imageLabel));
        
        // If this is the acqusition task 0_0_0_0, start the acquisition engine
        if (indices[0] == 0 && indices[1] == 0 && indices[2] == 0 && indices[3] == 0) {
            // Make sure the acquisition control dialog was initialized
            if (this.acqControlDlg == null) {
                throw new RuntimeException("acqControlDlg is not initialized!");
            }
            
            // load the position list and acquistion settings
            final PositionList posList = this.loadPositionList(conf, logger);

            final VerboseSummary verboseSummary = getVerboseSummary();
            logger.info(String.format("Verbose summary:"));
            for (String line : verboseSummary.summary.split("\n")) workflowRunner.getLogger().info(String.format("    %s", line));
            final int totalImages = verboseSummary.channels * verboseSummary.slices * verboseSummary.frames * verboseSummary.positions;

            // Get Dao objects ready for use
            final Dao<TaskConfig> taskConfigDao = workflowRunner.getInstanceDb().table(TaskConfig.class);
            final Dao<Acquisition> acqDao = workflowRunner.getInstanceDb().table(Acquisition.class);
            final Dao<Image> imageDao = workflowRunner.getInstanceDb().table(Image.class);
            final Dao<Slide> slideDao = workflowRunner.getInstanceDb().table(Slide.class);

            Date startAcquisition = new Date();

            // get the slide ID from the config
            if (!conf.containsKey("slideId")) throw new RuntimeException("No slideId found for image!");
            final Integer slideId = new Integer(conf.get("slideId").getValue());
            logger.info(String.format("Using slideId: %d", slideId));
            // get the slide's experiment ID
            Slide slide = slideId != null? slideDao.selectOne(where("id", slideId)) : null;
            String experimentId = slide != null? slide.getExperimentId() : null;
            
            // get the poolSlide record if it exists
            Dao<PoolSlide> poolSlideDao = this.workflowRunner.getInstanceDb().table(PoolSlide.class);
            PoolSlide poolSlide = null;
            if (conf.containsKey("loadPoolSlideId")) {
                poolSlide = poolSlideDao.selectOneOrDie(where("id", new Integer(conf.get("loadPoolSlideId").getValue())));
            }

            // set the acquisition name
            // Set rootDir and acqName
            final String rootDir = new File(
                    workflowRunner.getWorkflowDir(), 
                    workflowRunner.getWorkflowInstance().getStorageLocation()).getPath();
            String acqName = String.format("acquisition_%s_%s%s%s", 
                    new SimpleDateFormat("yyyyMMddHHmmss").format(startAcquisition), 
                    this.workflowModule.getName(), 
                    poolSlide != null? String.format("_C%dS%02d", poolSlide.getCartridgePosition(), poolSlide.getSlidePosition()) : "",
                    slide != null? String.format("_%s", slide.getName()) : "",
                    experimentId != null? String.format("_%s", experimentId.replaceAll("[\\/ :]+","_")) : "");

            CMMCore core = this.script.getMMCore();
            logger.fine(String.format("This task is the acquisition task")); 
            logger.info(String.format("Using rootDir: %s", rootDir)); 
            logger.info(String.format("Requesting to use acqName: %s", acqName)); 
            
            // Move stage to starting position and take some dummy pictures to adjust the camera
            if (conf.containsKey("dummyImageCount") && 
                posList.getNumberOfPositions() > 0) 
            {
                Integer dummyImageCount = new Integer(conf.get("dummyImageCount").getValue());
            	logger.info("Moving stage to starting position");
                MultiStagePosition pos = posList.getPosition(0);
                SlideImager.moveStage(workflowModule.getName(), core, pos.getX(), pos.getY(), logger);

                // Acquire N dummy images to calibrate the camera
                for (int i=0; i<dummyImageCount; ++i) {
                	// Take a picture but don't save it
                    try { core.snapImage(); } 
                    catch (Exception e) {throw new RuntimeException(e);}
                    logger.info(String.format("Acquired %d dummy images to calibrate the camera...", i+1));
                    // wait a second before taking the next one.
                    try { Thread.sleep(DUMMY_SLEEP); } 
                    catch (InterruptedException e) {logger.info("Sleep thread was interrupted");}
                }
            }
            
            // build a map of imageLabel -> sibling task record
            final Dao<Task> taskDao = this.workflowRunner.getTaskStatus();
            Dao<TaskDispatch> taskDispatchDao = this.workflowRunner.getTaskDispatch();
            List<TaskDispatch> tds = new ArrayList<>();
            for (Task t : taskDao.select(where("moduleId", this.workflowModule.getId()))) {
                TaskConfig slideIdConf = this.workflowRunner.getTaskConfig().selectOne(
                        where("id", t.getId()).
                        and("key", "slideId").
                        and("value", slideId));
                if (slideIdConf != null) {
                    tds.addAll(taskDispatchDao.select(where("taskId", t.getId())));
                }
            }
            final Map<String,Task> tasks = new LinkedHashMap<String,Task>();
            if (!tds.isEmpty()) {
                Collections.sort(tds, new Comparator<TaskDispatch>() {
                    @Override public int compare(TaskDispatch a, TaskDispatch b) {
                        return a.getTaskId() - b.getTaskId();
                    }});
                for (TaskDispatch t : tds) {
                	Task tt = taskDao.selectOneOrDie(where("id", t.getTaskId()));
                	TaskConfig imageLabelConf2 = taskConfigDao.selectOneOrDie(
                			where("id", new Integer(tt.getId()).toString()).
                			and("key", "imageLabel"));
                    tasks.put(imageLabelConf2.getValue(), tt);
                }
            }
            else {
                List<Task> tts = taskDao.select(where("moduleId", this.workflowModule.getId()));
                Collections.sort(tts, new Comparator<Task>() {
                    @Override public int compare(Task a, Task b) {
                        return a.getId() - b.getId();
                    }});
            	for (Task tt : tts) {
                	TaskConfig imageLabelConf2 = taskConfigDao.selectOneOrDie(
                			where("id", new Integer(tt.getId()).toString()).
                			and("key", "imageLabel"));
                    tasks.put(imageLabelConf2.getValue(), tt);
            	}
            }
            
            // close all open acquisition windows
            for (String name : MMStudio.getInstance().getAcquisitionNames()) {
                try { MMStudio.getInstance().closeAcquisitionWindow(name); } 
                catch (MMScriptException e) { /* do nothing */ }
            }

            // set the initial Z Position
            if (conf.containsKey("initialZPos")) {
                Double initialZPos = new Double(conf.get("initialZPos").getValue());
                logger.info(String.format("Setting initial Z Position to: %.02f", initialZPos));
                String focusDevice = core.getFocusDevice();

                final double EPSILON = 1.0;
                try { 
                    Double currentPos = core.getPosition(focusDevice);
                    while (Math.abs(currentPos-initialZPos) > EPSILON) {
                        core.setPosition(focusDevice, initialZPos); 
                        core.waitForDevice(focusDevice);
                        Thread.sleep(500);
                        currentPos = core.getPosition(focusDevice);
                    }
                } 
                catch (Exception e1) {throw new RuntimeException(e1);}
            }

            // Start the acquisition engine. This runs asynchronously.
            try {
                EventManager.register(this);
                
                this.workflowRunner.getTaskConfig().insertOrUpdate(
                        new TaskConfig(task.getId(),
                                "startAcquisition", 
                                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(startAcquisition)), 
                        "id", "key");
                
                logger.info(String.format("Now running the image acquisition sequence, please wait..."));
                String returnAcqName = acqControlDlg.runAcquisition(acqName, rootDir);
                if (returnAcqName == null) {
                    throw new RuntimeException("acqControlDlg.runAcquisition returned null acquisition name");
                }

                // get the prefix name and log it
                JSONObject summaryMetadata = this.engine.getSummaryMetadata();
                final String prefix;
                try { prefix = summaryMetadata.getString("Prefix"); } 
                catch (JSONException e) { throw new RuntimeException(e); }
                logger.info(String.format("Acquisition was saved to root directory %s, prefix %s", 
                        Util.escape(rootDir), Util.escape(prefix)));
                
                // Write the acquisition record
                final Acquisition acquisition = new Acquisition(returnAcqName, prefix, rootDir);
                acqDao.insertOrUpdate(acquisition,"prefix","directory");
                acqDao.reload(acquisition);
                logger.info(String.format("Using acquisition record: %s", acquisition));

                // add an image cache listener to eagerly kick off downstream processing after each image
                // is received.
                final ImageCache acqImageCache = this.engine.getImageCache();
                acqImageCache.addImageCacheListener(new ImageCacheListener() {
                    @Override public void imageReceived(final TaggedImage taggedImage) {
                        // Log how many images have been acquired so far
                        Set<String> taggedImages = new HashSet<String>(acqImageCache.imageKeys());
                        String label = MDUtils.getLabel(taggedImage.tags);

                        // Get the task record that should be eagerly dispatched.
                        final Task dispatchTask = tasks.get(label);
                        if (dispatchTask == null) throw new RuntimeException(String.format(
                                "No SlideImager task was created for image with label %s!", label));
                        
                        try {
                            if (taggedImage.pix == null) throw new RuntimeException(String.format(
                                    "%s: taggedImage.pix is null!", label));

                            String positionName = null;
                            try { positionName = MDUtils.getPositionName(taggedImage.tags); } 
                            catch (JSONException e) {}
                            
                            taggedImages.add(label);
                            logger.info(String.format("Acquired image: %s [%d/%d images]", 
                                    positionName != null?
                                        String.format("%s (%s)", positionName, label) :
                                        label,
                                    taggedImages.size(),
                                    totalImages));

                            int[] indices = MDUtils.getIndices(label);
                            if (indices == null || indices.length < 4) throw new RuntimeException(String.format(
                                    "Bad image label from MDUtils.getIndices(): %s", label));
                            // Don't eagerly dispatch the acquisition thread. This thread must not be set to success
                            // until the acquisition has finished, so the downstream processing for this task 
                            // must wait until the acquisition has finished.
                            if (!(indices[0] == 0 && indices[1] == 0 && indices[2] == 0 && indices[3] == 0)) {
                                // Make sure the position name of this image's metadata matches what we expected
                                // from the Task configuration.
                                TaskConfig positionNameConf = taskConfigDao.selectOneOrDie(
                                        where("id", new Integer(dispatchTask.getId()).toString()).and("key", "positionName"));
                                if (!positionName.equals(positionNameConf.getValue())) {
                                    throw new RuntimeException(String.format(
                                            "Position name mismatch! TaskConfig=%s, MDUtils.getPositionName=%s",
                                            positionNameConf, positionName));
                                }

                                // Write the image record and kick off downstream processing.
                                // create the image record
                                Image image = new Image(slideId, acquisition, indices[0], indices[1], indices[2], indices[3]);
                                imageDao.insertOrUpdate(image,"acquisitionId","channel","slice","frame","position");
                                logger.fine(String.format("Inserted/Updated image: %s", image));
                                imageDao.reload(image, "acquisitionId","channel","slice","frame","position");

                                // Store the Image ID as a Task Config variable
                                TaskConfig imageId = new TaskConfig(
                                        dispatchTask.getId(),
                                        "imageId",
                                        new Integer(image.getId()).toString());
                                taskConfigDao.insertOrUpdate(imageId,"id","key");
                                conf.put("imageId", imageId);
                                logger.fine(String.format("Inserted/Updated imageId config: %s", imageId));
                                
                                // eagerly run the Slide Imager task in order to dispatch downstream processing
                                logger.fine(String.format("Eagerly dispatching sibling task: %s", dispatchTask));
                                new Thread(new Runnable() {
                                    @Override public void run() {
                                        SlideImager.this.workflowRunner.run(dispatchTask, conf);
                                    }}).start();
                            }
                        }
                        catch (Throwable e) {
                            dispatchTask.setStatus(Status.ERROR);
                            taskDao.update(dispatchTask, "id");
                            throw new RuntimeException(e);
                        }
                    }
                    @Override public void imagingFinished(String path) { }
                });

                // wait until the current acquisition finishes
                while (acqControlDlg.isAcquisitionRunning()) {
                     try { Thread.sleep(1000); } 
                     catch (InterruptedException e) {
                         // if the thread is interrupted, abort the acquisition.
                         if (this.engine != null && this.engine.isAcquisitionRunning()) {
                             logger.warning("Aborting the acquisition...");
                             //this.engine.abortRequest();
                             this.engine.stop(true);
                             return Status.ERROR;
                         }
                     }
                }

                // Get the ImageCache object for this acquisition
                MMAcquisition mmacquisition = acquisition.getAcquisition(acqDao);
                ImageCache imageCache = mmacquisition.getImageCache();
                if (imageCache == null) throw new RuntimeException("MMAcquisition object was not initialized; imageCache is null!");
                
                // Wait for the image cache to finish...
                while (!imageCache.isFinished()) {
                    try { 
                        logger.info("Waiting for the ImageCache to finish...");
                        Thread.sleep(1000); 
                    } 
                    catch (InterruptedException e) { 
                        logger.warning("Thread was interrupted while waiting for the Image Cache to finish");
                        return Status.ERROR;
                    }
                }
                if (!imageCache.isFinished()) throw new RuntimeException("ImageCache is not finished!");

                Date endAcquisition = new Date();
                this.workflowRunner.getTaskConfig().insertOrUpdate(
                        new TaskConfig(task.getId(),
                                "endAcquisition", 
                                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(endAcquisition)), 
                        "id", "key");

                this.workflowRunner.getTaskConfig().insertOrUpdate(
                        new TaskConfig(task.getId(),
                                "acquisitionDuration", 
                                new Long(endAcquisition.getTime() - startAcquisition.getTime()).toString()), 
                        "id", "key");
                
                // get the autofocus duration from the autofocus object
                Autofocus autofocus = this.script.getAutofocus();
                if (new HashSet<String>(Arrays.asList(autofocus.getPropertyNames())).contains("autofocusDuration")) {
                    try {
                        this.workflowRunner.getTaskConfig().insertOrUpdate(
                                new TaskConfig(task.getId(),
                                        "autofocusDuration", 
                                        autofocus.getPropertyValue("autofocusDuration")),
                                "id", "key");
                    } 
                    catch (MMException e) { throw new RuntimeException(e); }
                }
                // reset the autofocus settings back to defaults
                autofocus.applySettings();

                // Get the set of taggedImage labels from the acquisition
                Set<String> taggedImages = imageCache.imageKeys();
                logger.info(String.format("Found %d taggedImages: %s", taggedImages.size(), taggedImages.toString()));
                
                // Make sure the set of acquisition image labels matches what we expected.
                if (!taggedImages.equals(tasks.keySet())) {
                    throw new RuntimeException(String.format(
                        "Error: Unexpected image set from acquisition! acquisition image count is %d, expected %d!%n"+
                        "Verbose summary: %s%nFrom acquisition: %s%nFrom task config: %s",
                        taggedImages.size(), tasks.size(), 
                        totalImages, 
                        taggedImages, tasks.keySet()));
                }
                
                // Add any missing Image record for e.g. the acquisition task. This is also important because
                // the ImageListener above is added *after* the acquisition is started, so there's a chance
                // we may have missed the first few imageReceived events. This race condition is currently 
                // unavoidable due to the way that MMAcquisition is implemented. Any remaining SlideImager tasks
                // that weren't eagerly dispatched will be dispatched normally by the workflowRunner after this 
                // task completes.
                for (String label : taggedImages) {
                    // make sure none of the taggedImage.pix values are null
                    int[] idx = MDUtils.getIndices(label);
                    if (idx == null || idx.length < 4) throw new RuntimeException(String.format(
                            "Bad image label from MDUtils.getIndices(): %s", label));

                    Task t = tasks.get(label);
                    if (t == null) throw new RuntimeException(String.format(
                            "Could not get task record for image with label %s!", label));

                    // The following sanity checks are very time-consuming and don't seem
                    // to be necessary, so I've disabled them by default using the SANITY_CHECK flag.
                    if (SANITY_CHECK) {
                        TaggedImage taggedImage = imageCache.getImage(idx[0], idx[1], idx[2], idx[3]);
                        if (taggedImage.pix == null) throw new RuntimeException(String.format(
                                "%s: taggedImage.pix is null!", label));

                        // get the positionName
                        String positionName;
                        try { positionName = MDUtils.getPositionName(taggedImage.tags); } 
                        catch (JSONException e) {throw new RuntimeException(e);}

                        // Make sure the position name of this image's metadata matches what we expected
                        // from the Task configuration.
                        TaskConfig positionNameConf = taskConfigDao.selectOneOrDie(
                                where("id", new Integer(t.getId()).toString()).and("key", "positionName"));
                        if (!positionName.equals(positionNameConf.getValue())) {
                            throw new RuntimeException(String.format(
                                    "Position name mismatch! TaskConfig=%s, MDUtils.getPositionName=%s",
                                    positionNameConf, positionName));
                        }
                    }


                    // Insert/Update image DB record
                    Image image = new Image(slideId, acquisition, idx[0], idx[1], idx[2], idx[3]);
                    imageDao.insertOrUpdate(image,"acquisitionId","channel","slice","frame","position");
                    logger.fine(String.format("Inserted image: %s", image));
                    imageDao.reload(image, "acquisitionId","channel","slice","frame","position");
                    
                    // Store the Image ID as a Task Config variable
                    TaskConfig imageId = new TaskConfig(
                            t.getId(),
                            "imageId",
                            new Integer(image.getId()).toString());
                    taskConfigDao.insertOrUpdate(imageId,"id","key");
                    conf.put("imageId", imageId);
                    logger.fine(String.format("Inserted/Updated imageId config: %s", imageId));
                }
            }
            finally {
                EventManager.unregister(this);
            }
        }
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
            SlideImagerDialog slideImagerDialog = new SlideImagerDialog(acqControlDlg, SlideImager.this.workflowRunner);

            @Override
            public Config[] retrieve() {
                List<Config> configs = new ArrayList<Config>();
                if (slideImagerDialog.acqSettingsText.getText().length()>0) {
                    configs.add(new Config(workflowModule.getId(), 
                            "acqSettingsFile", 
                            slideImagerDialog.acqSettingsText.getText()));
                }
                if (slideImagerDialog.posListText.getText().length()>0) {
                    configs.add(new Config(workflowModule.getId(), 
                            "posListFile", 
                            slideImagerDialog.posListText.getText()));
                }
                if (slideImagerDialog.moduleName.getSelectedIndex()>0) {
                    configs.add(new Config(workflowModule.getId(), 
                            "posListModule", 
                            slideImagerDialog.moduleName.getSelectedItem().toString()));
                }
                
                if (slideImagerDialog.dummyImageCount.getValue() != null) {
                    configs.add(new Config(workflowModule.getId(), 
                            "dummyImageCount", 
                            slideImagerDialog.dummyImageCount.getValue().toString()));
                }

                if (((Double)slideImagerDialog.pixelSize.getValue()).doubleValue() != 0.0) {
                    configs.add(new Config(workflowModule.getId(), "pixelSize", slideImagerDialog.pixelSize.getValue().toString()));
                }

                if (slideImagerDialog.invertXAxisYes.isSelected()) {
                	configs.add(new Config(workflowModule.getId(), "invertXAxis", "yes"));
                }
                else if (slideImagerDialog.invertXAxisNo.isSelected()) {
                	configs.add(new Config(workflowModule.getId(), "invertXAxis", "no"));
                }
                
                if (slideImagerDialog.invertYAxisYes.isSelected()) {
                	configs.add(new Config(workflowModule.getId(), "invertYAxis", "yes"));
                }
                else if (slideImagerDialog.invertYAxisNo.isSelected()) {
                	configs.add(new Config(workflowModule.getId(), "invertYAxis", "no"));
                }
                
                if (slideImagerDialog.setInitZPosYes.isSelected()) {
                    configs.add(new Config(workflowModule.getId(), "initialZPos", slideImagerDialog.initialZPos.getValue().toString()));
                }
                return configs.toArray(new Config[0]);
            }
            @Override
            public Component display(Config[] configs) {
                Map<String,Config> conf = new HashMap<String,Config>();
                for (Config config : configs) {
                    conf.put(config.getKey(), config);
                }
                if (conf.containsKey("acqSettingsFile")) {
                    Config acqDlgSettings = conf.get("acqSettingsFile");
                    slideImagerDialog.acqSettingsText.setText(acqDlgSettings.getValue());
                }
                if (conf.containsKey("posListFile")) {
                    Config posList = conf.get("posListFile");
                    slideImagerDialog.posListText.setText(posList.getValue());
                }
                if (conf.containsKey("posListModule")) {
                    Config posListModuleConf = conf.get("posListModule");
                    slideImagerDialog.moduleName.setSelectedItem(posListModuleConf.getValue());
                }
                
                if (conf.containsKey("dummyImageCount")) {
                    Config dummyImageCount = conf.get("dummyImageCount");
                    slideImagerDialog.dummyImageCount.setValue(new Integer(dummyImageCount.getValue()));
                }

                if (conf.containsKey("pixelSize")) {
                    slideImagerDialog.pixelSize.setValue(new Double(conf.get("pixelSize").getValue()));
                }
                
                if (conf.containsKey("invertXAxis")) {
                	if (conf.get("invertXAxis").getValue().equals("yes")) {
                		slideImagerDialog.invertXAxisYes.setSelected(true);
                		slideImagerDialog.invertXAxisNo.setSelected(false);
                	}
                	else if (conf.get("invertXAxis").getValue().equals("no")) {
                		slideImagerDialog.invertXAxisYes.setSelected(false);
                		slideImagerDialog.invertXAxisNo.setSelected(true);
                	}
                }

                if (conf.containsKey("invertYAxis")) {
                	if (conf.get("invertYAxis").getValue().equals("yes")) {
                		slideImagerDialog.invertYAxisYes.setSelected(true);
                		slideImagerDialog.invertYAxisNo.setSelected(false);
                	}
                	else if (conf.get("invertYAxis").getValue().equals("no")) {
                		slideImagerDialog.invertYAxisYes.setSelected(false);
                		slideImagerDialog.invertYAxisNo.setSelected(true);
                	}
                }
                
                if (conf.containsKey("initialZPos")) {
                    slideImagerDialog.setInitZPosYes.setSelected(true);
                    slideImagerDialog.initialZPos.setValue(new Double(conf.get("initialZPos").getValue()));
                }
                else {
                    slideImagerDialog.setInitZPosNo.setSelected(true);
                    slideImagerDialog.initialZPos.setValue(new Double(0.0));
                }
                return slideImagerDialog;
            }
            @Override
            public ValidationError[] validate() {
                List<ValidationError> errors = new ArrayList<ValidationError>();
                File acqSettingsFile = new File(slideImagerDialog.acqSettingsText.getText());
                if (!acqSettingsFile.exists()) {
                    errors.add(new ValidationError(workflowModule.getName(), "Acquisition settings file "+acqSettingsFile.toString()+" not found."));
                }
                if (slideImagerDialog.posListText.getText().length()>0) {
                    File posListFile = new File(slideImagerDialog.posListText.getText());
                    if (!posListFile.exists()) {
                        errors.add(new ValidationError(workflowModule.getName(), "Position list file "+posListFile.toString()+" not found."));
                    }
                }
                if (!((slideImagerDialog.posListText.getText().length()>0? 1: 0)
                        + (slideImagerDialog.moduleName.getSelectedIndex()>0? 1: 0) == 1))
                {
                    errors.add(new ValidationError(workflowModule.getName(), 
                            "You must enter one of either a position list file, or a position list name."));
                }
                if ((Double)slideImagerDialog.pixelSize.getValue() <= 0.0) {
                    errors.add(new ValidationError(workflowModule.getName(), 
                            "Pixel size must be greater than zero."));
                }

                return errors.toArray(new ValidationError[0]);
            }
        };
    }
    
    
    @Override
    public List<Task> createTaskRecords(List<Task> parentTasks, Map<String,Config> config, Logger logger) {
        Dao<Slide> slideDao = workflowRunner.getInstanceDb().table(Slide.class);
        Dao<ModuleConfig> moduleConfig = workflowRunner.getInstanceDb().table(ModuleConfig.class);
        Dao<TaskConfig> taskConfigDao = workflowRunner.getInstanceDb().table(TaskConfig.class);

        // Load all the module configuration into a HashMap
        Map<String,Config> conf = new HashMap<String,Config>();
        for (ModuleConfig c : moduleConfig.select(where("id",this.workflowModule.getId()))) {
            conf.put(c.getKey(), c);
            workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Using module config: %s", 
            		this.workflowModule.getName(), c));
        }
        
        // Create task records and connect to parent tasks
        // If no parent tasks were defined, then just create a single task instance.
        List<Task> tasks = new ArrayList<Task>();
        for (Task parentTask : parentTasks.size()>0? parentTasks.toArray(new Task[]{}) : new Task[]{null}) 
        {
            workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Connecting parent task %s", 
            		this.workflowModule.getName(), Util.escape(parentTask)));

        	// get the parent task configuration
        	Map<String,TaskConfig> parentTaskConf = new HashMap<String,TaskConfig>();
        	if (parentTask != null) {
                for (TaskConfig c : taskConfigDao.select(where("id",new Integer(parentTask.getId()).toString()))) {
                    parentTaskConf.put(c.getKey(), c);
                    workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Using task config: %s", 
                            this.workflowModule.getName(), c));
                }
        	}

        	// Get the associated slide.
        	Slide slide;
            if (parentTaskConf.containsKey("slideId")) {
            	slide = slideDao.selectOneOrDie(where("id",new Integer(parentTaskConf.get("slideId").getValue())));
            	workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Inherited slideId %s", this.workflowModule.getName(), parentTaskConf.get("slideId")));
            }
            // if posListModule is set, use the most recent slide
            else if (conf.containsKey("posListModule")) {
                List<Slide> slides = slideDao.select();
                Collections.sort(slides, new Comparator<Slide>() {
                    @Override public int compare(Slide a, Slide b) {
                        return b.getId()-a.getId();
                    }});
                if (slides.size() > 0) {
                    slide = slides.get(0);
                }
                else {
                    throw new RuntimeException("No slides were found!");
                }
            }
            // If no associated slide is registered, create a slide to represent this task
            else {
                String uuid = UUID.randomUUID().toString();
            	slide = new Slide(uuid);
            	slideDao.insertOrUpdate(slide,"experimentId");
            	slideDao.reload(slide, "experimentId");
            	workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Created new slide: %s", this.workflowModule.getName(), slide.toString()));
            }
            conf.put("slideId", new Config(this.workflowModule.getId(), "slideId", new Integer(slide.getId()).toString()));

            // Load the position list and set the acquisition settings
            PositionList positionList = loadPositionList(conf, workflowRunner.getLogger());
            
            // count the number of ROIs
            Set<String> rois = new HashSet<>();
            for (MultiStagePosition msp : positionList.getPositions()) {
                if (msp.getProperty("ROI") != null) {
                    rois.add(msp.getProperty("ROI"));
                }
            }

            // get the total images
            VerboseSummary verboseSummary = getVerboseSummary();
            workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Verbose summary:", this.workflowModule.getName()));
            for (String line : verboseSummary.summary.split("\n")) workflowRunner.getLogger().fine(String.format("    %s", line));
        
            int totalImages = verboseSummary.channels * verboseSummary.slices * verboseSummary.frames * verboseSummary.positions;
            workflowRunner.getLogger().fine(String.format("%s: getTotalImages: Will create %d images", 
                    this.workflowModule.getName(), totalImages));

            // Create the task records
            for (int c=0; c<verboseSummary.channels; ++c) {
                for (int s=0; s<verboseSummary.slices; ++s) {
                    for (int f=0; f<verboseSummary.frames; ++f) {
                        for (int p=0; p<verboseSummary.positions; ++p) {
                            // Create task record
                            Task task = new Task(this.workflowModule.getId(), Status.NEW);
                            workflowRunner.getTaskStatus().insert(task);
                            tasks.add(task);
                            workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Created task record: %s", 
                                    this.workflowModule.getName(), task));
                            
                            // Create taskConfig record for the image label
                            TaskConfig imageLabel = new TaskConfig(
                                    task.getId(),
                                    "imageLabel", 
                                    MDUtils.generateLabel(c, s, f, p));
                            taskConfigDao.insert(imageLabel);
                            workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Created task config: %s", 
                                    this.workflowModule.getName(), imageLabel));

                            // Create taskConfig record for the MSP label (positionName)
                            MultiStagePosition msp = positionList.getPosition(p);
                            TaskConfig positionName = new TaskConfig(
                                    task.getId(),
                                    "positionName", 
                                    msp.getLabel());
                            taskConfigDao.insert(positionName);
                            workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Created task config: %s", 
                                    this.workflowModule.getName(), positionName));
                            
                            // Store the MSP value as a JSON string
                            try {
                                PositionList mspPosList = new PositionList();
                                mspPosList.addPosition(msp);
                                String mspJson = new JSONObject(mspPosList.serialize()).
                                        getJSONArray("POSITIONS").getJSONObject(0).toString();
                                TaskConfig mspConf = new TaskConfig(
                                        task.getId(), "MSP", mspJson);
                                taskConfigDao.insert(mspConf);
                                conf.put("MSP", mspConf);
                                workflowRunner.getLogger().fine(String.format(
                                        "Inserted MultiStagePosition config: %s", mspJson));
                            } 
                            catch (MMSerializationException e) {throw new RuntimeException(e);} 
                            catch (JSONException e) {throw new RuntimeException(e);}

                            // Transfer MultiStagePosition property values to the task's configuration
                            for (String propName : msp.getPropertyNames()) {
                                String property = msp.getProperty(propName);
                                TaskConfig prop = new TaskConfig(
                                        task.getId(), propName, property);
                                taskConfigDao.insert(prop);
                                conf.put(property, prop);
                                workflowRunner.getLogger().fine(String.format(
                                        "Inserted MultiStagePosition config: %s", prop));
                            }

                            // create taskConfig record for the slide ID
                            TaskConfig slideId = new TaskConfig(
                                    task.getId(),
                                    "slideId", 
                                    new Integer(slide.getId()).toString());
                            taskConfigDao.insert(slideId);
                            workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Created task config: %s", 
                                    this.workflowModule.getName(), slideId));
                            
                            // add some task configs to the first imaging task to assist in calculating metrics
                            if (c==0 && s==0 && f==0 && p==0) {
                                TaskConfig positionsConf = new TaskConfig(
                                        task.getId(),
                                        "positions", 
                                        new Integer(verboseSummary.positions).toString());
                                taskConfigDao.insert(positionsConf);
                                
                                TaskConfig roisConf = new TaskConfig(
                                        task.getId(),
                                        "ROIs", 
                                        new Integer(rois.size()).toString());
                                taskConfigDao.insert(roisConf);
                                
                                TaskConfig verboseSummaryConf = new TaskConfig(
                                        task.getId(),
                                        "verboseSummary", 
                                        verboseSummary.summary);
                                taskConfigDao.insert(verboseSummaryConf);
                            }

                            // Create task dispatch record
                            if (parentTask != null) {
                                TaskDispatch dispatch = new TaskDispatch(task.getId(), parentTask.getId());
                                workflowRunner.getTaskDispatch().insert(dispatch);
                                workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Created task dispatch record: %s", 
                                        this.workflowModule.getName(), dispatch));
                            }
                        }
                    }
                }
            }
        }
        return tasks;
    }
    
    public static class VerboseSummary {
        public String summary;
        public int frames;
        public int positions;
        public int slices;
        public int channels;
        public int totalImages;
        public String totalMemory;
        public String duration;
        public String[] order;
    }
    
    /**
     * Parse the verbose summary text returned by the AcqusitionWrapperEngine. 
     * Sadly, this is the only way to get some needed information since the getNumSlices(), etc. 
     * methods are private.
     * @return the VerboseSummary object
     */
    public VerboseSummary getVerboseSummary() {
        String summary = this.engine.getVerboseSummary();
        VerboseSummary verboseSummary = new VerboseSummary();
        verboseSummary.summary = summary;

        Pattern pattern = Pattern.compile("^Number of time points: ([0-9]+)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(summary);
        if (!matcher.find()) throw new RuntimeException(String.format(
                "Could not parse frames field from summary:%n%s", summary));
        verboseSummary.frames = new Integer(matcher.group(1));

        pattern = Pattern.compile("^Number of positions: ([0-9]+)$", Pattern.MULTILINE);
        matcher = pattern.matcher(summary);
        if (!matcher.find()) throw new RuntimeException(String.format(
                "Could not parse positions field from summary:%n%s", summary));
        verboseSummary.positions = new Integer(matcher.group(1));

        pattern = Pattern.compile("^Number of slices: ([0-9]+)$", Pattern.MULTILINE);
        matcher = pattern.matcher(summary);
        if (!matcher.find()) throw new RuntimeException(String.format(
                "Could not parse slices field from summary:%n%s", summary));
        verboseSummary.slices = new Integer(matcher.group(1));

        pattern = Pattern.compile("^Number of channels: ([0-9]+)$", Pattern.MULTILINE);
        matcher = pattern.matcher(summary);
        if (!matcher.find()) throw new RuntimeException(String.format(
                "Could not parse channels field from summary:%n%s", summary));
        verboseSummary.channels = new Integer(matcher.group(1));

        pattern = Pattern.compile("^Total images: ([0-9]+)$", Pattern.MULTILINE);
        matcher = pattern.matcher(summary);
        if (!matcher.find()) throw new RuntimeException(String.format(
                "Could not parse total images field from summary:%n%s", summary));
        verboseSummary.totalImages = new Integer(matcher.group(1));

        pattern = Pattern.compile("^Total memory: ([^\\n]+)$", Pattern.MULTILINE);
        matcher = pattern.matcher(summary);
        if (!matcher.find()) throw new RuntimeException(String.format(
                "Could not parse total memory field from summary:%n%s", summary));
        verboseSummary.totalMemory = matcher.group(1).trim();

        pattern = Pattern.compile("^Duration: ([^\\n]+)$", Pattern.MULTILINE);
        matcher = pattern.matcher(summary);
        if (!matcher.find()) throw new RuntimeException(String.format(
                "Could not parse duration field from summary:%n%s", summary));
        verboseSummary.duration = matcher.group(1).trim();

        pattern = Pattern.compile("^Order: ([^\\n]+)$", Pattern.MULTILINE);
        matcher = pattern.matcher(summary);
        if (matcher.find()) {
            verboseSummary.order = matcher.group(1).trim().split(",\\s*");
        }
        else {
            this.workflowRunner.getLogger().info(String.format(
                    "Could not parse order field from summary:%n%s", summary));
        }
        return verboseSummary;
    }
    
    @Override
    public TaskType getTaskType() {
        return Module.TaskType.SERIAL;
    }

    @Override public void cleanup(Task task, Map<String,Config> config, Logger logger) { }

    @Override
    public List<ImageLogRecord> logImages(final Task task, final Map<String,Config> config, final Logger logger) {
        List<ImageLogRecord> imageLogRecords = new ArrayList<ImageLogRecord>();

        // Add an image logger instance to the workflow runner for this module
        imageLogRecords.add(new ImageLogRecord(task.getName(), task.getName(),
                new FutureTask<ImageLogRunner>(new Callable<ImageLogRunner>() {
            @Override public ImageLogRunner call() throws Exception {
                // Get the Image record
                Config imageIdConf = config.get("imageId");
                if (imageIdConf != null) {
                    Integer imageId = new Integer(imageIdConf.getValue());

                    logger.info(String.format("Using image ID: %d", imageId));
                    Dao<Image> imageDao = workflowRunner.getInstanceDb().table(Image.class);
                    final Image image = imageDao.selectOneOrDie(where("id",imageId));
                    logger.info(String.format("Using image: %s", image));

                    // Initialize the acquisition
                    Dao<Acquisition> acqDao = workflowRunner.getInstanceDb().table(Acquisition.class);
                    Acquisition acquisition = acqDao.selectOneOrDie(where("id",image.getAcquisitionId()));
                    logger.info(String.format("Using acquisition: %s", acquisition));
                    MMAcquisition mmacquisition = acquisition.getAcquisition(acqDao);

                    // Get the image cache object
                    ImageCache imageCache = mmacquisition.getImageCache();
                    if (imageCache == null) throw new RuntimeException("Acquisition was not initialized; imageCache is null!");
                    // Get the tagged image from the image cache
                    TaggedImage taggedImage = image.getImage(imageCache);
                    logger.info(String.format("Got taggedImage from ImageCache: %s", taggedImage));

                    ImageLogRunner imageLogRunner = new ImageLogRunner(task.getName());
                    imageLogRunner.addImage(taggedImage, "The image");
                    return imageLogRunner;
                }
                return null;
            }
        })));
        return imageLogRecords;
    }

    @Override
    public void runInitialize() { }
    
    public static void moveStage(String moduleId, CMMCore core, double x, double y, Logger logger) {
    	core.setTimeoutMs(10000);
        String xyStage = core.getXYStageDevice();

        if (logger != null) logger.fine(String.format("%s: Moving stage to position: (%f,%f)", moduleId, x, y));
        try {
            double[] x_stage = new double[] {0.0};
            double[] y_stage = new double[] {0.0};
            core.getXYPosition(xyStage, x_stage, y_stage);
            if (logger != null) logger.fine(String.format("%s: Current stage position: (%f,%f)", moduleId, x_stage[0], y_stage[0]));

            // sometimes moving the stage throws, try at least 10 times
            int tries = 0;
            final int MAX_TRIES = 10;
            while(tries <= MAX_TRIES) {
                try { 
                    core.setXYPosition(xyStage, x, y); 
                    break;
                } 
                catch (Throwable e) { 
                    if (tries >= MAX_TRIES) throw new RuntimeException(e);
                }
                Thread.sleep(500);
                ++tries;
            }

            // wait for the stage to finish moving
            final long MAX_WAIT = 10000000000L; // 10 seconds
            long startTime = System.nanoTime();
            while (core.deviceBusy(xyStage)) {
                if (MAX_WAIT < System.nanoTime() - startTime) {
                    // If it's taking too long to move the stage, 
                    // try re-sending the stage movement command.
                    if (logger != null) logger.warning(String.format("%s: Stage is taking too long to move, re-sending stage move commands...", moduleId));
                    core.stop(xyStage);
                    Thread.sleep(500);
                    startTime = System.nanoTime();
                    core.setXYPosition(xyStage, x, y);
                }
                Thread.sleep(500);
            }
            
            // get the new stage position
            double[] x_stage_new = new double[] {0.0};
            double[] y_stage_new = new double[] {0.0};
            core.getXYPosition(xyStage, x_stage_new, y_stage_new);
            if (logger != null) logger.fine(String.format("%s: New stage position: (%f,%f)", moduleId, x_stage_new[0], y_stage_new[0]));
            final double EPSILON = 1000;
            if (!(Math.abs(x_stage_new[0]-x) < EPSILON && Math.abs(y_stage_new[0]-y) < EPSILON)) {
            	throw new RuntimeException(String.format("%s: Stage moved to wrong coordinates: (%.2f,%.2f)",
            			moduleId, x_stage_new[0], y_stage_new[0]));
            }
		} 
        catch (Throwable e) { 
        	StringWriter sw = new StringWriter();
        	e.printStackTrace(new PrintWriter(sw));
        	if (logger != null) logger.severe(String.format("%s: Failed to move stage to position (%.2f,%.2f): %s", moduleId, x,y, sw.toString()));
        	throw new RuntimeException(e);
        }
    }

    public Status setTaskStatusOnResume(Task task) {
    	TaskConfig imageLabelConf = this.workflowRunner.getTaskConfig().selectOne(
    	        where("id", task.getId()).
    	        and("key", "imageLabel"));
    	if (imageLabelConf == null) throw new RuntimeException(String.format(
    	        "Could not find imageLabel conf for task %s!", task));
    	String imageLabel = imageLabelConf.getValue();
        int[] indices = MDUtils.getIndices(imageLabel);
        if (indices == null || indices.length < 4) throw new RuntimeException(String.format(
                "Invalid indices parsed from imageLabel %s", imageLabel));
        
        if (indices[0] == 0 && indices[1] == 0 && indices[2] == 0 && indices[3] == 0) {
            // get this slide ID
            TaskConfig slideIdConf = this.workflowRunner.getTaskConfig().selectOne(
                    where("id", task.getId()).
                    and("key", "slideId"));
            Integer slideId = slideIdConf != null? new Integer(slideIdConf.getValue()) : null;

            // if this task has a slide ID
            if (slideId != null) {
                // get all tasks with same slide ID as this one
                List<TaskConfig> sameSlideId = this.workflowRunner.getTaskConfig().select(
                        where("key", "slideId").
                        and("value", slideId));
                List<Task> tasks = new ArrayList<>();
                for (TaskConfig tc : sameSlideId) {
                    tasks.addAll(this.workflowRunner.getTaskStatus().select(
                            where("id", tc.getId()).
                            and("moduleId", task.getModuleId())));
                }
                for (Task t : tasks) {
                    if (t.getStatus() != Status.SUCCESS || 
                        this.workflowRunner.getTaskConfig().selectOne(where("id", t.getId()).and("key", "imageId")) == null) 
                    {
                        for (Task t2 : tasks) {
                            this.workflowRunner.getTaskStatus().update(
                                    set("status", Status.NEW).
                                    and("dispatchUUID", null), 
                                    where("id", t2.getId()));
                        }
                        return Status.NEW;
                    }
                }
            }
            else {
                // get all tasks without a defined slide ID
                List<Task> tasks = new ArrayList<>();
                for (Task t : this.workflowRunner.getTaskStatus().select(where("moduleId", task.getModuleId()))) {
                    TaskConfig tc = this.workflowRunner.getTaskConfig().selectOne(
                            where("id", t.getId()).
                            and("key", "slideId"));
                    if (tc == null) tasks.add(t);
                }
                for (Task t : tasks) {
                    if (t.getStatus() != Status.SUCCESS) {
                        for (Task t2 : tasks) {
                            this.workflowRunner.getTaskStatus().update(
                                    set("status", Status.NEW).
                                    and("dispatchUUID", null), 
                                    where("id", t2.getId()));
                        }
                        return Status.NEW;
                    }
                }
            }
        }
        return task.getDispatchUUID() == null? task.getStatus() : null;
    }
}
