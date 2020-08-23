package org.bdgp.OpenHiCAMM.Modules;


import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JOptionPane;

import org.bdgp.OpenHiCAMM.Dao;
import org.bdgp.OpenHiCAMM.Logger;
import org.bdgp.OpenHiCAMM.Util;
import org.bdgp.OpenHiCAMM.ValidationError;
import org.bdgp.OpenHiCAMM.WorkflowRunner;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.ModuleConfig;
import org.bdgp.OpenHiCAMM.DB.Pool;
import org.bdgp.OpenHiCAMM.DB.PoolSlide;
import org.bdgp.OpenHiCAMM.DB.Slide;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.Task.Status;
import org.bdgp.OpenHiCAMM.DB.TaskConfig;
import org.bdgp.OpenHiCAMM.DB.TaskDispatch;
import org.bdgp.OpenHiCAMM.DB.WorkflowModule;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Configuration;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Module;
import static org.bdgp.OpenHiCAMM.Util.where;

public class ManualSlideLoader implements Module {
    WorkflowRunner workflowRunner;
    WorkflowModule workflowModule;
    static boolean isInitialized = false;
    
    @Override
    public synchronized void initialize(WorkflowRunner workflowRunner, WorkflowModule workflowModule) {
        this.workflowRunner = workflowRunner;
        this.workflowModule = workflowModule;

        // set initial configs
        workflowRunner.getModuleConfig().insertOrUpdate(
                new ModuleConfig(this.workflowModule.getId(), "canLoadSlides", "yes"), 
                "id", "key");
    }

	@Override
	public synchronized Status run(Task task, Map<String, Config> config, Logger logger) {
		for (Config c : config.values()) {
			logger.fine(String.format("Using config: %s", c));
		}
        Dao<PoolSlide> poolSlideDao = workflowRunner.getWorkflowDb().table(PoolSlide.class);

        // get the PoolSlide ID to load
        Config loadPoolSlideIdConf = config.get("loadPoolSlideId");
        Integer loadPoolSlideId = loadPoolSlideIdConf != null && loadPoolSlideIdConf.getValue() != null? 
        		Integer.parseInt(loadPoolSlideIdConf.getValue()) : null;
        if (loadPoolSlideId != null) logger.info(String.format("Loading poolSlide ID: %s", Util.escape(loadPoolSlideId)));
        PoolSlide loadPoolSlide = loadPoolSlideId != null? poolSlideDao.selectOneOrDie(where("id",loadPoolSlideId)) : null;
        if (loadPoolSlide != null) logger.info(String.format("Loading poolSlide: %s", Util.escape(loadPoolSlide)));
        
        Dao<Slide> slideDao = workflowRunner.getWorkflowDb().table(Slide.class);
        Slide slide = loadPoolSlide != null? slideDao.selectOneOrDie(where("id",loadPoolSlide.getSlideId())) : null;
        if (slide != null) logger.info(String.format("Using slide: %s", Util.escape(slide)));

        logger.info(String.format("Running in manual slide loading mode"));
        if (loadPoolSlide != null) {
            JOptionPane.showMessageDialog(this.workflowRunner.getOpenHiCAMM().getDialog(),
                loadPoolSlide != null? 
                        "Please load slide number "+
                            loadPoolSlide.getSlidePosition()+" from cartridge "+
                            loadPoolSlide.getCartridgePosition()+
                            (slide != null? ", experiment ID \""+slide.getExperimentId()+"\"":"")
                    : "Please load the next slide",
                "Manual Slide Loading",
                JOptionPane.PLAIN_MESSAGE);
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
        final ManualSlideLoaderDialog dialog = new ManualSlideLoaderDialog(this, this.workflowRunner);
        return new Configuration() {
            @Override public ValidationError[] validate() {
                List<ValidationError> errors = new ArrayList<ValidationError>();
                if (dialog.poolList.getSelectedValue() == null) {
                    errors.add(new ValidationError(null, "Please select a pool"));
                }
                return errors.toArray(new ValidationError[0]);
            }
			@Override public Config[] retrieve() {
				List<Config> configs = new ArrayList<Config>();

                configs.add(new Config(ManualSlideLoader.this.workflowModule.getId(), "scanForSlides", "no"));

                int index = dialog.poolList.getSelectedIndex();
                if (index < 0) throw new RuntimeException("Invalid index stored in SlideLoader Configuration");
                String poolName = (String)dialog.poolList.getModel().getElementAt(index).toString();
                Integer poolId = Pool.name2id(poolName);
                if (poolId == null) throw new RuntimeException("Invalid pool name "+poolName);
                configs.add(new Config(ManualSlideLoader.this.workflowModule.getId(), "poolId", poolId.toString()));

                configs.add(new Config(ManualSlideLoader.this.workflowModule.getId(), "slideLoaderMode","manual"));
                
				return configs.toArray(new Config[0]);
			}
			@Override public Component display(Config[] configs) {
				Map<String,Config> conf = new HashMap<String,Config>();
				for (Config c : configs) {
					conf.put(c.getKey(), c);
				}
				if (conf.containsKey("poolId")) {
					Dao<Pool> poolDao = workflowRunner.getWorkflowDb().table(Pool.class);
					Pool pool = poolDao.selectOne(where("id",Integer.parseInt(conf.get("poolId").getValue())));
					if (pool != null) {
                        dialog.poolList.setSelectedValue(pool.getName(), true);
					}
				}
				return dialog;
			}
        };
    }

    @Override
    public List<Task> createTaskRecords(List<Task> parentTasks, Map<String,Config> configs, Logger logger) {
    	Dao<ModuleConfig> moduleConfigDao = workflowRunner.getModuleConfig();
    	Dao<PoolSlide> poolSlideDao = workflowRunner.getWorkflowDb().table(PoolSlide.class);
        Dao<TaskDispatch> taskDispatchDao = workflowRunner.getWorkflowDb().table(TaskDispatch.class);

    	ModuleConfig poolId = moduleConfigDao.selectOne(where("id",this.workflowModule.getId()).and("key","poolId"));

        // Get the pool record
        Dao<Pool> poolDao = this.workflowRunner.getWorkflowDb().table(Pool.class);
        Pool pool = poolDao.selectOne(where("id", Integer.parseInt(poolId.getValue())));
        if (pool == null) throw new RuntimeException("No pool record could be found!");
        List<PoolSlide> poolSlides = poolSlideDao.select(where("poolId",Integer.parseInt(poolId.getValue())));
    	Collections.sort(poolSlides);

    	Dao<Task> taskDao = workflowRunner.getWorkflowDb().table(Task.class);
    	Dao<TaskConfig> taskConfigDao = workflowRunner.getTaskConfig();
    	List<Task> tasks = new ArrayList<Task>();
    	for (Task parentTask : parentTasks.size()>0? parentTasks.toArray(new Task[0]) : new Task[] {null}) {
            workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Connecting parent task: %s", this.workflowModule.getName(), 
            		Util.escape(parentTask)));
            // Add sentinel poolSlide to represent putting the last slide back into the cartridge
    		for (int i=0; i<poolSlides.size(); ++i) {
    		    PoolSlide poolSlide = poolSlides.get(i);

    		    // create task record to put the slide on the stage
                Task loadTask = new Task(this.workflowModule.getId(), Status.NEW);
                taskDao.insert(loadTask);
                tasks.add(loadTask);
                workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Creating task %s for poolSlide %s", 
                        this.workflowModule.getName(), loadTask, poolSlide));

                TaskConfig poolSlideId = new TaskConfig(loadTask.getId(),
                        "loadPoolSlideId", 
                        poolSlide != null? Integer.toString(poolSlide.getId()) : null);
                taskConfigDao.insert(poolSlideId);
                workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Creating task config: %s", 
                        this.workflowModule.getName(), poolSlideId));

                TaskConfig slideId = new TaskConfig(loadTask.getId(),
                        "slideId", 
                        Integer.toString(poolSlide.getSlideId()));
                taskConfigDao.insert(slideId);
                workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Creating task config: %s", 
                        this.workflowModule.getName(), slideId));

                if (parentTask != null) {
                    TaskDispatch taskDispatch = new TaskDispatch(parentTask.getId(), loadTask.getId());
                    taskDispatchDao.insert(taskDispatch);
                    workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Creating taskDispatch: %s", 
                            this.workflowModule.getName(), taskDispatch));
                }
    		}
    		
    		// add a dummy task to put the last slide back
            Task dummyTask = new Task(this.workflowModule.getId(), Status.NEW);
            taskDao.insert(dummyTask);
            if (parentTask != null) {
                TaskDispatch taskDispatch = new TaskDispatch(parentTask.getId(), dummyTask.getId());
                taskDispatchDao.insert(taskDispatch);
            }
    	}
    	return tasks;
    }

	@Override
	public TaskType getTaskType() {
		return Module.TaskType.SERIAL;
	}
	
    @Override
    public Status setTaskStatusOnResume(Task task) {
        Set<Task> tasks = new HashSet<>();
        if (task != null) tasks.add(task);

        // if any serial child task needs to be re-run, then re-run this slide loader task first.
        while (!tasks.isEmpty()) {
            List<TaskDispatch> tds = new ArrayList<>();
            for (Task t : tasks) {
                Module module = this.workflowRunner.getModuleInstances().get(t.getModuleId());
                if (module == null) throw new RuntimeException(String.format("Unknown module: %s", t.getModuleId()));
                if (module.getTaskType() == Module.TaskType.SERIAL) {
                    if (t.getStatus() != Status.SUCCESS) {
                        return Task.Status.NEW;
                    }
                    tds.addAll(this.workflowRunner.getTaskDispatch().select(where("parentTaskId", t.getId())));
                }
            }
            tasks.clear();
            for (TaskDispatch td : tds) {
                tasks.addAll(this.workflowRunner.getTaskStatus().select(where("id", td.getTaskId())));
            }
        }
        return null;
    }
}
