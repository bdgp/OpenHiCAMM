package org.bdgp.OpenHiCAMM.Modules;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import org.bdgp.OpenHiCAMM.Dao;
import org.bdgp.OpenHiCAMM.ImageLog;
import org.bdgp.OpenHiCAMM.ImageLog.ImageLogRecord;
import org.bdgp.OpenHiCAMM.ImageLog.ImageLogRunner;
import org.bdgp.OpenHiCAMM.Logger;
import org.bdgp.OpenHiCAMM.OpenHiCAMM;
import org.bdgp.OpenHiCAMM.ValidationError;
import org.bdgp.OpenHiCAMM.WorkflowRunner;
import org.bdgp.OpenHiCAMM.DB.Acquisition;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.Image;
import org.bdgp.OpenHiCAMM.DB.ModuleConfig;
import org.bdgp.OpenHiCAMM.DB.Slide;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.Task.Status;
import org.bdgp.OpenHiCAMM.DB.TaskDispatch;
import org.bdgp.OpenHiCAMM.DB.WorkflowModule;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Configuration;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.ImageLogger;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Module;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.scijava.plugin.SciJavaPlugin;
import org.micromanager.Studio;

import static org.bdgp.OpenHiCAMM.Util.where;

public abstract class PostProcessor implements Module, ImageLogger, SciJavaPlugin {
    protected WorkflowRunner workflowRunner;
    protected WorkflowModule workflowModule;
    protected Studio script;

    @Override
    public void initialize(WorkflowRunner workflowRunner, WorkflowModule workflowModule) {
        this.workflowRunner = workflowRunner;
        this.workflowModule = workflowModule;
        OpenHiCAMM mmslide = workflowRunner.getOpenHiCAMM();
        this.script = mmslide.getApp();
        
        // set initial configs
        workflowRunner.getModuleConfig().insertOrUpdate(
                new ModuleConfig(this.workflowModule.getId(), "canPostProcess", "yes"), 
                "id", "key");
    }

    @Override
    public Status run(Task task, Map<String,Config> config, final Logger logger) {
    	logger.fine(String.format("Running task: %s", task));
    	for (Config c : config.values()) {
            logger.fine(String.format("Using config: %s", c));
    	}

        // Get the Image record
    	Config imageIdConf = config.get("imageId");
    	if (imageIdConf == null) throw new RuntimeException("No imageId task configuration was set by the slide imager!");
        Integer imageId = Integer.parseInt(imageIdConf.getValue());
        Dao<Image> imageDao = workflowRunner.getWorkflowDb().table(Image.class);
        final Image image = imageDao.selectOneOrDie(where("id",imageId));

        // get the tagged image
        org.micromanager.data.Image mmimage = getMMImage(image, logger);

        // get the image label and position name
        String positionName = mmimage.getMetadata().getPositionName(Integer.toString(mmimage.getCoords().getStagePosition()));
        String imageLabel = image.getLabel();
        String label = String.format("%s (%s)", positionName, imageLabel); 
        
        logger.fine(String.format("%s: Using image: %s", label, image));
        logger.fine(String.format("%s: Using image ID: %d", label, imageId));
        
        Dao<Slide> slideDao = workflowRunner.getWorkflowDb().table(Slide.class);
        Slide slide = slideDao.selectOneOrDie(where("id",image.getSlideId()));
        logger.fine(String.format("%s: Using slide: %s", label, slide));

        return process(image, mmimage, logger, new ImageLog.NullImageLogRunner(), config);
    }
    
    public org.micromanager.data.Image getMMImage(Image image, Logger logger) {
        String label = image.getLabel();

        // Initialize the acquisition
        Dao<Acquisition> acqDao = workflowRunner.getWorkflowDb().table(Acquisition.class);
        Acquisition acquisition = acqDao.selectOneOrDie(where("id",image.getAcquisitionId()));
        logger.fine(String.format("%s: Using acquisition: %s", label, acquisition));
        Datastore datastore = acquisition.getDatastore();

        // Get the datastore object
        if (datastore == null) throw new RuntimeException("Acquisition was not initialized; imageCache is null!");
        Set<String> imageLabels = new TreeSet<>();
        for (Coords coords: datastore.getUnorderedImageCoords()) {
        	imageLabels.add(Image.generateLabel(coords));
        }
        logger.fine(String.format("%s: Datastore has following images: %s", label, imageLabels));
        logger.fine(String.format("%s: Attempting to grab image %s from datastore", label, image));
        // Get the tagged image from the image cache
        org.micromanager.data.Image mmimage = image.getImage(datastore);
        if (mmimage == null) throw new RuntimeException(String.format("Acqusition %s, Image %s is not in the datastore!",
                acquisition, image));
        logger.fine(String.format("%s: Got mmimage from ImageCache: %s", label, mmimage));

        return mmimage;
    }
    
    /**
     * The process() method must be overridden by a subclass. This method holds the logic for ROI finding. It takes an image
     * and returns a list of ROIs.
     * @param image The input image DB record.
     * @param taggedImage The taggedImage object
     * @param logger A logger object
     * @param imageLog An image logger object
     * @param config A set of configuration key/value pairs
     * @return The list of ROI records.
     */
    public abstract Status process(
            Image image, 
            org.micromanager.data.Image mmimage, 
            Logger logger, 
            ImageLogRunner imageLog, 
            Map<String,Config> config);
    
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
                return configs.toArray(new Config[0]);
            }
            @Override
            public Component display(Config[] configs) {
                return null;
            }
            @Override
            public ValidationError[] validate() {
            	List<ValidationError> errors = new ArrayList<ValidationError>();
                return errors.toArray(new ValidationError[0]);
            }
        };
    }

    @Override
    public List<Task> createTaskRecords(List<Task> parentTasks, Map<String,Config> config, Logger logger) {
        WorkflowModule module = workflowRunner.getWorkflow().selectOneOrDie(where("id",workflowModule.getId()));
        List<Task> tasks = new ArrayList<Task>();
        if (module.getParentId() != null) {
            for (Task parentTask : parentTasks) {
            	workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: attaching to parent Task: %s",
            			this.workflowModule.getId(), parentTask));
                Task task = new Task(workflowModule.getId(), Status.NEW);
                workflowRunner.getTaskStatus().insert(task);
                tasks.add(task);
            	workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Created new task record: %s",
            			this.workflowModule.getId(), task));
                
                TaskDispatch dispatch = new TaskDispatch(task.getId(), parentTask.getId());
                workflowRunner.getTaskDispatch().insert(dispatch);
            	workflowRunner.getLogger().fine(String.format("%s: createTaskRecords: Created new task dispatch record: %s",
            			this.workflowModule.getId(), dispatch));
            }
        }
        return tasks;
    }

	@Override
	public TaskType getTaskType() {
		return Module.TaskType.PARALLEL;
	}

	@Override public void cleanup(Task task, Map<String,Config> config, Logger logger) { }

    @Override
    public List<ImageLogRecord> logImages(final Task task, final Map<String,Config> config, final Logger logger) {
        List<ImageLogRecord> imageLogRecords = new ArrayList<ImageLogRecord>();

        // Add an image logger instance to the workflow runner for this module
        imageLogRecords.add(new ImageLogRecord(task.getName(this.workflowRunner.getWorkflow()), task.getName(this.workflowRunner.getWorkflow()),
                new FutureTask<ImageLogRunner>(new Callable<ImageLogRunner>() {
            @Override public ImageLogRunner call() throws Exception {
                // Get the Image record
                Config imageIdConf = config.get("imageId");
                if (imageIdConf != null) {
                    Integer imageId = Integer.parseInt(imageIdConf.getValue());

                    logger.info(String.format("Using image ID: %d", imageId));
                    Dao<Image> imageDao = workflowRunner.getWorkflowDb().table(Image.class);
                    final Image image = imageDao.selectOneOrDie(where("id",imageId));
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
                    logger.info(String.format("Got mmimage from Datastore: %s", mmimage));

                    ImageLogRunner imageLogRunner = new ImageLogRunner(task.getName(workflowRunner.getWorkflow()));
                    PostProcessor.this.process(image, mmimage, logger, imageLogRunner, config);
                    return imageLogRunner;
                }
                return null;
            }
        })));
        return imageLogRecords;
    }

    @Override
    public void runInitialize() { }
}
