package org.bdgp.MMSlide.Modules;

import java.awt.Component;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

import mmcorej.CMMCore;

import org.bdgp.MMSlide.Dao;
import org.bdgp.MMSlide.Logger;
import org.bdgp.MMSlide.Util;
import org.bdgp.MMSlide.ValidationError;
import org.bdgp.MMSlide.WorkflowRunner;
import org.bdgp.MMSlide.DB.Config;
import org.bdgp.MMSlide.DB.ModuleConfig;
import org.bdgp.MMSlide.DB.Pool;
import org.bdgp.MMSlide.DB.PoolSlide;
import org.bdgp.MMSlide.DB.Slide;
import org.bdgp.MMSlide.DB.Task;
import org.bdgp.MMSlide.DB.Task.Status;
import org.bdgp.MMSlide.DB.TaskConfig;
import org.bdgp.MMSlide.DB.TaskDispatch;
import org.bdgp.MMSlide.Modules.Interfaces.Configuration;
import org.bdgp.MMSlide.Modules.Interfaces.Module;
import org.bdgp.MMSlide.Modules.PriorSlideLoader.SlideLoaderAPI;
import org.bdgp.MMSlide.Modules.PriorSlideLoader.SequenceDoc;

import static org.bdgp.MMSlide.Modules.PriorSlideLoader.PriorSlideLoader.getSTATE_STATEMASK;
import static org.bdgp.MMSlide.Modules.PriorSlideLoader.PriorSlideLoader.getSTATE_IDLE;
import static org.bdgp.MMSlide.Modules.PriorSlideLoader.PriorSlideLoader.getLOADER_ERROR;
import static org.bdgp.MMSlide.Modules.PriorSlideLoader.PriorSlideLoader.getLOADER_NOTCONNECTED;
import static org.bdgp.MMSlide.Util.where;

public class SlideLoader implements Module {
    WorkflowRunner workflowRunner;
    String moduleId;
    SlideLoaderAPI slideLoader;
    PoolSlide currentSlide;
    
    @Override
    public void initialize(WorkflowRunner workflowRunner, String moduleId) {
        this.workflowRunner = workflowRunner;
        this.moduleId = moduleId;
        this.slideLoader = new SlideLoaderAPI();
    }

	@Override
	public synchronized Status run(Task task, Map<String, Config> config, Logger logger) {
		for (Config c : config.values()) {
			logger.info(String.format("Using config: %s", c));
		}
        // get the PoolSlide ID for this Slide
        Config poolSlideIdConf = config.get("poolSlideId");
        Integer poolSlideId = poolSlideIdConf != null && poolSlideIdConf.getValue() != null? 
        		new Integer(poolSlideIdConf.getValue()) : null;
        logger.info(String.format("Using poolSlide ID: %s", Util.escape(poolSlideId)));
        		
        Dao<PoolSlide> poolSlideDao = workflowRunner.getInstanceDb().table(PoolSlide.class);
        PoolSlide thisSlide = poolSlideId != null? poolSlideDao.selectOneOrDie(where("id",poolSlideId)) : null;
        logger.info(String.format("Using poolSlide: %s", Util.escape(thisSlide)));
        
        Dao<Slide> slideDao = workflowRunner.getInstanceDb().table(Slide.class);
        Slide slide = thisSlide != null? slideDao.selectOneOrDie(where("id",thisSlide.getSlideId())) : null;
        logger.info(String.format("Using slide: %s", Util.escape(slide)));

		Config mode = config.get("slideLoaderMode");
		if (mode.getValue().equals("manual")) {
            logger.info(String.format("Running in manual slide loading mode"));
            JOptionPane.showMessageDialog(this.workflowRunner.getMMSlide().getDialog(),
                "Manual Slide Loading",
                thisSlide != null? 
                		"Please load slide number "+
                            thisSlide.getSlidePosition()+" from cartridge "+
                            thisSlide.getCartridgePosition()+
                            (slide != null? ", experiment ID \""+slide.getExperimentId()+"\"":"")
                	: "Please load the next slide",
                JOptionPane.PLAIN_MESSAGE);
            return Status.SUCCESS;
		}
		else {
            logger.info(String.format("Running in automatic slide loading mode"));
		}

		// get the device path
        Config deviceConfig = config.get("device");
        String device = deviceConfig != null && !deviceConfig.getValue().isEmpty()? 
        		deviceConfig.getValue() : "/dev/tty.usbserial-FTEKUITV";
        logger.info(String.format("Using device: %s", device));
        		
        // Load the stage coordinates from the configuration
        if (!config.containsKey("initXCoord") 
        		|| !config.containsKey("initYCoord")
        		|| !config.containsKey("loadXCoord")
        		|| !config.containsKey("loadYCoord")) 
        {
        	throw new RuntimeException("Stage coordinates not configured!");
        }
        Double initXCoord = new Double(config.get("initXCoord").getValue());
        Double initYCoord = new Double(config.get("initYCoord").getValue());
        logger.info(String.format("Using init coords: (%.02f,%.02f)", initXCoord, initYCoord));

        Double loadXCoord = new Double(config.get("loadXCoord").getValue());
        Double loadYCoord = new Double(config.get("loadYCoord").getValue());
        logger.info(String.format("Using load coords: (%.02f,%.02f)", loadXCoord, loadYCoord));

        // get a sorted list of all the Task records for this module
        Dao<Task> taskDao = workflowRunner.getInstanceDb().table(Task.class);
        List<Task> tasks = taskDao.select(where("moduleId", this.moduleId));
        Collections.sort(tasks, new Comparator<Task>() {
			@Override public int compare(Task o1, Task o2) {
				return o1.getId()-o2.getId();
			}});

        // make a note of which Tasks are the first, last, and current.
        Task firstTask = tasks.get(0);
        Task lastTask = tasks.get(tasks.size()-1);

        int[] retVal = {0};
        boolean[] slidePresent = {false};
        Status status = Status.SUCCESS;

        // Initialize the slide loader if this is the first task
        if (task.getId() == firstTask.getId()) {
            slideLoader.Connect(device, retVal);
            slideLoader.get_Status(retVal);
            reportStatus(retVal[0], logger);
            
            logger.info("Positioning stage toward initialization point.");
            moveStage(initXCoord, initYCoord, logger);
            logger.info("Positioned stage at initialization point.");
            
            // initialize Prior slide loader.....will take about 20 sec.
            slideLoader.Initialise(retVal);
            waitForSlideLoader();

            logger.info("** Initialize Complete **");
            
            logger.info("Positioning stage toward load point.");
            moveStage(loadXCoord, loadYCoord, logger);
            logger.info("Positioned stage at load point.");

            slideLoader.get_CassettesFitted(retVal);
            slideLoader.ScanCassette(1, retVal);
            waitForSlideLoader();
        }
        
        // Put the previous slide back 
        if (this.currentSlide != null) {
            logger.info("Positioning stage toward load point.");
            moveStage(loadXCoord, loadYCoord, logger);
            logger.info("Positioned stage at load point.");

            logger.info("Moving slide ");
            slideLoader.MoveFromStage(
            		this.currentSlide.getCartridgePosition(), 
            		this.currentSlide.getSlidePosition(), 
            		retVal);
            waitForSlideLoader();
            this.currentSlide = null;
        }

        // Load the next slide
        if (thisSlide != null) {
            // Make sure the slide is present
            slideLoader.get_SlideFitted(thisSlide.getCartridgePosition(), thisSlide.getSlidePosition(), slidePresent);
            if (slidePresent[0]) {
                slideLoader.MoveToStage(thisSlide.getCartridgePosition(), thisSlide.getSlidePosition(), retVal);
                waitForSlideLoader();
                this.currentSlide = thisSlide;
            }
            // If the slide is missing, mark this task as fail
            else {
                logger.severe("Slide at cartridge "+thisSlide.getCartridgePosition()+
                        ", slide position "+thisSlide.getSlidePosition()+" is missing!");
                status = Status.FAIL;
            }
        }

        // Disconnect the slide loader if this is the last task
        if (task.getId() == lastTask.getId()) {
        	logger.info("Disconnecting the slide loader");
            slideLoader.DisConnect(retVal);
        }
        return status;
	}
	
	public int waitForSlideLoader() {
		int[] retVal = {0};
        do {
            try { Thread.sleep(1000); } catch (InterruptedException e) { }
            slideLoader.get_Status(retVal);
            //reportStatus(retVal[0], logger);
        } while (((retVal[0] & getSTATE_STATEMASK()) != getSTATE_IDLE()) && 
        		!((retVal[0] & getLOADER_ERROR()) != 0));
        return retVal[0];
	}

    public void reportStatus(int status, Logger logger) {
        String state = SequenceDoc.currentState(status);
        String motion = SequenceDoc.parseMotion(status);
        String errors = "no errors";
        if (SequenceDoc.errorPresent(status)) {
            errors = SequenceDoc.parseErrors(status);
        }
        logger.info("State: "+state);
        logger.info("Motion: "+motion);
        logger.info("Errors: "+errors);

        if ((status & getLOADER_NOTCONNECTED()) != 0) {
            throw new RuntimeException("Loader is not connected!");
        }
    }
    
    public void moveStage(double x, double y, Logger logger) {
    	CMMCore core = workflowRunner.getMMSlide().getApp().getMMCore();
    	core.setTimeoutMs(10000);
        String xyStage = core.getXYStageDevice();
        try {
            //double[] x_stage = new double[] {0.0};
            //double[] y_stage = new double[] {0.0};
            //core.getXYPosition(xyStage, x_stage, y_stage);

            core.setXYPosition(xyStage, x, y);
            // wait for the stage to finish moving
            while (core.deviceBusy(xyStage)) {}
            
            //double[] x_stage_new = new double[] {0.0};
            //double[] y_stage_new = new double[] {0.0};
            //core.getXYPosition(xyStage, x_stage_new, y_stage_new);
            //double epsilon = 10;
            //if (Math.abs(x_stage[0]-x_stage_new[0]) < epsilon && Math.abs(y_stage[0]-y_stage_new[0]) < epsilon) {
            //	throw new RuntimeException("Stage did not move at all!");
            //}
            //if (Math.abs(x_stage_new[0]-x) < epsilon && Math.abs(y_stage_new[0]-y) < epsilon) {
            //	throw new RuntimeException(String.format("Stage moved to wrong coordinates: (%.2f,%.2f)",
            //			x_stage_new[0], y_stage_new[0]));
            //}
		} 
        catch (Throwable e) { 
        	StringWriter sw = new StringWriter();
        	e.printStackTrace(new PrintWriter(sw));
        	logger.severe(String.format("Failed to move stage to position (%.2f,%.2f): %s", x,y, sw.toString()));
        	throw new RuntimeException(e);
        }
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

                if (dialog.initXCoord.getDouble() == null || dialog.initYCoord.getDouble() == null) {
                	errors.add(new ValidationError(null, "Invalid Init Stage Coordinates: "+
                			dialog.initXCoord.getDouble()+","+dialog.initYCoord.getDouble()));
                }
                if (dialog.loadXCoord.getDouble() == null || dialog.loadYCoord.getDouble() == null) {
                	errors.add(new ValidationError(null, "Invalid Loading Stage Coordinates: "+
                			dialog.loadXCoord.getDouble()+","+dialog.loadYCoord.getDouble()));
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

                configs.add(new Config(SlideLoader.this.moduleId, "initXCoord", dialog.initXCoord.getDouble().toString()));
                configs.add(new Config(SlideLoader.this.moduleId, "initYCoord", dialog.initYCoord.getDouble().toString()));
                configs.add(new Config(SlideLoader.this.moduleId, "loadXCoord", dialog.loadXCoord.getDouble().toString()));
                configs.add(new Config(SlideLoader.this.moduleId, "loadYCoord", dialog.loadYCoord.getDouble().toString()));

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
				if (conf.containsKey("initXCoord") && conf.containsKey("initYCoord")) {
					dialog.initXCoord.setValue(new Double(conf.get("initXCoord").getValue()));
					dialog.initYCoord.setValue(new Double(conf.get("initYCoord").getValue()));
				}
				if (conf.containsKey("loadXCoord") && conf.containsKey("loadYCoord")) {
					dialog.loadXCoord.setValue(new Double(conf.get("loadXCoord").getValue()));
					dialog.loadYCoord.setValue(new Double(conf.get("loadYCoord").getValue()));
				}
				return dialog;
			}
        };
    }

    @Override
    public void createTaskRecords() {
    	Dao<ModuleConfig> moduleConfigDao = workflowRunner.getInstanceDb().table(ModuleConfig.class);
    	ModuleConfig poolId = moduleConfigDao.selectOne(where("id",this.moduleId).and("key","poolId"));
    	workflowRunner.getLogger().info(String.format("%s: createTaskRecords: Using pool config: %s", this.moduleId, poolId));

    	List<PoolSlide> poolSlides = new ArrayList<PoolSlide>();
    	if (poolId != null) {
    		Dao<Pool> poolDao = workflowRunner.getInstanceDb().table(Pool.class);
    		Pool pool = poolDao.selectOne(where("id",new Integer(poolId.getValue())));
            workflowRunner.getLogger().info(String.format("%s: createTaskRecords: Using pool: %s", this.moduleId, pool));
    		if (pool != null) {
                Dao<PoolSlide> poolSlideDao = workflowRunner.getInstanceDb().table(PoolSlide.class);
                poolSlides = poolSlideDao.select(where("poolId",new Integer(poolId.getValue())));
    		}
    	}
    	Collections.sort(poolSlides);
        poolSlides.add(null);

    	Dao<Task> taskDao = workflowRunner.getInstanceDb().table(Task.class);
    	List<Task> parentTasks = taskDao.select(where("moduleId",moduleId));
    	for (Task parentTask : parentTasks.size()>0? parentTasks.toArray(new Task[0]) : new Task[] {null}) {
            workflowRunner.getLogger().info(String.format("%s: createTaskRecords: Connecting parent task: %s", this.moduleId, 
            		Util.escape(parentTask)));
    		for (PoolSlide poolSlide : poolSlides) {
                Task task = new Task(moduleId, Status.NEW);
                taskDao.insert(task);
                task.createStorageLocation(
                		parentTask != null? parentTask.getStorageLocation() : null,
                        new File(workflowRunner.getWorkflowDir(), 
                        		workflowRunner.getInstance().getStorageLocation()).getPath());
                taskDao.update(task,"id");
                workflowRunner.getLogger().info(String.format("%s: createTaskRecords: Creating task %s for poolSlide %s", 
                		this.moduleId, task, poolSlide));

                if (parentTask != null) {
                    Dao<TaskDispatch> taskDispatchDao = workflowRunner.getInstanceDb().table(TaskDispatch.class);
                    TaskDispatch taskDispatch = new TaskDispatch(parentTask.getId(), task.getId());
                    taskDispatchDao.insert(taskDispatch);
                    workflowRunner.getLogger().info(String.format("%s: createTaskRecords: Creating taskDispatch: %s", 
                            this.moduleId, taskDispatch));
                }
                Dao<TaskConfig> taskConfigDao = workflowRunner.getInstanceDb().table(TaskConfig.class);

                TaskConfig poolSlideId = new TaskConfig(new Integer(task.getId()).toString(), 
                        "poolSlideId", 
                        poolSlide != null? new Integer(poolSlide.getId()).toString() : null);
                taskConfigDao.insert(poolSlideId);
                workflowRunner.getLogger().info(String.format("%s: createTaskRecords: Creating task config: %s", 
                        this.moduleId, poolSlideId));

                if (poolSlide != null) {
                	TaskConfig slideId = new TaskConfig(new Integer(task.getId()).toString(), 
                            "slideId", 
                            new Integer(poolSlide.getSlideId()).toString());
                    taskConfigDao.insert(slideId);
                    workflowRunner.getLogger().info(String.format("%s: createTaskRecords: Creating task config: %s", 
                            this.moduleId, slideId));
                }
    		}
    	}
    }

	@Override
	public TaskType getTaskType() {
		return Module.TaskType.SERIAL;
	}
	
    @Override public void cleanup(Task task) { }
}
