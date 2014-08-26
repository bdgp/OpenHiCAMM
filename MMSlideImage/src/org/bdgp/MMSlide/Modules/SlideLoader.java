package org.bdgp.MMSlide.Modules;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bdgp.MMSlide.Dao;
import org.bdgp.MMSlide.Logger;
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
import org.bdgp.slideloader.prior.SlideLoaderAPI;
import org.bdgp.slideloader.prior.SequenceDoc;

import static org.bdgp.slideloader.prior.PriorSlideLoader.getSTATE_STATEMASK;
import static org.bdgp.slideloader.prior.PriorSlideLoader.getSTATE_IDLE;
import static org.bdgp.slideloader.prior.PriorSlideLoader.getLOADER_ERROR;
import static org.bdgp.MMSlide.Util.where;

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
        int[] retVal = {0};
        SlideLoaderAPI sl = new SlideLoaderAPI();
        Config deviceConfig = config.getOrDefault("device", null);
        String device = deviceConfig != null && !deviceConfig.getValue().isEmpty()? 
        		deviceConfig.getValue() : "/dev/tty.usbserial-FTEKUITV";
        sl.Connect(device, retVal);
        sl.get_Status(retVal);
        reportStatus(retVal[0], logger);
        
        // initialize Prior slide loader.....will take about 20 sec.
        sl.Initialise(retVal);
        do {
            try { Thread.sleep(1000); } catch (InterruptedException e) { }
            sl.get_Status(retVal);
            //reportStatus(retVal[0], logger);
        } while (((retVal[0] & getSTATE_STATEMASK()) != getSTATE_IDLE()) && !((retVal[0] & getLOADER_ERROR()) != 0));

        sl.get_CassettesFitted(retVal);
        sl.ScanCassette(1, retVal);
        do {
            sl.get_Status(retVal);
            //reportStatus(retVal[0], logger);
        } while (((retVal[0] & getSTATE_STATEMASK()) != getSTATE_IDLE()) && !((retVal[0] & getLOADER_ERROR()) != 0));

        for(int slide = 1; slide < 32; ++slide) {
            boolean[] slidePresent = {false};
            sl.get_SlideFitted(1, slide, slidePresent);
            if(slidePresent[0]) {
                sl.MoveToStage(1, slide, retVal);
                do {
                    try { Thread.sleep(1000); } catch (InterruptedException e) { }
                    sl.get_Status(retVal);
                    //reportStatus(retVal[0], logger);
                } while (((retVal[0] & getSTATE_STATEMASK()) != getSTATE_IDLE()) && !((retVal[0] & getLOADER_ERROR()) != 0));

                sl.MoveFromStage(1, slide, retVal);
                do {
                    try { Thread.sleep(1000); } catch (InterruptedException e) { }
                    sl.get_Status(retVal);
                    //reportStatus(retVal[0], logger);
                } while (((retVal[0] & getSTATE_STATEMASK()) != getSTATE_IDLE()) && !((retVal[0] & getLOADER_ERROR()) != 0));
            }
        }
        sl.DisConnect(retVal);
        return Status.SUCCESS;
	}

    public void reportStatus(int status, Logger logger) {
        String state = SequenceDoc.currentState(status);
        String motion = SequenceDoc.parseMotion(status);
        String errors = "no errors";
        if (SequenceDoc.errorPresent(status)) {
            errors = SequenceDoc.parseErrors(status);
        }
        logger.info("State: "+state);
        logger.info("State: "+state);
        logger.info("Motion: "+motion);
        logger.info("Errors: "+errors);
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

                if (dialog.device.getText().isEmpty()) {
                    errors.add(new ValidationError(null, "Please enter a slide loader device path"));
                }
                else if (!new File(dialog.device.getText()).exists()) {
                    errors.add(new ValidationError(null, "Could not find slide loader device file in path"));
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
                
                configs.add(new Config(SlideLoader.this.moduleId, "device", dialog.device.getText()));
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
				if (conf.containsKey("device")) {
					dialog.device.setText(conf.get("device").getValue());
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
                		parentTask != null? parentTask.getStorageLocation() : null,
                        new File(workflowRunner.getWorkflowDir(), 
                        		workflowRunner.getInstance().getStorageLocation()).getPath());
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
	public TaskType getTaskType() {
		return Module.TaskType.SERIAL;
	}
}
