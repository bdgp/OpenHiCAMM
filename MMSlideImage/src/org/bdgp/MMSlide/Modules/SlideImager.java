package org.bdgp.MMSlide.Modules;

import java.awt.Component;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.JPanel;

import org.bdgp.MMSlide.Dao;
import org.bdgp.MMSlide.Logger;
import org.bdgp.MMSlide.ValidationError;
import org.bdgp.MMSlide.WorkflowRunner;
import org.bdgp.MMSlide.DB.Config;
import org.bdgp.MMSlide.DB.SlidePosList;
import org.bdgp.MMSlide.DB.Task;
import org.bdgp.MMSlide.DB.Task.Status;
import org.bdgp.MMSlide.DB.TaskDispatch;
import org.bdgp.MMSlide.DB.WorkflowModule;
import org.bdgp.MMSlide.Modules.Interfaces.Configuration;
import org.bdgp.MMSlide.Modules.Interfaces.Module;
import org.micromanager.AcqControlDlg;
import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.MMScriptException;

import static org.bdgp.MMSlide.Util.where;
import static org.bdgp.MMSlide.Util.map;

public class SlideImager implements Module {
    WorkflowRunner workflowRunner;
    String moduleId;
    AcqControlDlg acqControlDlg;
    ScriptInterface script;

    @SuppressWarnings("deprecation")
	@Override
    public void initialize(WorkflowRunner workflowRunner, String moduleId) {
        this.workflowRunner = workflowRunner;
        this.moduleId = moduleId;
        this.script = workflowRunner.getMMSlide().getApp();
        if (script != null) {
            this.acqControlDlg = script.getAcqDlg();
        }
    }

    @Override
    public Status run(Task task, Map<String,Config> config, Logger logger) {
        if (acqControlDlg != null) {
            String rootDir = new File(workflowRunner.getWorkflowDirectory(),
            		task.getStorageLocation()).getParent();
            String acqName = new File(workflowRunner.getWorkflowDirectory(),
            		task.getStorageLocation()).getName();
        	String returnAcqName = acqControlDlg.runAcquisition(acqName, rootDir);
        	if (returnAcqName == null) {
        		throw new RuntimeException("acqControlDlg.runAcquisition returned null acquisition name");
        	}
        	logger.info("Started acquisition to directory "+rootDir);
        	while (acqControlDlg.isAcquisitionRunning()) {
        		 try { Thread.sleep(1); } 
        		 catch (InterruptedException e) { 
        			 logger.info("Thread was interrupted.");
        		 } 
        	}
        	logger.info("Acquisition "+acqName+" finished.");
            return Status.SUCCESS;
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
            @Override
            public Config[] retrieve() {
            	List<Config> configs = new ArrayList<Config>();
                if (acqControlDlg != null) {
                    try {
                    	// Use reflection to get at the acqPrefs_ private member.
                    	// Ugly, but it's the only way I could figure out to save
                    	// the prefs without popping up a Save As dialog.
                        Field f = acqControlDlg.getClass().getDeclaredField("acqPrefs_");
                        f.setAccessible(true);
                        Preferences acqPrefs = (Preferences) f.get(acqControlDlg);
                        OutputStream prefs = new ByteArrayOutputStream();
                        acqPrefs.exportNode(prefs);
                        configs.add(new Config(moduleId, "acqDlgSettings", prefs.toString()));
                    } 
                    catch (NoSuchFieldException e) {throw new RuntimeException(e);} 
                    catch (SecurityException e) {throw new RuntimeException(e);} 
                    catch (IllegalArgumentException e) {throw new RuntimeException(e);} 
                    catch (IllegalAccessException e) {throw new RuntimeException(e);} 
                    catch (IOException e) {throw new RuntimeException(e);} 
                    catch (BackingStoreException e) {throw new RuntimeException(e);}
                }
                return configs.toArray(new Config[0]);
            }
            @Override
            public Component display(Config[] configs) {
                Map<String,Config> conf = new HashMap<String,Config>();
                for (Config config : configs) {
                    conf.put(config.getKey(), config);
                }
                if (acqControlDlg != null) {
                    try {
                    	// AcqControlDlg has a public method for loading 
                    	// preferences from a file, so we can use a temp file 
                    	// here to store the preferences, then load them, and 
                    	// delete the temp file. Still ugly, but not as ugly 
                    	// as grabbing a private field.
                    	if (conf.containsKey("acqDlgSettings")) {
                            Config acqDlgSettings = conf.get("acqDlgSettings");
                            File tempFile = File.createTempFile("mmslideimageprefs_",".prefs");
                            PrintWriter out = new PrintWriter(tempFile.getPath());
                            out.print(acqDlgSettings.getValue());
                            out.close();
                            acqControlDlg.loadAcqSettingsFromFile(tempFile.getPath());
                            tempFile.delete();
                    	}
                    	// set the position list if there's a posListId 
                    	// configuration value defined
                    	if (conf.containsKey("posListId")) {
                            Config posListId = conf.get("posListId");
                            Dao<SlidePosList> posListDao = workflowRunner.getInstanceDb().table(SlidePosList.class);
                            SlidePosList slidePosList = posListDao.selectOneOrDie(
                            		where("id",Integer.parseInt(posListId.getValue())));
                            PositionList posList = slidePosList.getPositionList();
                            script.setPositionList(posList);
                    	}
					} 
                    catch (MMScriptException e) {throw new RuntimeException(e);}
					catch (IOException e) {throw new RuntimeException(e);}

                    return acqControlDlg;
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
