package org.bdgp.OpenHiCAMM.Modules;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
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
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import mmcorej.CMMCore;

import org.bdgp.OpenHiCAMM.Dao;
import org.bdgp.OpenHiCAMM.ImageLog.ImageLogRecord;
import org.bdgp.OpenHiCAMM.ImageLog.ImageLogRunner;
import org.bdgp.OpenHiCAMM.Logger;
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
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.internal.dialogs.AcqControlDlg;
import org.micromanager.internal.MMStudio;
import org.micromanager.acquisition.internal.AcquisitionWrapperEngine;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.internal.DefaultNewImageEvent;
import org.micromanager.AutofocusManager;
import org.micromanager.AutofocusPlugin;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;

import com.google.common.eventbus.Subscribe;

import static org.bdgp.OpenHiCAMM.Util.set;
import static org.bdgp.OpenHiCAMM.Util.where;

public class SlideImager implements Module, ImageLogger {
	private static final long DUMMY_SLEEP = 500;

	WorkflowRunner workflowRunner;
    WorkflowModule workflowModule;
    AcqControlDlg acqControlDlg;
    MMStudio script;
    AcquisitionWrapperEngine engine;

    @Override
    public void initialize(WorkflowRunner workflowRunner, WorkflowModule workflowModule) {
        this.workflowRunner = workflowRunner;
        this.workflowModule = workflowModule;
        this.script = MMStudio.getInstance();

        if (this.script != null) {
            this.engine = this.script.getAcquisitionEngine();
            this.acqControlDlg = new AcqControlDlg(this.engine, this.script);
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
     * @return the position list, or null if no position list was found
     */
    public PositionList loadPositionList(Map<String,Config> conf, Logger logger) {
        Dao<Task> taskDao = this.workflowRunner.getTaskStatus();

        // first try to load a position list from the posListId conf
        // otherwise, try loading from the posListModuleId conf
    	PositionList positionList;
        if (conf.containsKey("posListModule")) {
            // get a sorted list of all the SlidePosList records for the posListModuleId module
            Config posListModuleConf = conf.get("posListModule");
            Dao<SlidePosList> posListDao = workflowRunner.getWorkflowDb().table(SlidePosList.class);
            Integer slideId = Integer.parseInt(conf.get("slideId").getValue());
            WorkflowModule posListModule = this.workflowRunner.getWorkflow().selectOneOrDie(
                    where("name",posListModuleConf.getValue()));
            // get the list of slide position lists with valid task IDs or where task ID is null
            List<SlidePosList> posLists = new ArrayList<>();
            for (SlidePosList posList : posListDao.select(
                    where("slideId", slideId).
                    and("moduleId", posListModule.getId()))) 
            {
                if (posList.getTaskId() != null) {
                    Task checkTask = taskDao.selectOne(where("id",posList.getTaskId()));  
                    if (checkTask == null) continue;
                }
                posLists.add(posList);
            }
            if (posLists.isEmpty()) {
                return null;
            }
            // Merge the list of PositionLists into a single positionList
            Collections.sort(posLists, (a,b)->a.getId()-b.getId());
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
            logger.fine(String.format("%s: Read position list from module %s:%n%s", 
                    this.workflowModule.getName(), conf.get("posListModule"), positionList.toPropertyMap().toJSON()));
            // load the position list into the acquisition engine
            this.script.getPositionListManager().setPositionList(positionList);
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
                this.script.getPositionListManager().setPositionList(positionList);
                this.engine.setPositionList(positionList);
            } 
            catch (IOException e) {throw new RuntimeException(e);}
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
            catch (IOException e) {throw new RuntimeException(e);}
        }
        return positionList;
    }
    
    @Override
    public Status run(Task task, Map<String,Config> conf, Logger logger) {
        // Get Dao objects ready for use
        Dao<Task> taskDao = this.workflowRunner.getTaskStatus();
        Dao<TaskDispatch> taskDispatchDao = this.workflowRunner.getTaskDispatch();
        Dao<TaskConfig> taskConfigDao = workflowRunner.getWorkflowDb().table(TaskConfig.class);
        Dao<Acquisition> acqDao = workflowRunner.getWorkflowDb().table(Acquisition.class);
        Dao<Image> imageDao = workflowRunner.getWorkflowDb().table(Image.class);
        Dao<Slide> slideDao = workflowRunner.getWorkflowDb().table(Slide.class);

    	logger.fine(String.format("Running task: %s", task));
    	for (Config c : conf.values()) {
    		logger.fine(String.format("Using configuration: %s", c));
    	}
    	
        // get the list of parent tasks for this task
        List<Task> siblingTasks = this.workflowRunner.getTaskStatus().select(where("dispatchUUID",task.getDispatchUUID()));
        Set<Task> parentTaskSet = new HashSet<>();
        for (Task siblingTask : siblingTasks) {
            for (TaskDispatch td : this.workflowRunner.getTaskDispatch().select(where("taskId",siblingTask.getId()))) {
                parentTaskSet.addAll(this.workflowRunner.getTaskStatus().select(where("id",td.getParentTaskId())));
            }
        }
        List<Task> parentTasks = parentTaskSet.stream().collect(Collectors.toList());

        // get the slide ID
        Config slideIdConf = conf.get("slideId");
        Integer slideId = slideIdConf != null? Integer.parseInt(conf.get("slideId").getValue()) : null;
        Slide slide = slideId != null? slideDao.selectOne(where("id", slideId)) : null;

    	// if this is the loadDynamicTaskRecords task, then we need to dynamically create the task records now
        Config loadDynamicTaskRecordsConf = conf.get("loadDynamicTaskRecords");
        if (loadDynamicTaskRecordsConf != null && loadDynamicTaskRecordsConf.getValue().equals("yes")) {
            conf.put("dispatchUUID", new Config(task.getId(), "dispatchUUID", task.getDispatchUUID()));
            workflowRunner.createTaskRecords(this.workflowModule, parentTasks, conf, logger);
            return Status.SUCCESS;
        }

    	Config imageLabelConf = conf.get("imageLabel");
    	if (imageLabelConf == null) throw new RuntimeException(String.format(
    	        "Could not find imageLabel conf for task %s!", task));
    	String imageLabel = imageLabelConf.getValue();
        logger.fine(String.format("Using imageLabel: %s", imageLabel));
        int[] indices = Image.getIndices(imageLabel);
        if (indices == null || indices.length < 4) throw new RuntimeException(String.format(
                "Invalid indices parsed from imageLabel %s", imageLabel));
        
        // If this is the acqusition task 0_0_0_0, start the acquisition engine
        if (indices[0] == 0 && indices[1] == 0 && indices[2] == 0 && indices[3] == 0) {
            logger.info(String.format("Using slideId: %d", slideId));
            // Make sure the acquisition control dialog was initialized
            if (this.acqControlDlg == null) {
                throw new RuntimeException("acqControlDlg is not initialized!");
            }
            
            // load the position list and acquistion settings
            PositionList posList = this.loadPositionList(conf, logger);
            if (posList == null) {
                throw new RuntimeException("Could not load position list!");
            }

            VerboseSummary verboseSummary = getVerboseSummary();
            logger.info(String.format("Verbose summary:"));
            for (String line : verboseSummary.summary.split("\n")) workflowRunner.getLogger().info(String.format("    %s", line));
            int totalImages = verboseSummary.channels * verboseSummary.slices * verboseSummary.frames * verboseSummary.positions;

            Date startAcquisition = new Date();

            // get the slide ID from the config
            if (slide == null) throw new RuntimeException("No slideId found for image!");
            String experimentId = slide != null? slide.getExperimentId() : null;
            
            // get the poolSlide record if it exists
            Dao<PoolSlide> poolSlideDao = this.workflowRunner.getWorkflowDb().table(PoolSlide.class);
            PoolSlide poolSlide = null;
            if (conf.containsKey("loadPoolSlideId")) {
                poolSlide = poolSlideDao.selectOneOrDie(where("id", Integer.parseInt(conf.get("loadPoolSlideId").getValue())));
            }

            // set the acquisition name
            // Set rootDir and acqName
            String rootDir = workflowRunner.getWorkflowDir().getPath();
            String acqName = String.format("acquisition_%s_%s%s%s", 
                    new SimpleDateFormat("yyyyMMddHHmmss").format(startAcquisition), 
                    this.workflowModule.getName(), 
                    poolSlide != null? String.format("_C%dS%02d", poolSlide.getCartridgePosition(), poolSlide.getSlidePosition()) : "",
                    slide != null? String.format("_%s", slide.getName()) : "",
                    experimentId != null? String.format("_%s", experimentId.replaceAll("[\\/ :]+","_")) : "");

            CMMCore core = this.script.getCMMCore();
            logger.fine(String.format("This task is the acquisition task")); 
            logger.info(String.format("Using rootDir: %s", rootDir)); 
            logger.info(String.format("Requesting to use acqName: %s", acqName)); 
            
            // Move stage to starting position and take some dummy pictures to adjust the camera
            if (conf.containsKey("dummyImageCount") && 
                posList.getNumberOfPositions() > 0) 
            {
                Integer dummyImageCount = Integer.parseInt(conf.get("dummyImageCount").getValue());
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
            List<TaskDispatch> tds = new ArrayList<>();
            for (Task t : taskDao.select(where("moduleId", this.workflowModule.getId()))) {
                TaskConfig tc = this.workflowRunner.getTaskConfig().selectOne(
                        where("id", t.getId()).
                        and("key", "slideId").
                        and("value", slideId));
                TaskConfig ldtr = this.workflowRunner.getTaskConfig().selectOne(
                        where("id", t.getId()).
                        and("key", "loadDynamicTaskRecords").
                        and("value", "yes"));
                if (tc != null && ldtr == null) {
                    tds.addAll(taskDispatchDao.select(where("taskId", t.getId())));
                }
            }
            Map<String,Task> tasks = new LinkedHashMap<String,Task>();
            if (!tds.isEmpty()) {
                Collections.sort(tds, new Comparator<TaskDispatch>() {
                    @Override public int compare(TaskDispatch a, TaskDispatch b) {
                        return a.getTaskId() - b.getTaskId();
                    }});
                for (TaskDispatch t : tds) {
                	Task tt = taskDao.selectOneOrDie(where("id", t.getTaskId()));
                	TaskConfig imageLabelConf2 = taskConfigDao.selectOneOrDie(
                			where("id", tt.getId()).
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
                			where("id", tt.getId()).
                			and("key", "imageLabel"));
                    tasks.put(imageLabelConf2.getValue(), tt);
            	}
            }
            
            // close all open acquisition windows
            MMStudio.getInstance().getDisplayManager().closeAllDisplayWindows(false);

            // set the initial Z Position
            if (conf.containsKey("initialZPos")) {
                Double initialZPos = Double.parseDouble(conf.get("initialZPos").getValue());
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
                //EventManager.register(this);
                
                this.workflowRunner.getTaskConfig().insertOrUpdate(
                        new TaskConfig(task.getId(),
                                "startAcquisition", 
                                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(startAcquisition)), 
                        "id", "key");
                
                logger.info(String.format("Now running the image acquisition sequence, please wait..."));
                Datastore datastore = acqControlDlg.runAcquisition(acqName, rootDir);
                if (datastore == null) {
                    throw new RuntimeException("acqControlDlg.runAcquisition returned null datastore");
                }

                // get the prefix name and log it
                JSONObject summaryMetadata = this.engine.getSummaryMetadata();
                String prefix;
                try { prefix = summaryMetadata.getString("Prefix"); } 
                catch (JSONException e) { throw new RuntimeException(e); }
                logger.info(String.format("Acquisition was saved to root directory %s, prefix %s", 
                        Util.escape(rootDir), Util.escape(prefix)));
                
                // Write the acquisition record
                Acquisition acquisition = new Acquisition(datastore.getName(), prefix, rootDir);
                acqDao.insertOrUpdate(acquisition,"prefix","directory");
                acqDao.reload(acquisition);
                logger.info(String.format("Using acquisition record: %s", acquisition));

                // add an image cache listener to eagerly kick off downstream processing after each image
                // is received.
                Set<String> imageLabels = new TreeSet<>();
                acquisition.getDatastore().registerForEvents(new Object() {
                	@Subscribe void newImage(DefaultNewImageEvent event) {
                		org.micromanager.data.Image mmimage = event.getImage();

                        // Get the task record that should be eagerly dispatched.
                		String label = mmimage.toString();
                        Task dispatchTask = tasks.get(label);
                        if (dispatchTask == null) throw new RuntimeException(String.format(
                                "No SlideImager task was created for image with label %s!", label));
                        
                        try {
                        	String positionName = Integer.toString(mmimage.getCoords().getStagePosition());
                            
                            imageLabels.add(label);
                            logger.info(String.format("Acquired image: %s [%d/%d images]", 
                                    positionName != null?
                                        String.format("%s (%s)", positionName, label) :
                                        label,
                                    imageLabels.size(),
                                    totalImages));

                            int[] indices = Image.getIndices(label);
                            if (indices == null || indices.length < 4) throw new RuntimeException(String.format(
                                    "Bad image label from MDUtils.getIndices(): %s", label));
                            // Don't eagerly dispatch the acquisition thread. This thread must not be set to success
                            // until the acquisition has finished, so the downstream processing for this task 
                            // must wait until the acquisition has finished.
                            if (!(indices[0] == 0 && indices[1] == 0 && indices[2] == 0 && indices[3] == 0)) {
                                // Make sure the position name of this image's metadata matches what we expected
                                // from the Task configuration.
                                TaskConfig positionNameConf = taskConfigDao.selectOneOrDie(
                                        where("id", dispatchTask.getId()).and("key", "positionName"));
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
                                        Integer.toString(image.getId()));
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
                            StringWriter sw = new StringWriter();
                            e.printStackTrace(new PrintWriter(sw));
                            if (logger != null) logger.severe(String.format("Error working on task %s: %s", 
                                    task.toString(), sw.toString()));
                            throw new RuntimeException(e);
                        }
                    }
                });

                // wait until the current acquisition finishes
                while (this.engine.isAcquisitionRunning()) {
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
                datastore = acquisition.getDatastore();
                if (datastore == null) throw new RuntimeException("MMAcquisition object was not initialized; datastore is null!");
                
                // Wait for the image cache to finish...
                while (!datastore.isFrozen()) {
                    try { 
                        logger.info("Waiting for the ImageCache to finish...");
                        Thread.sleep(1000); 
                    } 
                    catch (InterruptedException e) { 
                        logger.warning("Thread was interrupted while waiting for the Image Cache to finish");
                        return Status.ERROR;
                    }
                }
                if (!datastore.isFrozen()) throw new RuntimeException("ImageCache is not finished!");

                Date endAcquisition = new Date();
                this.workflowRunner.getTaskConfig().insertOrUpdate(
                        new TaskConfig(task.getId(),
                                "endAcquisition", 
                                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(endAcquisition)), 
                        "id", "key");

                this.workflowRunner.getTaskConfig().insertOrUpdate(
                        new TaskConfig(task.getId(),
                                "acquisitionDuration", 
                                Long.toString(endAcquisition.getTime() - startAcquisition.getTime())), 
                        "id", "key");
                
                // get the autofocus duration from the autofocus object
                AutofocusManager mgr = this.script.getAutofocusManager();
                AutofocusPlugin autofocus = mgr.getAutofocusMethod();
                if (new HashSet<String>(Arrays.asList(autofocus.getPropertyNames())).contains("autofocusDuration")) {
                    try {
                        this.workflowRunner.getTaskConfig().insertOrUpdate(
                                new TaskConfig(task.getId(),
                                        "autofocusDuration", 
                                        autofocus.getPropertyValue("autofocusDuration")),
                                "id", "key");
                    } 
                    catch (Exception e) { throw new RuntimeException(e); }
                }
                // reset the autofocus settings back to defaults
                autofocus.applySettings();

                // Get the set of taggedImage labels from the acquisition
                for (Coords coords: datastore.getUnorderedImageCoords()) {
                    imageLabels.add(Image.generateLabel(coords));
                }
                logger.info(String.format("Found %d mmimages: %s", imageLabels.size(), imageLabels.toString()));
                
                // Make sure the set of acquisition image labels matches what we expected.
                if (!imageLabels.equals(tasks.keySet())) {
                    throw new RuntimeException(String.format(
                        "Error: Unexpected image set from acquisition! acquisition image count is %d, expected %d!%n"+
                        "Verbose summary: %s%nFrom acquisition: %s%nFrom task config: %s",
                        imageLabels.size(), tasks.size(), 
                        totalImages, 
                        imageLabels, tasks.keySet()));
                }
                
                // Add any missing Image record for e.g. the acquisition task. This is also important because
                // the ImageListener above is added *after* the acquisition is started, so there's a chance
                // we may have missed the first few imageReceived events. This race condition is currently 
                // unavoidable due to the way that MMAcquisition is implemented. Any remaining SlideImager tasks
                // that weren't eagerly dispatched will be dispatched normally by the workflowRunner after this 
                // task completes.
                for (String label : imageLabels) {
                    // make sure none of the taggedImage.pix values are null
                    int[] idx = Image.getIndices(label);
                    if (idx == null || idx.length < 4) throw new RuntimeException(String.format(
                            "Bad image label from MDUtils.getIndices(): %s", label));

                    Task t = tasks.get(label);
                    if (t == null) throw new RuntimeException(String.format(
                            "Could not get task record for image with label %s!", label));

                    // Insert/Update image DB record
                    Image image = new Image(slideId, acquisition, idx[0], idx[1], idx[2], idx[3]);
                    imageDao.insertOrUpdate(image,"acquisitionId","channel","slice","frame","position");
                    logger.fine(String.format("Inserted image: %s", image));
                    imageDao.reload(image, "acquisitionId","channel","slice","frame","position");
                    
                    // Store the Image ID as a Task Config variable
                    TaskConfig imageId = new TaskConfig(
                            t.getId(),
                            "imageId",
                            Integer.toString(image.getId()));
                    taskConfigDao.insertOrUpdate(imageId,"id","key");
                    conf.put(imageId.getKey(), imageId);
                    logger.fine(String.format("Inserted/Updated imageId config: %s", imageId));
                }
            }
            finally {
                //EventManager.unregister(this);
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
                    slideImagerDialog.dummyImageCount.setValue(Integer.parseInt(dummyImageCount.getValue()));
                }

                if (conf.containsKey("pixelSize")) {
                    slideImagerDialog.pixelSize.setValue(Double.parseDouble(conf.get("pixelSize").getValue()));
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
                    slideImagerDialog.initialZPos.setValue(Double.parseDouble(conf.get("initialZPos").getValue()));
                }
                else {
                    slideImagerDialog.setInitZPosNo.setSelected(true);
                    slideImagerDialog.initialZPos.setValue(0.0);
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
        Dao<Slide> slideDao = workflowRunner.getWorkflowDb().table(Slide.class);
        Dao<ModuleConfig> moduleConfig = workflowRunner.getModuleConfig();
        Dao<TaskConfig> taskConfigDao = workflowRunner.getTaskConfig();

        // Load all the module configuration into a HashMap
        Map<String,Config> moduleConf = new HashMap<String,Config>();
        for (ModuleConfig c : moduleConfig.select(where("id",this.workflowModule.getId()))) {
            moduleConf.put(c.getKey(), c);
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
                for (TaskConfig c : taskConfigDao.select(where("id",parentTask.getId()))) {
                    parentTaskConf.put(c.getKey(), c);
                    workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Using task config: %s", 
                            this.workflowModule.getName(), c));
                }
        	}

        	// Get the associated slide.
        	Slide slide;
            if (parentTaskConf.containsKey("slideId")) {
            	slide = slideDao.selectOneOrDie(where("id",Integer.parseInt(parentTaskConf.get("slideId").getValue())));
            	workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Inherited slideId %s", this.workflowModule.getName(), parentTaskConf.get("slideId")));
            }
            // if posListModule is set, use the most recent slide
            else if (moduleConf.containsKey("posListModule")) {
                List<Slide> slides = slideDao.select();
                Collections.sort(slides, (a,b)->b.getId()-a.getId());
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
            config.put("slideId", new Config(this.workflowModule.getId(), "slideId", Integer.toString(slide.getId())));

            // Load the position list and set the acquisition settings.
            // If the position list is not yet loaded, then it will be determined at runtime.
            // We will add more task records when run() is called, since the position
            // list isn't ready yet.
            PositionList positionList = loadPositionList(config, workflowRunner.getLogger());
            if (positionList == null) {
                Task task = new Task(this.workflowModule.getId(), Status.NEW);
                workflowRunner.getTaskStatus().insert(task);
                
                TaskConfig loadDynamicTaskRecordsConf = new TaskConfig(
                        task.getId(),
                        "loadDynamicTaskRecords", 
                        "yes");
                taskConfigDao.insert(loadDynamicTaskRecordsConf);

                TaskConfig slideIdConf = new TaskConfig(
                        task.getId(),
                        "slideId", 
                        Integer.toString(slide.getId()));
                taskConfigDao.insert(slideIdConf);
                if (parentTask != null) {
                    TaskDispatch dispatch = new TaskDispatch(task.getId(), parentTask.getId());
                    workflowRunner.getTaskDispatch().insert(dispatch);
                }
                continue;
            }
            
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
            
            Config dispatchUUIDConf = config.get("dispatchUUID");
            String dispatchUUID = dispatchUUIDConf != null? dispatchUUIDConf.getValue() : null;
            
            // Create the task records
            for (int c=0; c<verboseSummary.channels; ++c) {
                for (int s=0; s<verboseSummary.slices; ++s) {
                    for (int f=0; f<verboseSummary.frames; ++f) {
                        for (int p=0; p<verboseSummary.positions; ++p) {
                            MultiStagePosition msp = positionList.getPosition(p);
                            if (msp == null) continue;

                            // Create task record
                            Task task = new Task(this.workflowModule.getId(), Status.NEW);
                            // setting the dispatch UUID is required for dynamically created
                            // tasks to be picked up by the workflow runner
                            task.setDispatchUUID(dispatchUUID);
                            workflowRunner.getTaskStatus().insert(task);
                            tasks.add(task);
                            workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Created task record: %s", 
                                    this.workflowModule.getName(), task));
                            
                            // Create taskConfig record for the image label
                            TaskConfig imageLabel = new TaskConfig(
                                    task.getId(),
                                    "imageLabel", 
                                    Image.generateLabel(c, s, f, p));
                            taskConfigDao.insert(imageLabel);
                            workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Created task config: %s", 
                                    this.workflowModule.getName(), imageLabel));

                            // Create taskConfig record for the MSP label (positionName)
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
                                String mspJson = new JSONObject(mspPosList.toPropertyMap().toJSON()).
                                        getJSONArray("POSITIONS").getJSONObject(0).toString();
                                TaskConfig mspConf = new TaskConfig(
                                        task.getId(), "MSP", mspJson);
                                taskConfigDao.insert(mspConf);
                                moduleConf.put("MSP", mspConf);
                                workflowRunner.getLogger().fine(String.format(
                                        "Inserted MultiStagePosition config: %s", mspJson));
                            } 
                            catch (JSONException e) {throw new RuntimeException(e);}

                            // Transfer MultiStagePosition property values to the task's configuration
                            for (String propName : msp.getPropertyNames()) {
                                String property = msp.getProperty(propName);
                                TaskConfig prop = new TaskConfig(
                                        task.getId(), propName, property);
                                taskConfigDao.insert(prop);
                                moduleConf.put(property, prop);
                                workflowRunner.getLogger().fine(String.format(
                                        "Inserted MultiStagePosition config: %s", prop));
                            }

                            // create taskConfig record for the slide ID
                            TaskConfig slideId = new TaskConfig(
                                    task.getId(),
                                    "slideId", 
                                    Integer.toString(slide.getId()));
                            taskConfigDao.insert(slideId);
                            workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Created task config: %s", 
                                    this.workflowModule.getName(), slideId));
                            
                            // add some task configs to the first imaging task to assist in calculating metrics
                            if (c==0 && s==0 && f==0 && p==0) {
                                TaskConfig positionsConf = new TaskConfig(
                                        task.getId(),
                                        "positions", 
                                        Integer.toString(verboseSummary.positions));
                                taskConfigDao.insert(positionsConf);
                                
                                TaskConfig roisConf = new TaskConfig(
                                        task.getId(),
                                        "ROIs", 
                                        Integer.toString(rois.size()));
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
        verboseSummary.frames = Integer.parseInt(matcher.group(1));

        pattern = Pattern.compile("^Number of positions: ([0-9]+)$", Pattern.MULTILINE);
        matcher = pattern.matcher(summary);
        if (!matcher.find()) throw new RuntimeException(String.format(
                "Could not parse positions field from summary:%n%s", summary));
        verboseSummary.positions = Integer.parseInt(matcher.group(1));

        pattern = Pattern.compile("^Number of slices: ([0-9]+)$", Pattern.MULTILINE);
        matcher = pattern.matcher(summary);
        if (!matcher.find()) throw new RuntimeException(String.format(
                "Could not parse slices field from summary:%n%s", summary));
        verboseSummary.slices = Integer.parseInt(matcher.group(1));

        pattern = Pattern.compile("^Number of channels: ([0-9]+)$", Pattern.MULTILINE);
        matcher = pattern.matcher(summary);
        if (!matcher.find()) throw new RuntimeException(String.format(
                "Could not parse channels field from summary:%n%s", summary));
        verboseSummary.channels = Integer.parseInt(matcher.group(1));

        pattern = Pattern.compile("^Total images: ([0-9]+)$", Pattern.MULTILINE);
        matcher = pattern.matcher(summary);
        if (!matcher.find()) throw new RuntimeException(String.format(
                "Could not parse total images field from summary:%n%s", summary));
        verboseSummary.totalImages = Integer.parseInt(matcher.group(1));

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
    public List<ImageLogRecord> logImages(Task task, Map<String,Config> config, Logger logger) {
        List<ImageLogRecord> imageLogRecords = new ArrayList<ImageLogRecord>();

        // Add an image logger instance to the workflow runner for this module
        imageLogRecords.add(new ImageLogRecord(task.getName(workflowRunner.getWorkflow()), task.getName(workflowRunner.getWorkflow()),
                new FutureTask<ImageLogRunner>(new Callable<ImageLogRunner>() {
            @Override public ImageLogRunner call() throws Exception {
                // Get the Image record
                Config imageIdConf = config.get("imageId");
                if (imageIdConf != null) {
                    Integer imageId = Integer.parseInt(imageIdConf.getValue());

                    logger.info(String.format("Using image ID: %d", imageId));
                    Dao<Image> imageDao = workflowRunner.getWorkflowDb().table(Image.class);
                    Image image = imageDao.selectOneOrDie(where("id",imageId));
                    logger.info(String.format("Using image: %s", image));

                    // Initialize the acquisition
                    Dao<Acquisition> acqDao = workflowRunner.getWorkflowDb().table(Acquisition.class);
                    Acquisition acquisition = acqDao.selectOneOrDie(where("id",image.getAcquisitionId()));
                    logger.info(String.format("Using acquisition: %s", acquisition));
                    Datastore datastore = acquisition.getDatastore();

                    // Get the image cache object
                    if (datastore == null) throw new RuntimeException("Acquisition was not initialized; datastore is null!");
                    // Get the tagged image from the image cache
                    org.micromanager.data.Image mmimage = image.getImage(datastore);
                    logger.info(String.format("Got mmimage from datastore: %s", mmimage));

                    ImageLogRunner imageLogRunner = new ImageLogRunner(task.getName(workflowRunner.getWorkflow()));
                    imageLogRunner.addImage(mmimage, "The image");
                    return imageLogRunner;
                }
                return null;
            }
        })));
        return imageLogRecords;
    }

    @Override
    public void runInitialize() { }
    
    public synchronized static double[] moveStage(String moduleId, CMMCore core, double x, double y, Logger logger) {
    	core.setTimeoutMs(10000);
        String xyStage = core.getXYStageDevice();

        int move_tries = 0;
        final int MOVE_MAX_TRIES = 10;
        Throwable err = new RuntimeException("Unknown exception");
        while (move_tries++ < MOVE_MAX_TRIES) {
            try {
                if (logger != null) logger.fine(String.format("%s: Moving stage to position: (%f,%f)", moduleId, x, y));
                // get the stage coordinates
                // sometimes moving the stage throws, try at least 10 times
                double[] x_stage = new double[] {0.0};
                double[] y_stage = new double[] {0.0};
                int tries = 0;
                final int MAX_TRIES = 10;
                while(tries <= MAX_TRIES) {
                    try { 
                        core.getXYPosition(xyStage, x_stage, y_stage);
                        break;
                    } 
                    catch (Throwable e) { 
                        if (tries >= MAX_TRIES) throw new RuntimeException(e);
                    }
                    Thread.sleep(500);
                    ++tries;
                }
                if (logger != null) logger.fine(String.format("%s: Current stage position: (%f,%f)", moduleId, x_stage[0], y_stage[0]));

                // set the stage coordinates
                tries = 0;
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
                    Thread.sleep(5);
                }
                
                // get the new stage coordinates
                double[] x_stage_new = new double[] {0.0};
                double[] y_stage_new = new double[] {0.0};
                tries = 0;
                while(tries <= MAX_TRIES) {
                    try { 
                        core.getXYPosition(xyStage, x_stage_new, y_stage_new);
                        break;
                    } 
                    catch (Throwable e) { 
                        if (tries >= MAX_TRIES) throw new RuntimeException(e);
                    }
                    Thread.sleep(500);
                    ++tries;
                }

                // make sure the stage moved to the correct coordinates
                if (logger != null) logger.fine(String.format("%s: New stage position: (%f,%f)", moduleId, x_stage_new[0], y_stage_new[0]));
                final double EPSILON = 1000;
                if (!(Math.abs(x_stage_new[0]-x) < EPSILON && Math.abs(y_stage_new[0]-y) < EPSILON)) {
                    throw new RuntimeException(String.format("%s: Stage moved to wrong coordinates: (%.2f,%.2f)",
                            moduleId, x_stage_new[0], y_stage_new[0]));
                }
                // return the new stage coordinates
                return new double[]{x_stage_new[0], y_stage_new[0]};
            } 
            catch (Throwable e) { 
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                if (logger != null) logger.severe(String.format("%s: Failed to move stage to position (%.2f,%.2f): %s", moduleId, x,y, sw.toString()));
                err = e;
            }
            try { Thread.sleep(1000); } 
            catch (InterruptedException e) { /* do nothing */ }
        }
        throw new RuntimeException(err);
    }

    public Status setTaskStatusOnResume(Task task) {
        // if this is the dummy loadDynamicTaskRecords task, check if this imaging needs to be redone.
        // Check the associated ROI finder module's task statuses for this slide
    	TaskConfig loadDynamicTaskRecordsConf = this.workflowRunner.getTaskConfig().selectOne(
    	        where("id", task.getId()).
    	        and("key", "loadDynamicTaskRecords").
    	        and("value","yes"));
    	if (loadDynamicTaskRecordsConf != null) {
    	    ModuleConfig posListModuleConf = this.workflowRunner.getModuleConfig().selectOne(
    	            where("id", this.workflowModule.getId()).
    	            and("key", "posListModule"));
    	    if (posListModuleConf != null) {
    	        WorkflowModule posListModule = this.workflowRunner.getWorkflow().selectOneOrDie(
    	                where("name", posListModuleConf.getValue()));
                TaskConfig slideIdConf = this.workflowRunner.getTaskConfig().selectOneOrDie(
                        where("id", task.getId()).
                        and("key", "slideId"));
                Integer slideId = Integer.parseInt(slideIdConf.getValue());
    	        for (Task t : this.workflowRunner.getTaskStatus().select(where("moduleId", posListModule.getId()))) {
    	            if (this.workflowRunner.getTaskConfig().selectOne(
    	                    where("id", t.getId()).
    	                    and("key", "slideId").
    	                    and("value", slideId)) != null) 
    	            {
    	                if (t.getStatus() != Status.SUCCESS) {
    	                    // remove old tasks
    	                    int deletedTasks=0;
    	                    for (Task t2 : this.workflowRunner.getTaskStatus().select(
    	                            where("moduleId", this.workflowModule.getId()))) 
    	                    {
    	                        TaskConfig tc = this.workflowRunner.getTaskConfig().selectOne(
    	                                where("id",t2.getId()).
    	                                and("key","slideId").
    	                                and("value",slideId));
    	                        if (tc != null && t2.getId() != task.getId()) {
    	                            int dt = this.workflowRunner.deleteTaskRecords(t2);
    	                            if (dt>0) ++deletedTasks;
    	                        }
    	                    }
    	                    if (deletedTasks > 0) {
                                this.workflowRunner.getLogger().info(String.format(
                                        "loadDynamicTaskRecords: slide %s: deleted %s old task records", slideId, deletedTasks));
    	                    }
    	                    return Status.NEW;
    	                }
    	            }
    	        }
    	    }
    	    return null;
    	}

    	// Handle normal tasks
    	TaskConfig imageLabelConf = this.workflowRunner.getTaskConfig().selectOne(
    	        where("id", task.getId()).
    	        and("key", "imageLabel"));
    	if (imageLabelConf == null) throw new RuntimeException(String.format(
    	        "Could not find imageLabel conf for task %s!", task));
    	String imageLabel = imageLabelConf.getValue();
        int[] indices = Image.getIndices(imageLabel);
        if (indices == null || indices.length < 4) throw new RuntimeException(String.format(
                "Invalid indices parsed from imageLabel %s", imageLabel));
        
        if (indices[0] == 0 && indices[1] == 0 && indices[2] == 0 && indices[3] == 0) {
            // get this slide ID
            TaskConfig slideIdConf = this.workflowRunner.getTaskConfig().selectOneOrDie(
                    where("id", task.getId()).
                    and("key", "slideId"));
            Integer slideId = Integer.parseInt(slideIdConf.getValue());

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
                if (this.workflowRunner.getTaskConfig().selectOne(
                        where("id", t.getId()).
                        and("key", "loadDynamicTaskRecords").
                        and("value", "yes")) == null) 
                {
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
        return null;
    }
}
