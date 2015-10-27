package org.bdgp.OpenHiCAMM.Modules;

import java.awt.Component;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import javax.swing.JPanel;

import org.bdgp.OpenHiCAMM.Dao;
import org.bdgp.OpenHiCAMM.ImageLog;
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
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.Task.Status;
import org.bdgp.OpenHiCAMM.DB.TaskConfig;
import org.bdgp.OpenHiCAMM.DB.TaskDispatch;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Configuration;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.ImageLogger;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Module;
import org.json.JSONArray;
import org.json.JSONException;
import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.api.ImageCache;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.io.FileSaver;
import ij.macro.Interpreter;
import ij.process.ImageProcessor;
import mmcorej.TaggedImage;

import static org.bdgp.OpenHiCAMM.Util.where;

public class ImageStitcher implements Module, ImageLogger {
    private static final String FUSION_METHOD = "Linear Blending";
    private static final int CHECK_PEAKS = 5;
    private static final boolean COMPUTE_OVERLAP = true;
    private static final String REGISTRATION_CHANNEL_IMAGE_1 = "Average all channels";
    private static final String REGISTRATION_CHANNEL_IMAGE_2 = "Average all channels";
    private static final double OVERLAP_WIDTH = 0.25;
    private static final double OVERLAP_HEIGHT = 0.25;
    private static final String STITCHED_IMAGE_DIRECTORY_PREFIX = "stitched";
    private static final boolean DEBUG_MODE = true;

    WorkflowRunner workflowRunner;
    String moduleId;

    @Override
    public void initialize(WorkflowRunner workflowRunner, String moduleId) {
        this.workflowRunner = workflowRunner;
        this.moduleId = moduleId;

        // set initial configs
        workflowRunner.getModuleConfig().insertOrUpdate(
                new ModuleConfig(this.moduleId, "canStitchImages", "yes"), 
                "id", "key");
    }
    
    public static class TaskTile {
        public Task task;
        public Map<String,TaskConfig> taskConfig;
        public int tileX;
        public int tileY;
        public ImagePlus image;
        public TaskTile(Task task, Map<String,TaskConfig> taskConfig, int tileX, int tileY, ImagePlus image) {
            this.task = task;
            this.taskConfig = taskConfig;
            this.tileX = tileX;
            this.tileY = tileY;
            this.image = image;
        }
    }
    
    private void showImageTable(String message, Logger logger) {
        List<String> imageTitles = new ArrayList<String>();
        for (int id : Interpreter.getBatchModeImageIDs()) {
            ImagePlus image = Interpreter.getBatchModeImage(id);
            if (image != null) imageTitles.add(Util.escape(image.getTitle()));
        }
        String imageTitleList = Util.join(", ", imageTitles);
        if (DEBUG_MODE) {
            logger.info(String.format(
                    "%s: Image Table (%d count): [%s]", message, imageTitles.size(), imageTitleList));
        }
    }

    @Override
    public Status run(Task task, Map<String,Config> config, Logger logger) {
    	Dao<TaskConfig> taskConfigDao = this.workflowRunner.getTaskConfig();
    	logger.fine(String.format("Running task %s: %s", task.getName(), task));
    	
    	// get the stitch group name
    	Config stitchGroupConf = config.get("stitchGroup");
    	if (stitchGroupConf == null) throw new RuntimeException(String.format(
    	        "%s: stitchGroup config not found!", task.getName()));
    	String stitchGroup = stitchGroupConf.getValue();
    	logger.fine(String.format("Stitching group: %s", stitchGroup));

    	// Run the stitching processing to produce a stitched image.
    	this.showImageTable("Before process()", logger);
    	StitchResult stitchResult = process(task, config, logger, new ImageLog.NullImageLogRunner());
    	this.showImageTable("After process()", logger);

    	// get the stitched folder
    	Config stitchedFolderConf = config.get("stitchedFolder");
    	if (stitchedFolderConf == null) throw new RuntimeException(String.format(
    	        "%s: stitchedFolder config not found!", task.getName()));
    	File stitchedFolder = new File(stitchedFolderConf.getValue());
    	logger.fine(String.format("Stitched folder: %s", stitchedFolder));
    	stitchedFolder.mkdirs();
    	
    	// save the input images to the stitch group folder
    	if (DEBUG_MODE) {
            for (ImagePlus input : stitchResult.inputs) {
                FileSaver fileSaver = new FileSaver(input);
                File imageFile = new File(stitchedFolder, String.format("%s.tif", input.getTitle()));
                fileSaver.saveAsTiff(imageFile.getPath());
            }
    	}
    	
    	// save the macro invocations
    	if (stitchResult.macroInvocations.size() > 0) {
            File macroInvocations = new File(stitchedFolder, "macro_invocations.txt");
            try {
                PrintWriter pw = new PrintWriter(macroInvocations.getPath());
                for (String macroInvocation : stitchResult.macroInvocations) {
                    pw.println(macroInvocation);
                }
                pw.close();
            } 
            catch (FileNotFoundException e) {throw new RuntimeException(e);}
    	}
    	
        // save the stitched image to the stitched folder using the stitch group as the 
        // file name.
        FileSaver fileSaver = new FileSaver(stitchResult.result);
        File imageFile = new File(stitchedFolder, String.format("%s.tif", stitchGroup));
        fileSaver.saveAsTiff(imageFile.getPath());
        
        // write a task configuration for the stitched image location
        TaskConfig imageFileConf = new TaskConfig(new Integer(task.getId()).toString(), 
                "stitchedImageFile", imageFile.getPath());
        taskConfigDao.insertOrUpdate(imageFileConf, "id", "key");
    	
        return Status.SUCCESS;
    }
    
    public static class StitchResult {
        public List<ImagePlus> inputs;
        public List<String> macroInvocations;
        public ImagePlus result;
        public StitchResult() {
            this.inputs = new ArrayList<ImagePlus>();
            this.macroInvocations = new ArrayList<String>();
            this.result = null;
        }
    }
    
    public StitchResult process(Task task, Map<String,Config> config, Logger logger, ImageLogRunner imageLogRunner) {
    	// get the stitch group name
    	Config stitchGroupConf = config.get("stitchGroup");
    	if (stitchGroupConf == null) throw new RuntimeException(String.format(
    	        "%s: stitchGroup config not found!", task.getName()));
    	String stitchGroup = stitchGroupConf.getValue();
    	logger.fine(String.format("Stitching group: %s", stitchGroup));
    	
    	// get the list of stitch task IDs
    	Config stitchTaskIdsConf = config.get("stitchTaskIds");
    	if (stitchTaskIdsConf == null) throw new RuntimeException(String.format(
    	        "%s: stitchTaskIds config not found!", task.getName()));
    	// get the list of stitch task IDs from the JSON array
    	List<Integer> stitchTaskIds = new ArrayList<Integer>();
    	try {
            JSONArray stitchTaskJson = new JSONArray(stitchTaskIdsConf.getValue());
            for (int i=0; i<stitchTaskJson.length(); ++i) {
                Integer stitchTaskId = stitchTaskJson.getInt(i);
                stitchTaskIds.add(stitchTaskId);
            }
    	}
    	catch (JSONException e) {throw new RuntimeException(e);}

    	Dao<Image> imageDao = this.workflowRunner.getInstanceDb().table(Image.class);
        Dao<Acquisition> acqDao = workflowRunner.getInstanceDb().table(Acquisition.class);

        // Organize the tasks into a grid of TaskTiles
        Integer gridWidth = null;
        Integer gridHeight = null;
        List<TaskTile> taskTiles = new ArrayList<TaskTile>();
        Integer frame = null;
        Integer channel = null;
        Integer slice = null;
        for (Integer stitchTaskId : stitchTaskIds) {
            Task stitchTask = this.workflowRunner.getTaskStatus().selectOne(where("id", stitchTaskId));
            if (stitchTask == null) throw new RuntimeException(String.format(
                    "%s: Stitch task with ID %d not found in database!", 
                    task.getName(), stitchTaskId));
            
            // get the task config for each stitch task
            List<TaskConfig> taskConfigs = this.workflowRunner.getTaskConfig().select(where("id", stitchTask.getId()));
            Map<String,TaskConfig> taskConfig = new HashMap<String,TaskConfig>();
            for (TaskConfig tc : taskConfigs) {
                taskConfig.put(tc.getKey(), tc);
            }
            // get the tileX and tileY, figure out gridWidth and gridHeight
            TaskConfig tileXConf = taskConfig.get("tileX");
            if (tileXConf == null) throw new RuntimeException(String.format("Task %s: tileX is null!", stitchTask));
            Integer tileX = new Integer(tileXConf.getValue());
            TaskConfig tileYConf = taskConfig.get("tileY");
            if (tileYConf == null) throw new RuntimeException(String.format("Task %s: tileY is null!", stitchTask));
            Integer tileY = new Integer(tileYConf.getValue());
            if (gridWidth == null || gridWidth < tileX+1) gridWidth = tileX+1;
            if (gridHeight == null || gridHeight < tileY+1) gridHeight = tileY+1;

            // get the image ID
            TaskConfig imageId = taskConfig.get("imageId");
            if (imageId == null) throw new RuntimeException(String.format("Task %s: imageId is null!", stitchTask));
            // get the Image record
            Image image = imageDao.selectOneOrDie(where("id", new Integer(imageId.getValue())));
            if (frame == null) frame = image.getFrame();
            if (channel == null) channel = image.getChannel();
            if (slice == null) slice = image.getSlice();

            // Initialize the acquisition
            Acquisition acquisition = acqDao.selectOneOrDie(where("id",image.getAcquisitionId()));
            MMAcquisition mmacquisition = acquisition.getAcquisition(acqDao);
            // Get the image cache object
            ImageCache imageCache = mmacquisition.getImageCache();
            if (imageCache == null) throw new RuntimeException("Acquisition was not initialized; imageCache is null!");
            // Get the tagged image from the image cache
            TaggedImage taggedImage = image.getImage(imageCache);
            if (taggedImage == null) throw new RuntimeException(String.format("Acqusition %s, Image %s is not in the image cache!",
                    acquisition, image));
            // convert the tagged image into an ImagePlus object
            ImageProcessor processor = ImageUtils.makeProcessor(taggedImage);
            ImagePlus imp = new ImagePlus(image.getName(), processor);
            imageLogRunner.addImage(imp, imp.getTitle());

            // create the TaskTile object
            TaskTile taskTile = new TaskTile(task, taskConfig, tileX, tileY, imp);
            taskTiles.add(taskTile);
        }
        TaskTile[][] taskGrid = new TaskTile[gridHeight][gridWidth];
        for (TaskTile taskTile : taskTiles) {
            taskGrid[taskTile.tileY][taskTile.tileX] = taskTile;
        }
        
        // create a StitchResult to hold the results of stitching
        // add the input images to the stitch result
        StitchResult stitchResult = new StitchResult();
        for (TaskTile taskTile : taskTiles) {
            stitchResult.inputs.add(taskTile.image);
        }

        // stitch the images into a single tagged image
        ImagePlus stitchedImage = stitchGrid(taskGrid, logger, stitchResult);
        imageLogRunner.addImage(stitchedImage, stitchedImage.getTitle());
        stitchResult.result = stitchedImage;

        // If a window is open, close it.
        stitchedImage.changes = false;
        stitchedImage.close();

        return stitchResult;
    }
    
    public ImagePlus stitchGrid(TaskTile[][] taskGrid, Logger logger, StitchResult stitchResult) {
        ImagePlus image1;
        ImagePlus image2;
        if (taskGrid.length > 1 || (taskGrid.length > 0 && taskGrid[0].length > 1)) {
            TaskTile[][] grid1;
            TaskTile[][] grid2;
            // vertical split
            if (taskGrid.length < taskGrid[0].length) {
                int splitPoint = taskGrid[0].length / 2;
                grid1 = new TaskTile[taskGrid.length][splitPoint];
                for (int r=0; r<taskGrid.length; ++r) {
                    for (int c=0; c<splitPoint; ++c) {
                        grid1[r][c] = taskGrid[r][c];
                    }
                }
                grid2 = new TaskTile[taskGrid.length][taskGrid[0].length-splitPoint];
                for (int r=0; r<taskGrid.length; ++r) {
                    for (int c=splitPoint; c<taskGrid[0].length; ++c) {
                        grid2[r][c-splitPoint] = taskGrid[r][c];
                    }
                }
                image1 = stitchGrid(grid1, logger, stitchResult);
                image2 = stitchGrid(grid2, logger, stitchResult);
                return stitchImages(image1, image2, false, logger, stitchResult);
            }
            // horizontal split
            else {
                int splitPoint = taskGrid.length / 2;
                grid1 = new TaskTile[splitPoint][taskGrid[0].length];
                for (int r=0; r<splitPoint; ++r) {
                    for (int c=0; c<taskGrid[0].length; ++c) {
                        grid1[r][c] = taskGrid[r][c];
                    }
                }
                grid2 = new TaskTile[taskGrid.length-splitPoint][taskGrid[0].length];
                for (int r=splitPoint; r<taskGrid.length; ++r) {
                    for (int c=0; c<taskGrid[0].length; ++c) {
                        grid2[r-splitPoint][c] = taskGrid[r][c];
                    }
                }
                image1 = stitchGrid(grid1, logger, stitchResult);
                image2 = stitchGrid(grid2, logger, stitchResult);
                return stitchImages(image1, image2, true, logger, stitchResult);
            }
        }
        else if (taskGrid.length > 1) {
            image1 = taskGrid[0][0].image;
            image2 = taskGrid[1][0].image;
            return stitchImages(image1, image2, true, logger, stitchResult);
        }
        else  if (taskGrid.length > 0 && taskGrid[0].length > 1) {
            image1 = taskGrid[0][0].image;
            image2 = taskGrid[0][1].image;
            return stitchImages(image1, image2, false, logger, stitchResult);
        }
        else if (taskGrid.length == 1 && taskGrid[0].length == 1) {
            return taskGrid[0][0].image;
        }
        else {
            throw new RuntimeException("Empty TaskTile array passed to stitchGrid!");
        }
    }
    
    public ImagePlus stitchImages(
            ImagePlus image1, 
            ImagePlus image2, 
            boolean isVerticallyAligned,
            Logger logger,
            StitchResult stitchResult) 
    {
        String fusion_method = FUSION_METHOD;
        int check_peaks = CHECK_PEAKS;
        boolean compute_overlap = COMPUTE_OVERLAP;
        double overlapWidth = OVERLAP_WIDTH;
        double overlapHeight = OVERLAP_HEIGHT;
        double x = isVerticallyAligned? 0.0 : image1.getWidth() * (1.0 - overlapWidth);
        double y = isVerticallyAligned? image1.getHeight() * (1.0 - overlapHeight) : 0.0;
        String registration_channel_image_1 = REGISTRATION_CHANNEL_IMAGE_1;
        String registration_channel_image_2 = REGISTRATION_CHANNEL_IMAGE_2;
        
        // open the input images
        this.showImageTable("Before show()", logger);
        image1.show();
        image2.show();
        this.showImageTable("After show()", logger);

        // run the pairwise stitching plugin
        String fusedImageTitle = String.format("%s<->%s", image1.getTitle(), image2.getTitle());
        String params = Util.join(" ", 
                String.format("first_image=%s", Util.macroEscape(image1.getTitle())),
                String.format("second_image=%s", Util.macroEscape(image2.getTitle())),
                String.format("fusion_method=%s", Util.macroEscape(fusion_method)),
                String.format("fused_image=%s", Util.macroEscape(fusedImageTitle)),
                String.format("check_peaks=%d", check_peaks),
                compute_overlap? "compute_overlap" : null,
                String.format("x=%.4f", x),
                String.format("y=%.4f", y),
                String.format("registration_channel_image_1=%s", Util.macroEscape(registration_channel_image_1)),
                String.format("registration_channel_image_2=%s", Util.macroEscape(registration_channel_image_2)));
        logger.fine(String.format("Running pairwise stitching with params: %s", params));
        stitchResult.macroInvocations.add(String.format("IJ.run(\"Pairwise stitching\", %s);", Util.escape(params)));
        IJ.run(image1, "Pairwise stitching", params);
        
        // Close the input images.
        // The pairwise stitching module modifies then input images in-place, so
        // closing them won't remove them from the image table. We have to find
        // the modified images with the same title, and close those instead.
        this.showImageTable("Before close()", logger);
        ImagePlus modifiedImage1 = WindowManager.getImage(image1.getTitle());
        if (modifiedImage1 == null) throw new RuntimeException(String.format(
                "Pairwise Stitching: could not find modified input image 1 with title: %s", image1.getTitle()));
        modifiedImage1.changes = false;
        modifiedImage1.close();
        ImagePlus modifiedImage2 = WindowManager.getImage(image2.getTitle());
        if (modifiedImage2 == null) throw new RuntimeException(String.format(
                "Pairwise Stitching: could not find modified input image 2 with title: %s", image2.getTitle()));
        modifiedImage2.changes = false;
        modifiedImage2.close();
        this.showImageTable("After close()", logger);
        
        // get the fused image
        ImagePlus fusedImage = WindowManager.getImage(fusedImageTitle);
        if (fusedImage == null) throw new RuntimeException(String.format(
                "Pairwise Stitching: could not find fused image with title: %s", fusedImageTitle));
        
        // convert stack to RGB
        if (fusedImage.getNChannels() == 3) {
            IJ.run(fusedImage, "Stack to RGB", "");
            String rgbImageTitle = String.format("%s (RGB)", fusedImage.getTitle());
            ImagePlus rgbImage = WindowManager.getImage(rgbImageTitle);
            if (rgbImage == null) throw new RuntimeException(String.format(
                    "Pairwise Stitching: could not find RGB image with title: %s", rgbImageTitle));

            // close the separate-channel fused image
            fusedImage.changes = false;
            fusedImage.close();

            // return the RGB image
            rgbImage.changes = false;
            rgbImage.close();
            return rgbImage;
        }

        // close the separate-channel fused image
        fusedImage.changes = false;
        fusedImage.close();
        return fusedImage;
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
                return new Config[0];
            }
            @Override
            public Component display(Config[] configs) {
                return new JPanel();
            }
            @Override
            public ValidationError[] validate() {
                return null;
            }
        };
    }

    @Override
    public List<Task> createTaskRecords(List<Task> parentTasks, Map<String,Config> config, Logger logger) {
        Dao<Task> taskDao = this.workflowRunner.getTaskStatus();
        Dao<TaskConfig> taskConfigDao = this.workflowRunner.getTaskConfig();
        Dao<TaskDispatch> taskDispatchDao = this.workflowRunner.getTaskDispatch();

        // Group the parent tasks by stitchGroup name
        Map<String,List<Task>> stitchGroups = new LinkedHashMap<String,List<Task>>();
        for (Task parentTask : parentTasks) {
            TaskConfig stitchGroup = taskConfigDao.selectOne(where("id", parentTask.getId()).and("key", "stitchGroup"));
            if (stitchGroup != null) {
                // get the stitch group label: "stitch_group.channel_slice_frame"
                TaskConfig imageLabel = taskConfigDao.selectOneOrDie(where("id", parentTask.getId()).and("key", "imageLabel"));
                int[] indices = MDUtils.getIndices(imageLabel.getValue());
                if (indices == null || indices.length < 4) throw new RuntimeException(String.format(
                        "invalid indices in image label: %s", Util.escape(indices)));
                int channel = indices[0];
                int slice = indices[1];
                int frame = indices[2];
                String stitchGroupLabel = String.format("%s.%d_%d_%d", stitchGroup.getValue(), channel, slice, frame);

                // make sure tileX and tileY are set
                TaskConfig tileX = taskConfigDao.selectOne(where("id", parentTask.getId()).and("key", "tileX"));
                TaskConfig tileY = taskConfigDao.selectOne(where("id", parentTask.getId()).and("key", "tileY"));
                if (tileX == null || tileY == null) {
                    throw new RuntimeException(String.format("tileX and tileY not defined for task: %s", parentTask));
                }
                // add the task to the stitch group
                if (!stitchGroups.containsKey(stitchGroupLabel)) {
                    stitchGroups.put(stitchGroupLabel, new ArrayList<Task>());
                }
                stitchGroups.get(stitchGroupLabel).add(parentTask);
            }
        }

        // create a folder to store the stitched images
        File stitchedFolder = createStitchedImageFolder();

        List<Task> tasks = new ArrayList<Task>();
        for (Map.Entry<String, List<Task>> entry : stitchGroups.entrySet()) {
            String stitchGroup = entry.getKey();
            List<Task> stitchTasks = entry.getValue();
            File stitchedGroupFolder = new File(stitchedFolder, stitchGroup);
            
            // insert the task record
            Task task = new Task(this.moduleId, Status.NEW);
            taskDao.insert(task);
            tasks.add(task);
            
            // add the stitchedFolder task config
            TaskConfig stitchedFolderConf = new TaskConfig(
                    new Integer(task.getId()).toString(), 
                    "stitchedFolder", stitchedGroupFolder.toString());
            taskConfigDao.insert(stitchedFolderConf);

            // add the stitchGroup task config
            TaskConfig stitchGroupConf = new TaskConfig(
                    new Integer(task.getId()).toString(), 
                    "stitchGroup", stitchGroup);
            taskConfigDao.insert(stitchGroupConf);

            // add the stitchTaskIds task config, a JSONArray of stitch task IDs.
            JSONArray stitchTaskIds = new JSONArray();
            for (Task stitchTask : stitchTasks) {
                stitchTaskIds.put(stitchTask.getId());
            }
            TaskConfig stitchTaskIdsConf = new TaskConfig(
                    new Integer(task.getId()).toString(), 
                    "stitchTaskIds", stitchTaskIds.toString());
            taskConfigDao.insert(stitchTaskIdsConf);
                
            // add the task dispatch records
            for (Task stitchTask : stitchTasks) {
                TaskDispatch td = new TaskDispatch(task.getId(), stitchTask.getId());
                taskDispatchDao.insert(td);
            }
        }
        
        return tasks;
    }
    
    private File createStitchedImageFolder() {
        String rootDir = new File(
                this.workflowRunner.getWorkflowDir(), 
                this.workflowRunner.getInstance().getStorageLocation()).getPath();
        int count = 1;
        File stitchedFolder = new File(rootDir, String.format("%s_%d", STITCHED_IMAGE_DIRECTORY_PREFIX, count));
        while (!stitchedFolder.mkdirs()) {
            ++count;
            stitchedFolder = new File(rootDir, String.format("%s_%d", STITCHED_IMAGE_DIRECTORY_PREFIX, count));
        }
        return stitchedFolder;
    }

	@Override
	public TaskType getTaskType() {
		return Module.TaskType.PARALLEL;
	}

	@Override public void cleanup(Task task, Map<String,Config> config, Logger logger) { }

    @Override
    public void runIntialize() { }

    @Override
    public List<ImageLogRecord> logImages(final Task task, final Map<String,Config> config, final Logger logger) {
        List<ImageLogRecord> imageLogRecords = new ArrayList<ImageLogRecord>();

        // Add an image logger instance to the workflow runner for this module
        imageLogRecords.add(new ImageLogRecord(task.getName(), task.getName(),
                new FutureTask<ImageLogRunner>(new Callable<ImageLogRunner>() {
            @Override public ImageLogRunner call() throws Exception {
                ImageLogRunner imageLogRunner = new ImageLogRunner(task.getName());
                ImageStitcher.this.process(task, config, logger, imageLogRunner);
                return imageLogRunner;
            }
        })));
        return imageLogRecords;
    }

}
