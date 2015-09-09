package org.bdgp.OpenHiCAMM.Modules;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JPanel;

import org.bdgp.OpenHiCAMM.Dao;
import org.bdgp.OpenHiCAMM.Logger;
import org.bdgp.OpenHiCAMM.Util;
import org.bdgp.OpenHiCAMM.ValidationError;
import org.bdgp.OpenHiCAMM.WorkflowRunner;
import org.bdgp.OpenHiCAMM.DB.Acquisition;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.Image;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.Task.Status;
import org.bdgp.OpenHiCAMM.DB.TaskConfig;
import org.bdgp.OpenHiCAMM.DB.TaskDispatch;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Configuration;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Module;
import org.json.JSONArray;
import org.json.JSONException;
import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.api.ImageCache;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import mmcorej.TaggedImage;

import static org.bdgp.OpenHiCAMM.Util.where;

public class ImageStitcher implements Module {
    private static final String FUSION_METHOD = "Linear Blending";
    private static final int CHECK_PEAKS = 5;
    private static final boolean COMPUTE_OVERLAP = true;
    private static final String REGISTRATION_CHANNEL_IMAGE_1 = "Average all channels";
    private static final String REGISTRATION_CHANNEL_IMAGE_2 = "Average all channels";
    private static final double OVERLAP_WIDTH = 0.25;
    private static final double OVERLAP_HEIGHT = 0.25;
    private static final String TEMP_FILE_PREFIX = ".ImageStitcher.temp.";
    private static final String STITCHED_IMAGE_DIRECTORY_PREFIX = "stitched";

    WorkflowRunner workflowRunner;
    String moduleId;

    @Override
    public void initialize(WorkflowRunner workflowRunner, String moduleId) {
        this.workflowRunner = workflowRunner;
        this.moduleId = moduleId;
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
        }
    }

    @Override
    public Status run(Task task, Map<String,Config> config, Logger logger) {
    	logger.fine(String.format("Running task %s: %s", task.getName(), task));
    	
    	// get the stitch group name
    	Config stitchGroupConf = config.get("stitchGroup");
    	if (stitchGroupConf == null) throw new RuntimeException(String.format(
    	        "%s: stitchGroup config not found!", task.getName()));
    	String stitchGroup = stitchGroupConf.getValue();
    	logger.fine(String.format("Stitching group: %s", stitchGroup));
    	
    	// get the position index
    	Config positionIndexConf = config.get("positionIndex");
    	if (positionIndexConf == null) throw new RuntimeException(String.format(
    	        "%s: positionIndex config not found!", task.getName()));
    	Integer positionIndex = new Integer(stitchGroupConf.getValue());
    	logger.fine(String.format("Stitching position index: %s", positionIndex));
    	
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
            if (gridWidth == null || gridWidth < tileX) gridWidth = tileX;
            if (gridHeight == null || gridHeight < tileY) gridHeight = tileX;

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
            ImagePlus imp = new ImagePlus(image.toString(), processor);

            // create the TaskTile object
            TaskTile taskTile = new TaskTile(task, taskConfig, tileX, tileY, imp);
            taskTiles.add(taskTile);
        }
        TaskTile[][] taskGrid = new TaskTile[gridWidth][gridHeight];
        for (TaskTile taskTile : taskTiles) {
            taskGrid[taskTile.tileY][taskTile.tileX] = taskTile;
        }
        
        // Create a fake acquisition directory to store the stitched images
        List<File> tempImages = new ArrayList<File>();
        String rootDir = new File(
                this.workflowRunner.getWorkflowDir(), 
                this.workflowRunner.getInstance().getStorageLocation()).getPath();
        try {
            MMAcquisition stitchedAcquisition = new MMAcquisition(STITCHED_IMAGE_DIRECTORY_PREFIX, rootDir, false, true, false);
            stitchedAcquisition.initialize();

            ImagePlus stitchedImage = stitchGrid(taskGrid, stitchedAcquisition.getImageCache().getDiskLocation(), tempImages);
            TaggedImage taggedImage = ImageUtils.makeTaggedImage(stitchedImage.getProcessor());
            MDUtils.setPositionName(taggedImage.tags, stitchGroup);
            stitchedAcquisition.insertImage(taggedImage, frame, channel, slice, positionIndex);
        } 
        catch (MMScriptException e) {throw new RuntimeException(e);} 
        catch (JSONException e) {throw new RuntimeException(e);}
        finally {
            // delete the intermediate temporary image files
            for (File tempImage : tempImages) {
                tempImage.delete();
            }
        }
    	
        return Status.SUCCESS;
    }
    
    public ImagePlus stitchGrid(TaskTile[][] taskGrid, String stitchedImageDirectory, List<File> tempImages) {
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
                        grid1[r][c-splitPoint] = taskGrid[r][c];
                    }
                }
                image1 = stitchGrid(grid1, stitchedImageDirectory, tempImages);
                image2 = stitchGrid(grid2, stitchedImageDirectory, tempImages);
                return stitchImages(image1, image2, false, stitchedImageDirectory, tempImages);
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
                        grid1[r-splitPoint][c] = taskGrid[r][c];
                    }
                }
                image1 = stitchGrid(grid1, stitchedImageDirectory, tempImages);
                image2 = stitchGrid(grid2, stitchedImageDirectory, tempImages);
                return stitchImages(image1, image2, true, stitchedImageDirectory, tempImages);
            }
        }
        else if (taskGrid.length > 1) {
            image1 = taskGrid[0][0].image;
            image2 = taskGrid[1][0].image;
            return stitchImages(image1, image2, true, stitchedImageDirectory, tempImages);
        }
        else  if (taskGrid.length > 0 && taskGrid[0].length > 1) {
            image1 = taskGrid[0][0].image;
            image2 = taskGrid[0][1].image;
            return stitchImages(image1, image2, false, stitchedImageDirectory, tempImages);
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
            String stitchedImageDirectory, 
            List<File> tempImages) 
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

        // Store the fused image to a temporary file
        File fused_image;
        try { fused_image = File.createTempFile(TEMP_FILE_PREFIX, ".tif", new File(stitchedImageDirectory)); } 
        catch (IOException e) {throw new RuntimeException(e);}
        tempImages.add(fused_image);
        fused_image.deleteOnExit();

        File first_image = new File(image1.getFileInfo().directory, image1.getFileInfo().fileName);
        File second_image = new File(image2.getFileInfo().directory, image2.getFileInfo().fileName);
        String params = Util.join(" ", 
                String.format("first_image=%s", Util.macroEscape(first_image)),
                String.format("second_image=%s", Util.macroEscape(second_image)),
                String.format("fusion_method=%s", Util.macroEscape(fusion_method)),
                String.format("fused_image=%s", Util.escape(fused_image)),
                String.format("check_peaks=%d", check_peaks),
                compute_overlap? "compute_overlap" : null,
                String.format("x=%.4f", x),
                String.format("y=%.4f", y),
                String.format("registration_channel_image_1=%s", Util.macroEscape(registration_channel_image_1)),
                String.format("registration_channel_image_2=%s", Util.macroEscape(registration_channel_image_2)));
        IJ.run(image1, "Pairwise stitching", params);
        
        // Load the image file into an ImagePlus object and return it
        ImagePlus fusedImage = new ImagePlus(fused_image.getPath());
        
        // delete the temp images once they're no longer used.
        if (image1.getFileInfo().fileName.startsWith(TEMP_FILE_PREFIX)) {
            first_image.delete();
        }
        if (image2.getFileInfo().fileName.startsWith(TEMP_FILE_PREFIX)) {
            second_image.delete();
        }
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
    public List<Task> createTaskRecords(List<Task> parentTasks) {
        Dao<Task> taskDao = this.workflowRunner.getTaskStatus();
        Dao<TaskConfig> taskConfigDao = this.workflowRunner.getTaskConfig();
        Dao<TaskDispatch> taskDispatchDao = this.workflowRunner.getTaskDispatch();

        // Group the parent tasks by stitchGroup name
        Map<String,List<Task>> stitchGroups = new LinkedHashMap<String,List<Task>>();
        for (Task parentTask : parentTasks) {
            TaskConfig stitchGroup = taskConfigDao.selectOne(where("id", parentTask.getId()).and("key", "stitchGroup"));
            if (stitchGroup != null) {
                // make sure tileX and tileY are set
                TaskConfig tileX = taskConfigDao.selectOne(where("id", parentTask.getId()).and("key", "tileX"));
                TaskConfig tileY = taskConfigDao.selectOne(where("id", parentTask.getId()).and("key", "tileY"));
                if (tileX == null || tileY == null) {
                    throw new RuntimeException(String.format("tileX and tileY not defined for task: %s", parentTask));
                }
                // add the task to the stitch group
                if (!stitchGroups.containsKey(stitchGroup.getValue())) {
                    stitchGroups.put(stitchGroup.getValue(), new ArrayList<Task>());
                }
                stitchGroups.get(stitchGroup.getValue()).add(parentTask);
            }
        }

        int positionIndex = 0;
        List<Task> tasks = new ArrayList<Task>();
        for (Map.Entry<String, List<Task>> entry : stitchGroups.entrySet()) {
            String stitchGroup = entry.getKey();
            List<Task> stitchTasks = entry.getValue();
            
            // insert the task record
            Task task = new Task(this.moduleId, Status.NEW);
            taskDao.insert(task);
            tasks.add(task);
            
            // add the stitchGroup task config
            TaskConfig stitchGroupConf = new TaskConfig(
                    new Integer(task.getId()).toString(), 
                    "stitchGroup", stitchGroup);
            taskConfigDao.insert(stitchGroupConf);

            // add a configuration for the positionIndex of the stitch group in the 
            // acquisition folder we will be creating.
            TaskConfig positionIndexConf = new TaskConfig(
                    new Integer(task.getId()).toString(), 
                    "positionIndex", new Integer(positionIndex).toString());
            taskConfigDao.insert(positionIndexConf);
            ++positionIndex;

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

	@Override
	public TaskType getTaskType() {
		return Module.TaskType.PARALLEL;
	}

	@Override public void cleanup(Task task) { }

    @Override
    public void runIntialize() { }
}
