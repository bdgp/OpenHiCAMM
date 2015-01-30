package org.bdgp.MMSlide.Modules;

import java.awt.Component;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import mmcorej.TaggedImage;

import org.bdgp.MMSlide.Dao;
import org.bdgp.MMSlide.Logger;
import org.bdgp.MMSlide.MMSlide;
import org.bdgp.MMSlide.ValidationError;
import org.bdgp.MMSlide.WorkflowRunner;
import org.bdgp.MMSlide.DB.Acquisition;
import org.bdgp.MMSlide.DB.Config;
import org.bdgp.MMSlide.DB.Image;
import org.bdgp.MMSlide.DB.ModuleConfig;
import org.bdgp.MMSlide.DB.Slide;
import org.bdgp.MMSlide.DB.SlidePos;
import org.bdgp.MMSlide.DB.SlidePosList;
import org.bdgp.MMSlide.DB.Task;
import org.bdgp.MMSlide.DB.Task.Status;
import org.bdgp.MMSlide.DB.TaskConfig;
import org.bdgp.MMSlide.DB.TaskDispatch;
import org.bdgp.MMSlide.DB.WorkflowModule;
import org.bdgp.MMSlide.Modules.Interfaces.Configuration;
import org.bdgp.MMSlide.Modules.Interfaces.Module;
import org.micromanager.dialogs.AcqControlDlg;
import org.micromanager.MMOptions;
import org.micromanager.MMStudio;
import org.micromanager.acquisition.AcquisitionEngine;
import org.micromanager.acquisition.TaggedImageQueue;
import org.micromanager.api.DataProcessor;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.MMException;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.MDUtils;

import static org.bdgp.MMSlide.Util.where;

public class SlideImager implements Module {
    WorkflowRunner workflowRunner;
    String moduleId;
    AcqControlDlg acqControlDlg;
    ScriptInterface script;

    @Override
    public void initialize(WorkflowRunner workflowRunner, String moduleId) {
        this.workflowRunner = workflowRunner;
        this.moduleId = moduleId;
        MMSlide mmslide = workflowRunner.getMMSlide();
        this.script = mmslide.getApp();

        Preferences prefs = Preferences.userNodeForPackage(this.script.getClass());
        MMOptions options = new MMOptions();
        options.loadSettings();
        if (this.script != null) {
            this.acqControlDlg = new AcqControlDlg(
                    ((MMStudio)this.script).getAcquisitionEngine(),
                    prefs,
                    this.script,
                    options);
        }
    }

    @Override
    public Status run(final Task task, final Map<String,Config> conf, final Logger logger) {
        if (acqControlDlg != null) {
            // first try to load a position list from the DB
            if (conf.containsKey("posListId")) {
                Config posListId = conf.get("posListId");
                Dao<SlidePosList> posListDao = workflowRunner.getInstanceDb().table(SlidePosList.class);
                SlidePosList slidePosList = posListDao.selectOneOrDie(
                        where("id",Integer.parseInt(posListId.getValue())));
                PositionList posList = slidePosList.getPositionList();
                try { script.setPositionList(posList); } 
                catch (MMScriptException e) {throw new RuntimeException(e);}
            }
            // otherwise, load a position list from a file
            else if (conf.containsKey("posListFile")) {
                Config posList = conf.get("posListFile");
                File posListFile = new File(posList.getValue());
                if (!posListFile.exists()) {
                    throw new RuntimeException("Cannot find position list file "+posListFile.getPath());
                }
                try {
                    PositionList positionList = new PositionList();
                    positionList.load(posListFile.getPath());
                    script.setPositionList(positionList);
                } 
                catch (MMException e) {throw new RuntimeException(e);}
                catch (MMScriptException e) {throw new RuntimeException(e);}
            }

            // Load the settings and position list from the settings files
            if (conf.containsKey("acqSettingsFile")) {
                Config acqSettings = conf.get("acqSettingsFile");
                File acqSettingsFile = new File(acqSettings.getValue());
                if (!acqSettingsFile.exists()) {
                    throw new RuntimeException("Cannot find acquisition settings file "+acqSettingsFile.getPath());
                }
                try { acqControlDlg.loadAcqSettingsFromFile(acqSettingsFile.getPath()); } 
                catch (MMScriptException e) {throw new RuntimeException(e);}
            }

            // Get Dao objects ready for use
            final Dao<Task> taskDao = workflowRunner.getInstanceDb().table(Task.class);
            final Dao<TaskConfig> taskConfigDao = workflowRunner.getInstanceDb().table(TaskConfig.class);

            // get the position index of this task
            TaskConfig positionIndex = taskConfigDao.selectOne(
                    where("id",new Integer(task.getId()).toString()).and("key","positionIndex"));
            if (positionIndex == null) {
                throw new RuntimeException("positionIndex config not found for task ID "+task.getId());
            }

            // If this is the first position, start the acquisition engine
            if (new Integer(positionIndex.getValue()) == 0 && !conf.containsKey("imageId")) {
                // Set rootDir and acqName
                final String rootDir = new File(
                        workflowRunner.getWorkflowDir(), 
                        workflowRunner.getInstance().getStorageLocation()).getPath();
                final String acqName = "images";

                // make a map of position list index -> Task
                final List<Task> tasks = taskDao.select(where("moduleId",task.getModuleId()));
                final Map<Integer,Task> posList2Task = new HashMap<Integer,Task>();
                for (Task t : tasks) {
                    TaskConfig posIndex = taskConfigDao.selectOne(
                            where("id",new Integer(t.getId()).toString()).and("key","positionIndex"));
                    if (posIndex != null) {
                        posList2Task.put(new Integer(posIndex.getValue()),t);
                    }
                }

                // Create an acquisition record and get its ID. We won't know what acquisition name
                // it gets until we run the acquisition, so set it to null for now. After runAcqusition 
                // returns the real name, we'll update the acquisition DB record with the name.
                final Dao<Acquisition> acqDao = workflowRunner.getInstanceDb().table(Acquisition.class);

                // As images are completed, kick off the individual task related to the image
                this.script.addImageProcessor(new DataProcessor<TaggedImage>() {
					@Override protected void process() {
                        Acquisition acquisition = null;
						TaggedImage taggedImage = null;
						while ((taggedImage = this.poll()) != TaggedImageQueue.POISON) {
                            try {
                                // Make a copy of conf so that each child task can have its own configuration
                                Map<String,Config> config = new HashMap<String,Config>();
                                config.putAll(conf);

                                Integer positionIndex = MDUtils.getPositionIndex(taggedImage.tags);
                                if (posList2Task.containsKey(positionIndex)) {
                                    Task t = posList2Task.get(positionIndex);
                                    // Save the image label. This can be used by downstream processing scripts to get the TaggedImage
                                    // out of the ImageCache.
                                    String imageLabel = MDUtils.getLabel(taggedImage.tags);
                                    TaskConfig imageLabelConf = new TaskConfig(new Integer(t.getId()).toString(),"imageLabel",imageLabel);
                                    taskConfigDao.insertOrUpdate(imageLabelConf,"id","key");
                                    config.put("imageLabel", imageLabelConf);

                                    // get the slide ID and slide Position ID from the config
                                    if (!config.containsKey("slideId")) throw new RuntimeException("No slideId found for image!");
                                    Integer slideId = new Integer(config.get("slideId").getValue());
                                    if (!config.containsKey("slidePosId")) throw new RuntimeException("No slidePosId found for image!");
                                    Integer slidePosId = new Integer(config.get("slidePosId").getValue());

                                    // Make sure the acquisition name is set before running the child tasks.
                                    // We have to do it here to avoid a race condition.
                                    if (acquisition  == null) {
                                        String prefix = MDUtils.getSummary(taggedImage.tags).getString("Prefix");
                                        acquisition = new Acquisition(prefix, rootDir);
                                        acqDao.insert(acquisition);
                                    }
                                    
                                    // Create image DB record
                                    Dao<Image> imageDao = workflowRunner.getInstanceDb().table(Image.class);
                                    Image image = new Image(slideId, slidePosId, acquisition, 
                                            MDUtils.getChannelIndex(taggedImage.tags),
                                            MDUtils.getSliceIndex(taggedImage.tags),
                                            MDUtils.getFrameIndex(taggedImage.tags),
                                            MDUtils.getPositionIndex(taggedImage.tags));
                                    logger.info("Insert image: "+image.toString());
                                    imageDao.insert(image);

                                    // Store the Image ID as a Task Config variable
                                    TaskConfig imageId = new TaskConfig(
                                            new Integer(t.getId()).toString(),
                                            "imageId",
                                            new Integer(image.getId()).toString());
                                    taskConfigDao.insertOrUpdate(imageId,"id","key");
                                    config.put("imageId", imageId);
                                    // Also store the taggedImage object as a config value. This
                                    // is required when not running in offline mode, since the acquisition
                                    // folder will not have been written yet.
                                    config.put("taggedImage", new TaskConfig(
                                    		new Integer(t.getId()).toString(),
                                    		"taggedImage",
                                    		null,
                                    		taggedImage));
                                    
                                    // Run the downstream processing tasks for each image
                                    workflowRunner.run(t, config);
                                }
                                else {
                                    logger.warning("Unexpected position index "+positionIndex);
                                }
                                this.produce(taggedImage);
                            } 
                            catch (Exception e) {
                                StringWriter sw = new StringWriter();
                                PrintWriter pw = new PrintWriter(sw);
                                e.printStackTrace(pw);
                                logger.severe(String.format("Error reported during task %s:%n%s", 
                                    task.toString(), sw.toString()));
                                throw new RuntimeException(e);
                            }
						}
					}});
                String returnAcqName = acqControlDlg.runAcquisition(acqName, rootDir);

                // wait for all the acquisitions to finish
                if (returnAcqName == null) {
                    throw new RuntimeException("acqControlDlg.runAcquisition returned null acquisition name");
                }
                logger.info("Started acquisition to directory "+rootDir);

                while (acqControlDlg.isAcquisitionRunning()) {
                     try { Thread.sleep(1); } 
                     catch (InterruptedException e) { logger.info("Thread was interrupted."); } 
                }
                logger.info("Acquisition "+acqName+" finished.");
                // Set status to IN_PROGRESS, this task will be run again for the first image, which will set
                // the final status for this task.
                return Status.IN_PROGRESS;
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
    
    
    public void createTaskRecords() {
        WorkflowModule module = workflowRunner.getWorkflow().selectOneOrDie(where("id",moduleId));
        WorkflowModule parentModule = workflowRunner.getWorkflow().selectOne(where("id",module.getParentId()));

        Dao<Slide> slideDao = workflowRunner.getInstanceDb().table(Slide.class);
        Dao<SlidePosList> posListDao = workflowRunner.getInstanceDb().table(SlidePosList.class);
        Dao<SlidePos> posDao = workflowRunner.getInstanceDb().table(SlidePos.class);
        Dao<ModuleConfig> config = workflowRunner.getInstanceDb().table(ModuleConfig.class);
        Dao<TaskConfig> taskConfigDao = workflowRunner.getInstanceDb().table(TaskConfig.class);

        // Load all the module configuration into a HashMap
        Map<String,Config> conf = new HashMap<String,Config>();
        for (ModuleConfig c : config.select(where("id",this.moduleId))) {
            conf.put(c.getKey(), c);
        }

        // Create task records and connect to parent tasks
        // If no parent tasks were defined, then just create a single task instance.
        for (Task parentTask : parentModule != null? 
        		workflowRunner.getTaskStatus().select(where("moduleId",parentModule.getId())).toArray(new Task[0]) : 
        		new Task[] {null}) 
        {
        	// get the parent task configuration
        	Map<String,TaskConfig> parentTaskConf = new HashMap<String,TaskConfig>();
        	if (parentTask != null) {
                for (TaskConfig c : taskConfigDao.select(where("id",new Integer(parentTask.getId()).toString()))) {
                    parentTaskConf.put(c.getKey(), c);
                }
        	}
        	// Get the associated slide
        	Slide slide;
            if (parentTaskConf.containsKey("slideId")) {
            	slide = slideDao.selectOneOrDie(where("id",new Integer(parentTaskConf.get("slideId").getValue())));
            }
            // If no associated slide is registered, create a slide to represent this task
            else {
            	slide = new Slide(moduleId);
            	slideDao.insertOrUpdate(slide,"experimentId");
            	slide = slideDao.reload(slide, "experimentId");
            }
            // get the position list for the slide
            SlidePosList posList = null;
            // first try to load a position list from the DB
            if (conf.containsKey("posListModuleId")) {
                Config posListModuleId = conf.get("posListModuleId");
                posList = posListDao.selectOne(
                		where("slideId",slide.getId()).
                		and("moduleId",posListModuleId.getValue()));
                if (posList == null) {
                    throw new RuntimeException("Position list from module \""+posListModuleId.getValue()+"\" not found in database");
                }
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
                    posList = new SlidePosList(this.moduleId, slide, positionList);
                } 
                catch (MMException e) {throw new RuntimeException(e);}
                
                // store the loaded position list in the DB
                posListDao.insertOrUpdate(posList,"moduleId","slideId");
                posList = posListDao.reload(posList,"moduleId","slideId");
                // delete any old record first
                posDao.delete(where("slidePosListId", posList.getId()));
                // then create new records
                MultiStagePosition[] msps = posList.getPositionList().getPositions();
                for (int i=0; i<msps.length; ++i) {
                    posDao.insert(new SlidePos(posList.getId(), i));
                }
            }

            // Create the task records
            MultiStagePosition[] msps = posList.getPositionList().getPositions();
            for (int i=0; i<msps.length; ++i) {
                // Create task record
                Task task = new Task(moduleId, Status.NEW);
                workflowRunner.getTaskStatus().insert(task);
                task.createStorageLocation(
                        parentTask != null? parentTask.getStorageLocation() : null, 
                        new File(workflowRunner.getWorkflowDir(), 
                                workflowRunner.getInstance().getStorageLocation()).getPath());
                workflowRunner.getTaskStatus().update(task,"id");
                
                // Create taskConfig record to link task to position index in Position List
                taskConfigDao.insert(new TaskConfig(
                        new Integer(task.getId()).toString(), 
                        "positionIndex", 
                        new Integer(i).toString()));

                // create taskConfig record for the slide ID
                taskConfigDao.insert(new TaskConfig(
                        new Integer(task.getId()).toString(), 
                        "slideId", 
                        new Integer(slide.getId()).toString()));

                // Insert the slidePosId as a task config
                SlidePos slidePos = posDao.selectOne(
                        where("slidePosListId",posList.getId()).
                        and("slidePosListIndex",i));
                if (slidePos != null) {
                    taskConfigDao.insert(new TaskConfig(
                            new Integer(task.getId()).toString(), 
                            "slidePosId", 
                            new Integer(slidePos.getId()).toString()));
                }

                // Create task dispatch record
                if (parentTask != null) {
                    TaskDispatch dispatch = new TaskDispatch(task.getId(), parentTask.getId());
                    workflowRunner.getTaskDispatch().insert(dispatch);
                }
            }
        }
    	
    }

    @Override
    public TaskType getTaskType() {
        return Module.TaskType.SERIAL;
    }

    @Override public void cleanup(Task task) { 
        if (this.script != null) {
            AcquisitionEngine engine = ((MMStudio)this.script).getAcquisitionEngine();
            if (engine != null && engine.isAcquisitionRunning()) {
                engine.abortRequest();
            }
        }
    }
}
