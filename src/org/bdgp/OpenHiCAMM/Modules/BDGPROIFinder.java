package org.bdgp.OpenHiCAMM.Modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bdgp.OpenHiCAMM.ImageLog.ImageLogRunner;
import org.bdgp.OpenHiCAMM.Logger;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.Image;
import org.bdgp.OpenHiCAMM.DB.ROI;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.ImageLogger;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Module;
import org.json.JSONException;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.ParticleAnalyzer;
import ij.process.ImageProcessor;
import mmcorej.TaggedImage;

public class BDGPROIFinder extends ROIFinder implements Module, ImageLogger {
    public BDGPROIFinder() {
        super();
    }

    @Override
    public List<ROI> process(
            Image image, 
            TaggedImage taggedImage,
            Logger logger, 
            ImageLogRunner imageLog,
            Map<String, Config> config) 
    {
        // get the image label
        String positionName = null;
        try { positionName = MDUtils.getPositionName(taggedImage.tags); } 
        catch (JSONException e) {throw new RuntimeException(e);} 
        String imageLabel = image.getLabel();
        String label = String.format("%s (%s)", positionName, imageLabel); 

        List<ROI> rois = new ArrayList<ROI>();
        ImageProcessor processor = ImageUtils.makeProcessor(taggedImage);
        ImagePlus imp = new ImagePlus(image.toString(), processor);
        
        // Convert to gray
        logger.fine(String.format("%s: Convert to gray", label));
        IJ.run(imp, "8-bit", "");
        imageLog.addImage(imp, "Convert to gray");
        
        int w=imp.getWidth();
        int h=imp.getHeight();
        logger.fine(String.format("%s: Image dimensions: (%d,%d)", label, w, h));
        
        Config roiImageScaleFactorConf = config.get("roiImageScaleFactor");
        if (roiImageScaleFactorConf == null) throw new RuntimeException("Config value roiImageScaleFactor not found!");
        Double roiImageScaleFactor = new Double(roiImageScaleFactorConf.getValue());

        // Resize by roiImageScaleFactor
        if (roiImageScaleFactor != 1.0) {
            logger.fine(String.format("%s: Resizing", label));
            imp.getProcessor().setInterpolationMethod(ImageProcessor.BILINEAR);
            imp.setProcessor(imp.getTitle(), imp.getProcessor().resize(
                    (int)Math.floor(imp.getWidth() * roiImageScaleFactor), 
                    (int)Math.floor(imp.getHeight() * roiImageScaleFactor)));
            imageLog.addImage(imp, "Resizing");
        }
        
        Config imageScaleFactorConf = config.get("imageScaleFactor");
        double imageScaleFactor = imageScaleFactorConf != null? new Double(imageScaleFactorConf.getValue()) : 1.0;

        // get the pixel size config value and scale it by the image scale factor
        // This value is inherited from the parent module's moduleconfig
        Config pixelSizeConf = config.get("pixelSize");
        if (pixelSizeConf == null) throw new RuntimeException("pixelSize config value is missing!");
        double pixelSize = new Double(pixelSizeConf.getValue());
        pixelSize /= imageScaleFactor;
        logger.fine(String.format("%s: Using pixelSize: %f", label, pixelSize));

        // get the minRoiArea config value and scale it by the pixel size squared
        Config minRoiAreaConf = config.get("minRoiArea");
        if (minRoiAreaConf == null) throw new RuntimeException("minRoiArea config value is missing!");
        double minRoiArea = new Double(minRoiAreaConf.getValue());
        minRoiArea /= pixelSize;
        minRoiArea *= minRoiArea;
        logger.fine(String.format("%s: Using minRoiArea: %f", label, minRoiArea));
        
        // Find edges
        logger.fine(String.format("%s: Finding edges", label));
        IJ.run(imp, "Find Edges", "");
        imageLog.addImage(imp, "Find Edges");

        // convert to short[] values
        imp.setProcessor(imp.getProcessor().convertToByte(true));

        // Auto threshold
        byte pixelThreshold = 13;
        byte pixMin = 0, pixMax = -1;
        logger.fine(String.format("%s: Running threshold", label));
        //IJ.run(imp, "Auto Threshold", "method=IsoData white");
        byte[] pixels = (byte[])imp.getProcessor().getPixels();
        for (int i=0; i<pixels.length; ++i) {
            pixels[i] = pixels[i] < pixelThreshold? pixMin : pixMax;
        }
        imageLog.addImage(imp, "Threshold");
        
        // Gray morphology
        logger.fine(String.format("%s: Running gray morphology", label));
        IJ.run(imp, "Gray Morphology", "radius=10 type=circle operator=[fast close]");
        imageLog.addImage(imp, "Gray Morphology");

        // invert
        //logger.fine(String.format("%s: Running invert", label));
        //IJ.run(imp, "Invert", "");
        //imageLog.addImage(imp, "Invert");

        // Fill holes
        logger.fine(String.format("%s: Filling holes", label));
        IJ.run(imp, "Options...", "iterations=1 count=1 black");
        IJ.run(imp, "Fill Holes", "");
        imageLog.addImage(imp, "Filling holes");
        
        // Run the particle analyzer and save results to a results table
        ResultsTable rt = new ResultsTable();
        ParticleAnalyzer particleAnalyzer = new ParticleAnalyzer(
                ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES|ParticleAnalyzer.CLEAR_WORKSHEET|ParticleAnalyzer.IN_SITU_SHOW,
                Measurements.AREA|Measurements.MEAN|Measurements.MIN_MAX|Measurements.RECT,
                rt, 0.0, Double.POSITIVE_INFINITY);
        if (!particleAnalyzer.analyze(imp)) throw new RuntimeException("ParticleAnalyzer.analyze() returned false!");

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

            // Area usually > 18,000 but embryo may be cut off at boundary; don’t know how your ROI code would deal with that
            // -> I’d suggest:
            // Select for area > 2000, check if object is at boundary of image (bx or by == 1)
            // ROI: upper left corner = bx/by with width/height

            //if (area >= minRoiArea && bx > 1 && by > 1 && bx+width < w && by+height < h) {
            if (area >= minRoiArea) {
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
            else {
                if (area < minRoiArea) {
                    logger.finest(String.format("Skipping, area %.2f is less than %.2f", area, minRoiArea));
                }
                //if (!(bx > 1 && by > 1 && bx+width < w && by+height < h)) {
                //    logger.finest(String.format("Skipping, ROI hits edge"));
                //}
            }
        }
        imageLog.addImage(imp, "Adding ROIs to image");
        return rois;
    }
}
