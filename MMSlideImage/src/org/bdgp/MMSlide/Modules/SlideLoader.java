package org.bdgp.MMSlide.Modules;

import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bdgp.MMSlide.Dao;
import org.bdgp.MMSlide.Logger;
import org.bdgp.MMSlide.Util;
import org.bdgp.MMSlide.ValidationError;
import org.bdgp.MMSlide.WorkflowRunner;
import org.bdgp.MMSlide.DB.Config;
import org.bdgp.MMSlide.DB.ModuleConfig;
import org.bdgp.MMSlide.DB.Pool;
import org.bdgp.MMSlide.DB.PoolSlide;
import org.bdgp.MMSlide.DB.Task;
import org.bdgp.MMSlide.DB.Task.Status;
import org.bdgp.MMSlide.DB.TaskConfig;
import org.bdgp.MMSlide.DB.TaskDispatch;
import org.bdgp.MMSlide.Modules.Interfaces.Configuration;
import org.bdgp.MMSlide.Modules.Interfaces.Module;

import static org.bdgp.MMSlide.Util.where;
import static org.bdgp.MMSlide.Util.map;

public class SlideLoader implements Module {
    WorkflowRunner workflowRunner;
    String moduleId;

    @Override
    public void initialize(WorkflowRunner workflowRunner, String moduleId) {
        this.workflowRunner = workflowRunner;
        this.moduleId = moduleId;
    }


	@Override
	public Status run(Task task, Map<String, Config> config, Logger logger) {
        Util.sleep();
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
        final SlideLoaderDialog dialog = new SlideLoaderDialog(workflowRunner.getInstanceDb(), workflowRunner.getMMSlide());
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
                int index = dialog.poolList.getSelectedIndex();
                if (index < 0) throw new RuntimeException("Invalid index stored in SlideLoader Configuration");
                String poolName = (String)dialog.poolList.getModel().getElementAt(index).toString();
                Integer poolId = Pool.name2id(poolName);
                if (poolId == null) throw new RuntimeException("Invalid pool name "+poolName);

                configs.add(new Config(SlideLoader.this.moduleId, "poolId", poolId.toString()));
                if (dialog.radioButtonSlideLoader.isSelected()) {
                	configs.add(new Config(SlideLoader.this.moduleId, "slideLoaderMode","automatic"));
                }
                else if (dialog.radioButtonSlideManual.isSelected()) {
                	configs.add(new Config(SlideLoader.this.moduleId, "slideLoaderMode","manual"));
                }
				return configs.toArray(new Config[0]);
			}
			@Override public Component display(Config[] configs) {
				Map<String,Config> conf = new HashMap<String,Config>();
				for (Config c : configs) {
					conf.put(c.getKey(), c);
				}
				if (conf.containsKey("poolId")) {
					Dao<Pool> poolDao = workflowRunner.getInstanceDb().table(Pool.class);
					Pool pool = poolDao.selectOneOrDie(where("id",new Integer(conf.get("poolId").getValue())));
					dialog.poolList.setSelectedValue(pool.getName(), true);
				}
				if (conf.containsKey("slideLoaderMode")) {
					if (conf.get("slideLoaderMode").getValue().equals("automatic")) {
						dialog.radioButtonSlideLoader.setSelected(true);
						dialog.radioButtonSlideManual.setSelected(false);
					}
					else if (conf.get("slideLoaderMode").getValue().equals("manual")) {
						dialog.radioButtonSlideLoader.setSelected(false);
						dialog.radioButtonSlideManual.setSelected(true);
					}
				}
				return dialog;
			}
        };
    }

    @Override
    public void createTaskRecords() {
    	Dao<ModuleConfig> moduleConfigDao = workflowRunner.getInstanceDb().table(ModuleConfig.class);
    	ModuleConfig poolId = moduleConfigDao.selectOne(where("moduleId",this.moduleId).and("key","poolId"));
    	List<PoolSlide> poolSlides = new ArrayList<PoolSlide>();
    	if (poolId != null) {
    		Dao<Pool> poolDao = workflowRunner.getInstanceDb().table(Pool.class);
    		Pool pool = poolDao.selectOne(where("id",new Integer(poolId.getValue())));
    		if (pool != null) {
                Dao<PoolSlide> poolSlideDao = workflowRunner.getInstanceDb().table(PoolSlide.class);
                poolSlides = poolSlideDao.select(where("poolId",new Integer(poolId.getValue())));
    		}
    	}
    	Dao<Task> taskDao = workflowRunner.getInstanceDb().table(Task.class);
    	List<Task> parentTasks = taskDao.select(where("moduleId",moduleId));
    	for (Task parentTask : parentTasks.size()>0? parentTasks.toArray(new Task[0]) : new Task[] {null}) {
    		for (PoolSlide poolSlide : poolSlides.size()>0? poolSlides.toArray(new PoolSlide[0]) : new PoolSlide[] {null}) {
                Task task = new Task(moduleId, Status.NEW);
                taskDao.insert(task);
                task.createStorageLocation(
                        workflowRunner.getInstance().getStorageLocation(), 
                        task.getStorageLocation());
                taskDao.update(task,"id");

                if (parentTask != null) {
                    Dao<TaskDispatch> taskDispatchDao = workflowRunner.getInstanceDb().table(TaskDispatch.class);
                    taskDispatchDao.insert(new TaskDispatch(parentTask.getId(), task.getId()));
                }
                if (poolSlide != null) {
                	Dao<TaskConfig> taskConfigDao = workflowRunner.getInstanceDb().table(TaskConfig.class);
                	taskConfigDao.insert(new TaskConfig(new Integer(task.getId()).toString(), 
                			"poolSlideId", 
                			new Integer(poolSlide.getId()).toString()));
                }
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
