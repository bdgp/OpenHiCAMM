package org.bdgp.OpenHiCAMM.Modules;

import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bdgp.OpenHiCAMM.ImageLog.ImageLogRunner;
import org.bdgp.OpenHiCAMM.Logger;
import org.bdgp.OpenHiCAMM.ValidationError;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.Image;
import org.bdgp.OpenHiCAMM.DB.ROI;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Configuration;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.ImageLogger;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Module;
import mmcorej.org.json.JSONException;
import org.micromanager.internal.utils.imageanalysis.ImageUtils;
import org.micromanager.internal.utils.MDUtils;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.process.ImageProcessor;
import mmcorej.TaggedImage;

import static org.bdgp.OpenHiCAMM.Util.where;

public class CustomMacroROIFinder extends ROIFinder implements Module, ImageLogger {

    public CustomMacroROIFinder() {
        super();
    }

    @Override
    public List<ROI> process(Image image, TaggedImage taggedImage,
            Logger logger, ImageLogRunner imageLog,
            Map<String, Config> config) 
    {
        // get the image label
        String positionName = null;
        try { positionName = MDUtils.getPositionName(taggedImage.tags); } 
        catch (JSONException e) {throw new RuntimeException(e);} 
        String imageLabel = image.getLabel();
        String label = String.format("%s (%s)", positionName, imageLabel); 

        Config roiImageScaleFactorConf = config.get("roiImageScaleFactor");
        if (roiImageScaleFactorConf == null) throw new RuntimeException("Config value roiImageScaleFactor not found!");
        Double roiImageScaleFactor = Double.parseDouble(roiImageScaleFactorConf.getValue());

        Config customMacroConf = config.get("customMacro");
        if (customMacroConf == null) throw new RuntimeException("Config value customMacro not found!");
        String customMacro = customMacroConf.getValue();

        ImageProcessor processor = ImageUtils.makeProcessor(taggedImage);
        ImagePlus imp = new ImagePlus(image.toString(), processor);
        if (roiImageScaleFactor != 1.0) {
            logger.fine(String.format("%s: Resizing", label));
            imp.getProcessor().setInterpolationMethod(ImageProcessor.BILINEAR);
            imp.setProcessor(imp.getTitle(), imp.getProcessor().resize(
                    (int)Math.floor(imp.getWidth() * roiImageScaleFactor), 
                    (int)Math.floor(imp.getHeight() * roiImageScaleFactor)));
        }
        imageLog.addImage(imp, "Resized");

        logger.info(String.format("Running custom macro macro:%n%s", customMacro));
        imp.show();
        IJ.runMacro(customMacro);
        ImagePlus modifiedImage1 = WindowManager.getImage(imp.getTitle());
        if (modifiedImage1 != null) {
            imp = modifiedImage1;
        }
        imageLog.addImage(imp, "Running macro");

        List<ROI> rois = new ArrayList<ROI>();
        ResultsTable rt = Analyzer.getResultsTable();

        // Get the objects and iterate through them
        logger.fine(String.format("ResultsTable Column Headings: %s", rt.getColumnHeadings()));
        for (int i=0; i < rt.getCounter(); i++) {
            double area = rt.getValue("Area", i) / (roiImageScaleFactor*roiImageScaleFactor); // area of the object
            double bx = rt.getValue("BX", i) / roiImageScaleFactor; // x of bounding box
            double by = rt.getValue("BY", i) / roiImageScaleFactor; // y of bounding box
            double width = rt.getValue("Width", i) / roiImageScaleFactor; // width of bounding box
            double height = rt.getValue("Height", i) / roiImageScaleFactor; // height of bounding box
            logger.finest(String.format(
                    "Found object: area=%.2f, bx=%.2f, by=%.2f, width=%.2f, height=%.2f",
                    area, bx, by, width, height));

            ROI roi = new ROI(image.getId(), 
                    (int)Math.floor(bx), 
                    (int)Math.floor(by), 
                    (int)Math.floor(bx+width), 
                    (int)Math.floor(by+height));
            rois.add(roi);
            // Draw the ROI rectangle
            try {
                imp.setRoi((int)Math.floor(rt.getValue("BX", i)), 
                        (int)Math.floor(rt.getValue("BY", i)), 
                        (int)Math.floor(rt.getValue("Width", i)), 
                        (int)Math.floor(rt.getValue("Height", i)));
                IJ.setForegroundColor(255, 255, 0);
                IJ.run(imp, "Draw", "");
            }
            catch (Throwable e) { 
                // Sometimes this fails, but it's not really important, so ignore it
                logger.warning(String.format("Couldn't draw the ROI rectangle!: %s", e.getMessage()));
            }
        }
        // close the image
        imageLog.addImage(imp, "Adding ROIs");
        return rois;
    }

    @Override
    public Configuration configure() {
        return new Configuration() {
            CustomMacroROIFinderDialog dialog = new CustomMacroROIFinderDialog(CustomMacroROIFinder.this);
            @Override
            public Config[] retrieve() {
            	List<Config> configs = new ArrayList<Config>();

            	Double hiResPixelSize = (Double)dialog.hiResPixelSize.getValue();
            	if (hiResPixelSize != null) {
            	    configs.add(new Config(workflowModule.getId(), "hiResPixelSize", hiResPixelSize.toString()));
            	}
            	Double overlapPct = (Double)dialog.overlapPct.getValue();
            	if (overlapPct != null) {
            	    configs.add(new Config(workflowModule.getId(), "overlapPct", overlapPct.toString()));
            	}
            	Double roiMarginPct = (Double)dialog.roiMarginPct.getValue();
            	if (roiMarginPct != null) {
            	    configs.add(new Config(workflowModule.getId(), "roiMarginPct", roiMarginPct.toString()));
            	}
            	Double roiImageScaleFactor = (Double)dialog.roiImageScaleFactor.getValue();
            	if (roiImageScaleFactor != null) {
            	    configs.add(new Config(workflowModule.getId(), "roiImageScaleFactor", roiImageScaleFactor.toString()));
            	}
            	Integer imageWidth = (Integer)dialog.imageWidth.getValue();
            	if (imageWidth != null) {
            	    configs.add(new Config(workflowModule.getId(), "imageWidth", imageWidth.toString()));
            	}
            	Integer imageHeight = (Integer)dialog.imageHeight.getValue();
            	if (imageHeight != null) {
            	    configs.add(new Config(workflowModule.getId(), "imageHeight", imageHeight.toString()));
            	}
                if (!dialog.customMacro.getText().replaceAll("\\s+","").isEmpty()) {
                    configs.add(new Config(workflowModule.getId(), "customMacro", dialog.customMacro.getText()));
                }
                TaskType taskType = (TaskType)dialog.taskType.getSelectedItem();
                if (taskType != null) {
                    configs.add(new Config(workflowModule.getId(), "taskType", taskType.toString()));
                }
                return configs.toArray(new Config[0]);
            }
            @Override
            public Component display(Config[] configs) {
            	Map<String,Config> confs = new HashMap<String,Config>();
            	for (Config c : configs) {
            		confs.put(c.getKey(), c);
            	}

            	if (confs.containsKey("hiResPixelSize")) {
            	    dialog.hiResPixelSize.setValue(Double.parseDouble(confs.get("hiResPixelSize").getValue()));
            	}
            	if (confs.containsKey("overlapPct")) {
            	    dialog.overlapPct.setValue(Double.parseDouble(confs.get("overlapPct").getValue()));
            	}
            	if (confs.containsKey("roiMarginPct")) {
            	    dialog.roiMarginPct.setValue(Double.parseDouble(confs.get("roiMarginPct").getValue()));
            	}
            	if (confs.containsKey("roiImageScaleFactor")) {
            	    dialog.roiImageScaleFactor.setValue(Double.parseDouble(confs.get("roiImageScaleFactor").getValue()));
            	}
            	if (confs.containsKey("imageWidth")) {
            	    dialog.imageWidth.setValue(Integer.parseInt(confs.get("imageWidth").getValue()));
            	}
            	if (confs.containsKey("imageHeight")) {
            	    dialog.imageHeight.setValue(Integer.parseInt(confs.get("imageHeight").getValue()));
            	}
                if (confs.containsKey("customMacro")) {
                    dialog.customMacro.setText(confs.get("customMacro").getValue());
                }
                if (confs.containsKey("taskType")) {
                    try {
                        TaskType taskType = TaskType.valueOf(confs.get("taskType").getValue());
                        if (taskType != null) {
                            dialog.taskType.setSelectedItem(taskType);
                        }
                    }
                    catch (Throwable e) { /* do nothing */ }
                }
                return dialog;
            }
            @Override
            public ValidationError[] validate() {
            	List<ValidationError> errors = new ArrayList<ValidationError>();

            	Double hiResPixelSize = (Double)dialog.hiResPixelSize.getValue();
            	if (hiResPixelSize == null || hiResPixelSize == 0.0) {
            	    errors.add(new ValidationError(workflowModule.getName(), "Please enter a nonzero value for hiResPixelSize"));
            	}
            	Double overlapPct = (Double)dialog.overlapPct.getValue();
            	if (overlapPct == null || overlapPct < 0.0 || overlapPct > 100.0) {
            	    errors.add(new ValidationError(workflowModule.getName(), 
            	            "Please enter a value between 0 and 100 for Tile Percent Overlap"));
            	}
            	Double roiMarginPct = (Double)dialog.roiMarginPct.getValue();
            	if (roiMarginPct == null || roiMarginPct < 0.0 || roiMarginPct > 100.0) {
            	    errors.add(new ValidationError(workflowModule.getName(), 
            	            "Please enter a value between 0 and 100 for ROI Margin Percent"));
            	}
            	Double roiImageScaleFactor = (Double)dialog.roiImageScaleFactor.getValue();
            	if (roiImageScaleFactor == null || roiImageScaleFactor <= 0.0) {
            	    errors.add(new ValidationError(workflowModule.getName(), 
            	            "Please enter a value greater than 0.0 ROI Image Scale Factor"));
            	}
            	Integer imageWidth = (Integer)dialog.imageWidth.getValue();
            	if (imageWidth == null || imageWidth <= 0.0) {
            	    errors.add(new ValidationError(workflowModule.getName(), 
            	            "Please enter a value greater than 0 for the HiRes Image Width"));
            	}
            	Integer imageHeight = (Integer)dialog.imageHeight.getValue();
            	if (imageHeight == null || imageHeight <= 0.0) {
            	    errors.add(new ValidationError(workflowModule.getName(), 
            	            "Please enter a value greater than 0 for the HiRes Image Height"));
            	}
                if (dialog.customMacro.getText().replaceAll("\\s+","").isEmpty()) {
            	    errors.add(new ValidationError(workflowModule.getName(), 
            	            "Please fill in the custom macro code!"));
                }
                return errors.toArray(new ValidationError[0]);
            }
        };
    }

    @Override
    public TaskType getTaskType() {
        Config taskTypeConf = this.workflowRunner.getModuleConfig().selectOne(
                where("id", this.workflowModule.getId()).and("key", "taskType"));
        if (taskTypeConf == null) return CustomMacroROIFinderDialog.DEFAULT_TASK_TYPE;
        Module.TaskType taskType = Module.TaskType.valueOf(taskTypeConf.getValue());
        if (taskType == null) return CustomMacroROIFinderDialog.DEFAULT_TASK_TYPE;
        return taskType;
    }
}
