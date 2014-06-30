package org.bdgp.MMSlide.Modules;

import java.awt.Component;
import java.io.File;
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
import org.bdgp.MMSlide.DB.Config;
import org.bdgp.MMSlide.DB.Image;
import org.bdgp.MMSlide.DB.ModuleConfig;
import org.bdgp.MMSlide.DB.PoolSlide;
import org.bdgp.MMSlide.DB.SlidePos;
import org.bdgp.MMSlide.DB.SlidePosList;
import org.bdgp.MMSlide.DB.Task;
import org.bdgp.MMSlide.DB.Task.Status;
import org.bdgp.MMSlide.DB.TaskConfig;
import org.bdgp.MMSlide.DB.TaskDispatch;
import org.bdgp.MMSlide.DB.WorkflowModule;
import org.bdgp.MMSlide.Modules.Interfaces.Configuration;
import org.bdgp.MMSlide.Modules.Interfaces.Module;
import org.json.JSONException;
import org.micromanager.AcqControlDlg;
import org.micromanager.MMOptions;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.api.ImageCacheListener;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.MMException;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.MDUtils;

import static org.bdgp.MMSlide.Util.where;
import static org.bdgp.MMSlide.Util.map;

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
            		((MMStudioMainFrame)this.script).getAcquisitionEngine(),
            		prefs,
            		this.script,
            		options);
        }
    }

    @Override
    public Status run(final Task task, final Map<String,Config> conf, Logger logger) {
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
            if (!((MMStudioMainFrame)this.script).getAcquisitionEngine().isAcquisitionRunning()) {
            	// Set rootDir and acqName
                String rootDir = new File(workflowRunner.getWorkflowDirectory(),
                        task.getStorageLocation()).getParent();
                String acqName = new File(workflowRunner.getWorkflowDirectory(),
                        task.getStorageLocation()).getName();
                // make a map of position list index -> Task
                final Dao<Task> taskDao = workflowRunner.getInstanceDb().table(Task.class);
                final Dao<TaskDispatch> taskDispatchDao = workflowRunner.getInstanceDb().table(TaskDispatch.class);
                final Dao<TaskConfig> taskConfigDao = workflowRunner.getInstanceDb().table(TaskConfig.class);
                final List<Task> tasks = taskDao.select(where("moduleId",task.getModuleId()));
                final Map<Integer,Task> posList2Task = new HashMap<Integer,Task>();
                for (Task t : tasks) {
                	TaskConfig positionIndex = taskConfigDao.selectOne(where("id",new Integer(t.getId()).toString()));
                	if (positionIndex != null) {
                		posList2Task.put(new Integer(positionIndex.getValue()),t);
                	}
                }

                // As images are completed, kick off the individual task related to the image
                ((MMStudioMainFrame)this.script).getAcquisitionEngine().getImageCache().addImageCacheListener(new ImageCacheListener() {
                    @Override public void imageReceived(TaggedImage taggedImage) {
                    	try {
							Integer positionIndex = MDUtils.getPositionIndex(taggedImage.tags);

							if (posList2Task.containsKey(positionIndex)) {
								Task t = posList2Task.get(positionIndex);
								t.setStatus(Status.SUCCESS);
								taskDao.update(t);
								// Save the image label. This can be used by downstream processing scripts to get the TaggedImage
								// out of the ImageCache.
                                String imageLabel = MDUtils.getLabel(taggedImage.tags);
								taskConfigDao.insert(new TaskConfig(new Integer(t.getId()).toString(),"imageLabel",imageLabel));
								// Try to get the Slide ID
								List<TaskDispatch> parentTaskDispatch = taskDispatchDao.select(where("id",task.getId()));
								Integer slideId = new Integer(0);
								if (parentTaskDispatch.size() == 1) {
									TaskConfig poolSlideId = taskConfigDao.selectOne(
											where("id",parentTaskDispatch.get(0).getParentTaskId()).
											and("key","poolSlideId"));
									if (poolSlideId != null) {
										Dao<PoolSlide> poolSlideDao = workflowRunner.getInstanceDb().table(PoolSlide.class);
										PoolSlide poolSlide = poolSlideDao.selectOneOrDie(where("id",new Integer(poolSlideId.getValue())));
										slideId = poolSlide.getSlideId();
									}
                                }
								// Try to get the SlidePos ID
								TaskConfig slidePosIdConfig = taskConfigDao.selectOne(
										where("id",new Integer(task.getId()).toString()).
										and("key","slidePosId"));
								Integer slidePosId = new Integer(slidePosIdConfig != null? new Integer(slidePosIdConfig.getValue()) : 0);

								// Create image DB record
								Dao<Image> imageDao = workflowRunner.getInstanceDb().table(Image.class);
								Image image = new Image(slideId, slidePosId, MDUtils.getFileName(taggedImage.tags), taggedImage.tags);
								imageDao.insert(image);

								// Store the Image ID as a Task Config variable
								taskConfigDao.insert(new TaskConfig(
										new Integer(t.getId()).toString(),
										"imageId",
										new Integer(image.getId()).toString()));
								
								// Run the downstream processing tasks for each image
								List<TaskDispatch> childTaskIds = taskDispatchDao.select(where("parentId",t.getId()));
								for (TaskDispatch childTaskId : childTaskIds) {
									Task child = taskDao.selectOne(where("id",childTaskId.getTaskId()));
									if (child != null) {
                                        workflowRunner.run(child, conf);
									}
								}
							}
						} 
                    	catch (JSONException e) {throw new RuntimeException(e);}
                    }
                    @Override public void imagingFinished(String path) { }
                });
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
                return Status.SUCCESS;
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
        	SlideImagerDialog slideImagerDialog = new SlideImagerDialog(acqControlDlg);

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
            	if (slideImagerDialog.posListName.getText().length()>0) {
            		configs.add(new Config(moduleId, 
            				"posListName", 
            				slideImagerDialog.posListName.getText()));
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
                if (conf.containsKey("posListName")) {
                    Config posListName = conf.get("posListName");
                	slideImagerDialog.posListName.setText(posListName.getValue());
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
            			+ (slideImagerDialog.posListName.getText().length()>0? 1: 0) == 1))
            	{
            		errors.add(new ValidationError(moduleId, 
            				"You must enter one of either a position list file, or a position list name."));
            	}
                return errors.toArray(new ValidationError[0]);
            }
        };
    }
    
    @Override
    public void createTaskRecords() {
    	// Load all the module configuration into a HashMap
    	Dao<ModuleConfig> config = workflowRunner.getInstanceDb().table(ModuleConfig.class);
    	Map<String,Config> conf = new HashMap<String,Config>();
    	for (ModuleConfig c : config.select(where("id",this.moduleId))) {
    		conf.put(c.getKey(), c);
    	}

    	SlidePosList posList = null;
        // first try to load a position list from the DB
        Dao<SlidePosList> posListDao = workflowRunner.getInstanceDb().table(SlidePosList.class);
        Dao<SlidePos> posDao = workflowRunner.getInstanceDb().table(SlidePos.class);
        if (conf.containsKey("posListName")) {
            Config posListName = conf.get("posListName");
            posList = posListDao.selectOne(where("name",posListName.getValue()));
            if (posList == null) {
            	throw new RuntimeException("Position list with name \""+posListName.getValue()+"\" not found in database");
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
            	posList = new SlidePosList(posListConf.getValue(), positionList);
            } 
            catch (MMException e) {throw new RuntimeException(e);}
            
            // store the loaded position list in the DB
            posListDao.insert(posList);
            MultiStagePosition[] msps = posList.getPositionList().getPositions();
            for (int i=0; i<msps.length; ++i) {
            	posDao.insert(new SlidePos(posList.getId(), i));
            }
        }

        // get any parent tasks
        WorkflowModule module = workflowRunner.getWorkflow().selectOneOrDie(where("id",moduleId));
        Task parentTask = workflowRunner.getTaskStatus().selectOne(where("moduleId",module.getParentId()));
        // Create the task records
        MultiStagePosition[] msps = posList.getPositionList().getPositions();
        for (int i=0; i<msps.length; ++i) {
        	// Create task record
            Task task = new Task(moduleId, Status.NEW);
            workflowRunner.getTaskStatus().insert(task);
            task.createStorageLocation(
            		parentTask != null? parentTask.getStorageLocation() : null, 
            		workflowRunner.getWorkflowDirectory().getPath());
            workflowRunner.getTaskStatus().update(task,"id");
            
            // Create taskConfig record to link task to position index in Position List
            Dao<TaskConfig> taskConfigDao = workflowRunner.getInstanceDb().table(TaskConfig.class);
            taskConfigDao.insert(new TaskConfig(
            		new Integer(task.getId()).toString(), 
            		"positionIndex", 
            		new Integer(i).toString()));
            
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

    @Override
    public Map<String, Integer> getResources() {
        return map("cpu",1);
    }

	@Override
	public TaskType getTaskType() {
		return Module.TaskType.SERIAL;
	}
}
