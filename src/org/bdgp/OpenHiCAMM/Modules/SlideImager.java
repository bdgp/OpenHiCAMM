package org.bdgp.OpenHiCAMM.Modules;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.bdgp.OpenHiCAMM.DB.Slide;
import org.bdgp.OpenHiCAMM.DB.SlidePos;
import org.bdgp.OpenHiCAMM.DB.SlidePosList;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.Task.Status;
import org.bdgp.OpenHiCAMM.DB.TaskConfig;
import org.bdgp.OpenHiCAMM.DB.TaskDispatch;
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

import static org.bdgp.OpenHiCAMM.Util.where;

public class SlideImager implements Module, ImageLogger {
	private static final long DUMMY_SLEEP = 500;

	WorkflowRunner workflowRunner;
    String moduleId;
    AcqControlDlg acqControlDlg;
    ScriptInterface script;
    AcquisitionWrapperEngine engine;

    @Override
    public void initialize(WorkflowRunner workflowRunner, String moduleId) {
        this.workflowRunner = workflowRunner;
        this.moduleId = moduleId;
        OpenHiCAMM openhicamm = workflowRunner.getOpenHiCAMM();
        this.script = openhicamm.getApp();

        Preferences prefs = Preferences.userNodeForPackage(this.script.getClass());
        MMOptions options = new MMOptions();
        options.loadSettings();
        if (this.script != null) {
            this.engine = MMStudio.getInstance().getAcquisitionEngine();
            this.acqControlDlg = new AcqControlDlg(this.engine, prefs, this.script, options);
        }
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
        if (conf.containsKey("posListModuleId")) {
            // get a sorted list of all the SlidePosList records for the posListModuleId module
            Config posListModuleId = conf.get("posListModuleId");
            Dao<SlidePosList> posListDao = workflowRunner.getInstanceDb().table(SlidePosList.class);
            Integer slideId = new Integer(conf.get("slideId").getValue());
            List<SlidePosList> posLists = posListDao.select(
                    where("slideId", slideId).
                    and("moduleId", posListModuleId.getValue()));
            Collections.sort(posLists, new Comparator<SlidePosList>() {
                @Override public int compare(SlidePosList a, SlidePosList b) {
                    return a.getId()-b.getId();
                }});
            if (posLists.isEmpty()) {
                throw new RuntimeException("Position list from module \""+posListModuleId.getValue()+"\" not found in database");
            }
            // Merge the list of PositionLists into a single positionList
            positionList = posLists.get(0).getPositionList();
            for (int i=1; i<posLists.size(); ++i) {
                SlidePosList spl = posLists.get(i);
                PositionList pl = spl.getPositionList();
                for (int j=0; j<pl.getNumberOfPositions(); ++j) {
                    MultiStagePosition msp = pl.getPosition(j);
                    positionList.addPosition(msp);
                }
            }
            // log the position list to the console
            try {
				logger.info(String.format("Read position list from module %s:%n%s", 
						conf.get("posListModuleId"), positionList.serialize()));
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
            logger.info(String.format("Loading position list from file: %s", Util.escape(posList.getValue())));
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
            logger.info(String.format("Loading acquisition settings from file: %s", Util.escape(acqSettings.getValue())));
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
    	logger.info(String.format("Running task: %s", task));
    	for (Config c : conf.values()) {
    		logger.info(String.format("Using configuration: %s", c));
    	}
    	Config imageNumberConf = conf.get("imageNumber");
    	if (imageNumberConf == null) throw new RuntimeException(String.format(
    	        "Could not find imageNumber conf for task %s!", task));
    	Integer imageNumber = new Integer(imageNumberConf.getValue());
        logger.info(String.format("Using imageNumber: %s", imageNumber)); 

        // If this is the first position, start the acquisition engine
        if (imageNumber == 0) {
            // Make sure the acquisition control dialog was initialized
            if (this.acqControlDlg == null) {
                throw new RuntimeException("acqControlDlg is not initialized!");
            }
            // load the position list and acquistion settings
            PositionList posList = this.loadPositionList(conf, logger);

            // Get Dao objects ready for use
            Dao<TaskConfig> taskConfigDao = workflowRunner.getInstanceDb().table(TaskConfig.class);
            Dao<Acquisition> acqDao = workflowRunner.getInstanceDb().table(Acquisition.class);

            // Set rootDir and acqName
            String rootDir = new File(
                    workflowRunner.getWorkflowDir(), 
                    workflowRunner.getInstance().getStorageLocation()).getPath();
            String acqName = "images";

            CMMCore core = this.script.getMMCore();
            logger.info(String.format("This task is the acquisition task")); 
            logger.info(String.format("Using rootDir: %s", rootDir)); 
            logger.info(String.format("Requesting to use acqName: %s", acqName)); 
            
            // Move stage to starting position and take some dummy pictures to adjust the camera
            if (conf.containsKey("dummyImageCount") && 
                posList.getNumberOfPositions() > 0) 
            {
                Integer dummyImageCount = new Integer(conf.get("dummyImageCount").getValue());
            	logger.info("Moving stage to starting position");
                MultiStagePosition pos = posList.getPosition(0);
                String xyStage = core.getXYStageDevice();
                try {
					core.setXYPosition(xyStage, pos.getX(), pos.getY());
                    // wait for the stage to finish moving
                    while (core.deviceBusy(xyStage)) {}
				} 
                catch (Exception e) {throw new RuntimeException(e);}
                
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
            
            // Set the autofocus device
            try { 
                this.script.getAutofocusManager().selectDevice("BDGP"); 
                Autofocus autofocus = this.script.getAutofocus();

                // Only pass these settings to the autofocus object:
                Set<String> autoFocusKeys = new HashSet<String>();
                autoFocusKeys.add("minAutoFocus");
                autoFocusKeys.add("maxAutoFocus");

                // Set the autofocus property values
                for (Map.Entry<String, Config> entry : conf.entrySet()) {
                    if (autoFocusKeys.contains(entry.getKey())) {
                        autofocus.setPropertyValue(entry.getKey(), entry.getValue().getValue());
                    }
                }
                
                // now apply the settings
                autofocus.applySettings();
            } 
            catch (MMException e) {throw new RuntimeException(e);}

            // Start the acquisition engine. This runs asynchronously.
            try {
                EventManager.register(this);
                logger.info(String.format("Now running the image acquisition sequence, please wait..."));
                String returnAcqName = acqControlDlg.runAcquisition(acqName, rootDir);
                if (returnAcqName == null) {
                    throw new RuntimeException("acqControlDlg.runAcquisition returned null acquisition name");
                }
                final Integer[] imageCount = new Integer[]{0};
                final int totalImages = getTotalImages();
                this.engine.getImageCache().addImageCacheListener(new ImageCacheListener() {
                    @Override public void imageReceived(TaggedImage taggedImage) {
                        ++imageCount[0];
                        logger.info(String.format("Acquired image: %s (%d/%d images)", 
                                MDUtils.getLabel(taggedImage.tags),
                                imageCount[0],
                                totalImages));
                    }
                    @Override public void imagingFinished(String path) { 
                        logger.info(String.format("Finished image acquisition."));
                    }
                });

                // wait until the current acquisition finishes
                while (acqControlDlg.isAcquisitionRunning()) {
                     try { Thread.sleep(1000); } 
                     catch (InterruptedException e) {throw new RuntimeException(e);}
                }
            }
            finally {
                EventManager.unregister(this);
            }

            // get the prefix name and log it
            JSONObject summaryMetadata = this.engine.getSummaryMetadata();
            String prefix;
            try { prefix = summaryMetadata.getString("Prefix"); } 
            catch (JSONException e) { throw new RuntimeException(e); }
            logger.info(String.format("Acquisition was saved to root directory %s, prefix %s", 
                    Util.escape(rootDir), Util.escape(prefix)));
            
            // Write the acquisition record
            Acquisition acquisition = new Acquisition(prefix, rootDir);
            acqDao.insertOrUpdate(acquisition,"name","directory");
            acqDao.reload(acquisition);
            logger.info(String.format("Using acquisition record: %s", acquisition));

            // Get the ImageCache object for this acquisition
            MMAcquisition mmacquisition = acquisition.getAcquisition();
            ImageCache imageCache = mmacquisition.getImageCache();
            if (imageCache == null) throw new RuntimeException("MMAcquisition object was not initialized; imageCache is null!");
            if (!imageCache.isFinished()) throw new RuntimeException("ImageCache is not finished!");

            // Get the tagged image that corresponds to this imageNumber from the image cache
            List<String> taggedImages = new ArrayList<String>(imageCache.imageKeys());
            logger.info(String.format("Found %d taggedImages: %s", taggedImages.size(), taggedImages.toString()));
            
            // get all sibling tasks
            Dao<Task> taskDao = this.workflowRunner.getTaskStatus();
            Dao<TaskDispatch> taskDispatchDao = this.workflowRunner.getTaskDispatch();
            TaskDispatch td = taskDispatchDao.selectOne(where("taskId", task.getId()));
            List<TaskDispatch> tds = td != null? 
                    taskDispatchDao.select(where("parentTaskId", td.getParentTaskId())) :
                    null;
            Map<Integer,Task> tasks = new HashMap<Integer,Task>();
            if (tds != null) {
                for (TaskDispatch t : tds) {
                	Task tt = taskDao.selectOneOrDie(where("id", t.getTaskId()));
                	TaskConfig imageNumConf = taskConfigDao.selectOneOrDie(
                			where("id", new Integer(tt.getId()).toString()).
                			and("key", "imageNumber"));
                    tasks.put(new Integer(imageNumConf.getValue()), tt);
                }
            }
            else {
            	for (Task tt : taskDao.select(where("moduleId", this.moduleId))) {
                	TaskConfig imageNumConf = taskConfigDao.selectOneOrDie(
                			where("id", new Integer(tt.getId()).toString()).
                			and("key", "imageNumber"));
                    tasks.put(new Integer(imageNumConf.getValue()), tt);
            	}
            }

            // get the slide ID and slide Position ID from the config
            if (!conf.containsKey("slideId")) throw new RuntimeException("No slideId found for image!");
            Integer slideId = new Integer(conf.get("slideId").getValue());
            logger.info(String.format("Using slideId: %d", slideId));

            // For image acquisition, only the first task actually does any work, the
            // other tasks represent each image, so that the downstream processing tasks
            // can dispatch correctly. So we can update the status and configs for the 
            // sibling tasks in the first task.
            for (int imageNum=0; imageNum<taggedImages.size(); ++imageNum) {
            	Task t = tasks.get(imageNum);
            	if (t == null) throw new RuntimeException(String.format(
            			"Could not get task record for image number %d!", imageNum));

                String[] taggedImageKeys = taggedImages.get(imageNum).split("_");

                // Create image DB record
                Dao<Image> imageDao = workflowRunner.getInstanceDb().table(Image.class);
                Image image = new Image(slideId, acquisition, 
                        new Integer(taggedImageKeys[0]),
                        new Integer(taggedImageKeys[1]),
                        new Integer(taggedImageKeys[2]),
                        new Integer(taggedImageKeys[3]));
                imageDao.insert(image);
                logger.info(String.format("Inserted image: %s", image));

                // Store the Image ID as a Task Config variable
                TaskConfig imageId = new TaskConfig(
                        new Integer(t.getId()).toString(),
                        "imageId",
                        new Integer(image.getId()).toString());
                taskConfigDao.insertOrUpdate(imageId,"id","key");
                conf.put("imageId", imageId);
                logger.info(String.format("Inserted/Updated imageId config: %s", imageId));
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
                    configs.add(new Config(moduleId, 
                            "acqSettingsFile", 
                            slideImagerDialog.acqSettingsText.getText()));
                }
                if (slideImagerDialog.posListText.getText().length()>0) {
                    configs.add(new Config(moduleId, 
                            "posListFile", 
                            slideImagerDialog.posListText.getText()));
                }
                if (slideImagerDialog.moduleId.getSelectedIndex()>0) {
                    configs.add(new Config(moduleId, 
                            "posListModuleId", 
                            slideImagerDialog.moduleId.getSelectedItem().toString()));
                }
                
                if (slideImagerDialog.dummyImageCount.getValue() != null) {
                    configs.add(new Config(moduleId, 
                            "dummyImageCount", 
                            slideImagerDialog.dummyImageCount.getValue().toString()));
                }

                if (((Double)slideImagerDialog.minAutoFocus.getValue()).doubleValue() != 0.0) {
                    configs.add(new Config(moduleId, "minAutoFocus", slideImagerDialog.minAutoFocus.getValue().toString()));
                }
                if (((Double)slideImagerDialog.maxAutoFocus.getValue()).doubleValue() != 0.0) {
                    configs.add(new Config(moduleId, "maxAutoFocus", slideImagerDialog.maxAutoFocus.getValue().toString()));
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
                if (conf.containsKey("posListModuleId")) {
                    Config posListModuleId = conf.get("posListModuleId");
                    slideImagerDialog.moduleId.setSelectedItem(posListModuleId.getValue());
                }
                
                if (conf.containsKey("dummyImageCount")) {
                    Config dummyImageCount = conf.get("dummyImageCount");
                    slideImagerDialog.dummyImageCount.setValue(new Integer(dummyImageCount.getValue()));
                }

                if (conf.containsKey("minAutoFocus")) {
                    slideImagerDialog.minAutoFocus.setValue(new Double(conf.get("minAutoFocus").getValue()));
                }
                if (conf.containsKey("maxAutoFocus")) {
                    slideImagerDialog.maxAutoFocus.setValue(new Double(conf.get("maxAutoFocus").getValue()));
                }

                return slideImagerDialog;
            }
            @Override
            public ValidationError[] validate() {
                List<ValidationError> errors = new ArrayList<ValidationError>();
                File acqSettingsFile = new File(slideImagerDialog.acqSettingsText.getText());
                if (!acqSettingsFile.exists()) {
                    errors.add(new ValidationError(moduleId, "Acquisition settings file "+acqSettingsFile.toString()+" not found."));
                }
                if (slideImagerDialog.posListText.getText().length()>0) {
                    File posListFile = new File(slideImagerDialog.posListText.getText());
                    if (!posListFile.exists()) {
                        errors.add(new ValidationError(moduleId, "Position list file "+posListFile.toString()+" not found."));
                    }
                }
                if (!((slideImagerDialog.posListText.getText().length()>0? 1: 0)
                        + (slideImagerDialog.moduleId.getSelectedIndex()>0? 1: 0) == 1))
                {
                    errors.add(new ValidationError(moduleId, 
                            "You must enter one of either a position list file, or a position list name."));
                }

                return errors.toArray(new ValidationError[0]);
            }
        };
    }
    
    
    @Override
    public List<Task> createTaskRecords(List<Task> parentTasks) {
        Dao<Slide> slideDao = workflowRunner.getInstanceDb().table(Slide.class);
        Dao<SlidePosList> posListDao = workflowRunner.getInstanceDb().table(SlidePosList.class);
        Dao<SlidePos> posDao = workflowRunner.getInstanceDb().table(SlidePos.class);
        Dao<ModuleConfig> config = workflowRunner.getInstanceDb().table(ModuleConfig.class);
        Dao<TaskConfig> taskConfigDao = workflowRunner.getInstanceDb().table(TaskConfig.class);

        // Load all the module configuration into a HashMap
        Map<String,Config> conf = new HashMap<String,Config>();
        for (ModuleConfig c : config.select(where("id",this.moduleId))) {
            conf.put(c.getKey(), c);
            workflowRunner.getLogger().info(String.format("%s: createTaskRecords: Using module config: %s", 
            		this.moduleId, c));
        }
        
        // Create task records and connect to parent tasks
        // If no parent tasks were defined, then just create a single task instance.
        List<Task> tasks = new ArrayList<Task>();
        for (Task parentTask : parentTasks.size()>0? parentTasks.toArray(new Task[0]) : new Task[] {null}) 
        {
            workflowRunner.getLogger().info(String.format("%s: createTaskRecords: Connecting parent task %s", 
            		this.moduleId, Util.escape(parentTask)));

        	// get the parent task configuration
        	Map<String,TaskConfig> parentTaskConf = new HashMap<String,TaskConfig>();
        	if (parentTask != null) {
                for (TaskConfig c : taskConfigDao.select(where("id",new Integer(parentTask.getId()).toString()))) {
                    parentTaskConf.put(c.getKey(), c);
                    workflowRunner.getLogger().info(String.format("%s: createTaskRecords: Using task config: %s", 
                            this.moduleId, c));
                }
        	}
        	// Make sure this isn't a sentinel (dummy) task. The final slide loader task is only 
        	// used for putting the last slide back, and should have no child tasks.
        	if (parentTask != null && 
                (!parentTaskConf.containsKey("poolSlideId") || parentTaskConf.get("poolSlideId") == null)) 
        	{
                workflowRunner.getLogger().info(String.format(
                		"%s: createTaskRecords: Task %d is a sentinel, not attaching child tasks", 
                        this.moduleId, parentTask.getId()));
                continue;
        	}
        	// Get the associated slide.
        	Slide slide;
            if (parentTaskConf.containsKey("slideId")) {
            	slide = slideDao.selectOneOrDie(where("id",new Integer(parentTaskConf.get("slideId").getValue())));
            	workflowRunner.getLogger().info(String.format("%s: createTaskRecords: Inherited slideId %s", this.moduleId, parentTaskConf.get("slideId")));
            }
            // if posListModuleId is set, use the most recent slide
            else if (conf.containsKey("posListModuleId")) {
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
            	slide = new Slide(moduleId);
            	slideDao.insertOrUpdate(slide,"experimentId");
            	slideDao.reload(slide, "experimentId");
            	workflowRunner.getLogger().info(String.format("%s: createTaskRecords: Created new slide: %s", this.moduleId, slide.toString()));
            }
            conf.put("slideId", new Config(this.moduleId, "slideId", new Integer(slide.getId()).toString()));

            // get the position list for the slide
            SlidePosList posList = null;
            // first try to load a position list from the DB
            if (conf.containsKey("posListModuleId")) {
                // get a sorted list of all the SlidePosList records for the posListModuleId module
                Config posListModuleId = conf.get("posListModuleId");
                List<SlidePosList> posLists = posListDao.select(
                        where("slideId", slide.getId()).
                        and("moduleId", posListModuleId.getValue()));
                Collections.sort(posLists, new Comparator<SlidePosList>() {
                    @Override public int compare(SlidePosList a, SlidePosList b) {
                        return a.getId()-b.getId();
                    }});
                if (posLists.isEmpty()) {
                    throw new RuntimeException("Position list from module \""+posListModuleId.getValue()+"\" not found in database");
                }

                // Merge the list of PositionLists into a single positionList
                PositionList positionList = posLists.get(0).getPositionList();
                for (int i=1; i<posLists.size(); ++i) {
                    SlidePosList spl = posLists.get(i);
                    PositionList pl = spl.getPositionList();
                    for (int j=0; j<pl.getNumberOfPositions(); ++j) {
                        MultiStagePosition msp = pl.getPosition(j);
                        positionList.addPosition(msp);
                    }
                }
                posList = new SlidePosList(posLists.get(0).getModuleId(), posLists.get(0).getSlideId(), null, positionList);
                workflowRunner.getLogger().info(String.format("%s: createTaskRecords: Read position list from module %s", 
            			this.moduleId, conf.get("posListModuleId")));
            }
            // otherwise, load a position list from a file
            else if (conf.containsKey("posListFile")) {
                Config posListConf = conf.get("posListFile");

                File posListFile = new File(posListConf.getValue());
                if (!posListFile.exists()) {
                    throw new RuntimeException("Cannot find position list file "+posListFile.getPath());
                }
                try { 
                    PositionList positionList = new PositionList();
                    positionList.load(posListFile.getPath()); 
                    posList = new SlidePosList(this.moduleId, slide.getId(), null, positionList);
                } 
                catch (MMException e) {throw new RuntimeException(e);}
                
            	workflowRunner.getLogger().info(String.format("%s: createTaskRecords: Read position list from file %s", 
            			this.moduleId, Util.escape(posListConf.getValue())));

                // store the loaded position list in the DB
                posListDao.insertOrUpdate(posList,"moduleId","slideId");
                posListDao.reload(posList,"moduleId","slideId");
            	workflowRunner.getLogger().info(String.format("%s: createTaskRecords: Created/Updated posList: %s", 
            			this.moduleId, posList));
                // delete any old record first
                posDao.delete(where("slidePosListId", posList.getId()));
                // then create new records
                MultiStagePosition[] msps = posList.getPositionList().getPositions();
                for (int i=0; i<msps.length; ++i) {
                	SlidePos slidePos = new SlidePos(posList.getId(), i);
                    posDao.insert(slidePos);
                    workflowRunner.getLogger().info(String.format("%s: createTaskRecords: Inserted slidePos at position %d: %s", 
                            this.moduleId, i, slidePos));
                }
            }

            // Load the position list and set the acquisition settings
            loadPositionList(conf, workflowRunner.getLogger());
            // get the total images
            Integer totalImages = getTotalImages();
            workflowRunner.getLogger().info(String.format("%s: getTotalImages: Will create %d images", 
                    this.moduleId, totalImages));

            // Create the task records
            for (int i=0; i<totalImages; ++i) {
                // Create task record
                Task task = new Task(moduleId, Status.NEW);
                workflowRunner.getTaskStatus().insert(task);
                tasks.add(task);
                workflowRunner.getLogger().info(String.format("%s: createTaskRecords: Created task record: %s", 
                        this.moduleId, task));
                
                // Create taskConfig record to link task to position index in Position List
                TaskConfig imageNumber = new TaskConfig(
                        new Integer(task.getId()).toString(), 
                        "imageNumber", 
                        new Integer(i).toString());
                taskConfigDao.insert(imageNumber);
                workflowRunner.getLogger().info(String.format("%s: createTaskRecords: Created task config: %s", 
                        this.moduleId, imageNumber));

                // create taskConfig record for the slide ID
                TaskConfig slideId = new TaskConfig(
                        new Integer(task.getId()).toString(), 
                        "slideId", 
                        new Integer(slide.getId()).toString());
                taskConfigDao.insert(slideId);
                workflowRunner.getLogger().info(String.format("%s: createTaskRecords: Created task config: %s", 
                        this.moduleId, slideId));

                // Create task dispatch record
                if (parentTask != null) {
                    TaskDispatch dispatch = new TaskDispatch(task.getId(), parentTask.getId());
                    workflowRunner.getTaskDispatch().insert(dispatch);
                    workflowRunner.getLogger().info(String.format("%s: createTaskRecords: Created task dispatch record: %s", 
                            this.moduleId, dispatch));
                }
            }
        }
        return tasks;
    }
    
    /**
     * Get the total number of images that will be produced by the acqusition engine given 
     * the current settings.      
     * @return the total number of images
     */
    public Integer getTotalImages() {
        String summary = this.engine.getVerboseSummary();
        workflowRunner.getLogger().info(String.format("Verbose summary:%n%s%n", summary));

        Pattern pattern = Pattern.compile("^Total images: ([0-9]+)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(summary);
        if (!matcher.find()) {
            throw new RuntimeException(String.format(
                "Could not parse Total images field from summary:%n%s", summary));
        }
        return new Integer(matcher.group(1));
    }
    
    @Override
    public TaskType getTaskType() {
        return Module.TaskType.SERIAL;
    }

    @Override public void cleanup(Task task) { 
        if (this.script != null) {
            if (this.engine != null && this.engine.isAcquisitionRunning()) {
                this.engine.abortRequest();
            }
        }
    }

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
                    MMAcquisition mmacquisition = acquisition.getAcquisition();

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
    public void runIntialize() { }
}
