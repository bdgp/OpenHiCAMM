package org.bdgp.OpenHiCAMM.Modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bdgp.OpenHiCAMM.Dao;
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
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
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

        // get the minRoiArea config value
        double minRoiArea = new Double(config.get("minRoiArea").getValue());
        logger.fine(String.format("%s: Using minRoiArea: %f", label, minRoiArea));
        
        List<ROI> rois = new ArrayList<ROI>();
        ImageProcessor processor = ImageUtils.makeProcessor(taggedImage);
        ImagePlus imp = new ImagePlus(image.toString(), processor);
        //imp.show();
        
        // Convert to gray
        logger.fine(String.format("%s: Convert to gray", label));
        IJ.run(imp, "8-bit", "");
        imageLog.addImage(imp, "Convert to gray");
        
        // Subtract background
//        IJ.run(imp, "Subtract Background...", "rolling=200 light");
//        imageLog.addImage(imp, "Subtract Background");

        // Resize to 1/4
        int w=imp.getWidth();
        int h=imp.getHeight();
        logger.fine(String.format("%s: Image dimensions: (%d,%d)", label, w, h));

//        double scale = 0.25;
        double scale = 1.0;

        double ws=(double)w*scale;
        double hs=(double)h*scale;

        String scaleOp = String.format("x=%f y=%f width=%d height=%d interpolation=Bicubic average", 
                scale, scale, (int)ws, (int)hs);
        logger.fine(String.format("%s: Scaling: %s", label, scaleOp));
        IJ.run(imp, "Scale...", scaleOp);
        imageLog.addImage(imp, "Scaling: scaleOp");

        // Crop after scale
        double crop = 2.0;
        double rw=(w/crop)-(ws/crop);
        double rh=(h/crop)-(hs/crop);
        logger.fine(String.format("%s: Cropping: %d, %d, %d, %d", label, (int)rw, (int)rh, (int)ws, (int)hs));
        imp.setRoi((int)rw,(int)rh,(int)ws,(int)hs);
        IJ.run(imp, "Crop", "");
        imageLog.addImage(imp, String.format("Cropping: %d, %d, %d, %d", (int)rw, (int)rh, (int)ws, (int)hs));

        // Binarize
        logger.fine(String.format("%s: Binarizing", label));
        IJ.run(imp, "Auto Threshold", "method=Huang white"); // array out of bounds exception
        imageLog.addImage(imp, "Binarizing");

        // Morphological operations: close gaps, fill holes
        logger.fine(String.format("%s: Closing gaps", label));
        IJ.run(imp, "Close-", "");
        imageLog.addImage(imp, "Closing gaps");

        logger.fine(String.format("%s: Filling holes", label));
        IJ.run(imp, "Fill Holes", "");
        imageLog.addImage(imp, "Filling holes");

        // Set the measurements
        logger.fine(String.format("%s: Set the measurements", label));
        IJ.run(imp, "Set Measurements...", "area mean min bounding redirect=None decimal=3");
        imageLog.addImage(imp, "Set measurements");

        // Detect the objects
        logger.fine(String.format("%s: Analyzing particles", label));
        IJ.run(imp, "Analyze Particles...", "exclude clear in_situ");
        imageLog.addImage(imp, "Analyzing particles");
       
        Dao<ROI> roiDao = this.workflowRunner.getInstanceDb().table(ROI.class);
        // Get the objects and iterate through them
        ResultsTable rt = Analyzer.getResultsTable();
        logger.fine(String.format("ResultsTable Column Headings: %s", rt.getColumnHeadings()));
        for (int i=0; i < rt.getCounter(); i++) {
            double area = rt.getValue("Area", i) / (scale*scale); // area of the object
            double bx = rt.getValue("BX", i) / scale; // x of bounding box
            double by = rt.getValue("BY", i) / scale; // y of bounding box
            double width = rt.getValue("Width", i) / scale; // width of bounding box
            double height = rt.getValue("Height", i) / scale; // height of bounding box
            logger.finest(String.format(
                    "Found object: area=%.2f, bx=%.2f, by=%.2f, width=%.2f, height=%.2f",
                    area, bx, by, width, height));

            // Area usually > 18,000 but embryo may be cut off at boundary; don’t know how your ROI code would deal with that
            // -> I’d suggest:
            // Select for area > 2000, check if object is at boundary of image (bx or by == 1)
            // ROI: upper left corner = bx/by with width/height
            if (area >= minRoiArea && bx > 1 && by > 1 && bx+width < w && by+height < h) {
                ROI roi = new ROI(image.getId(), (int)(bx*scale), (int)(by*scale), (int)(bx+width), (int)(by+height));
                rois.add(roi);
                roiDao.insert(roi);
                logger.info(String.format("%s: Created new ROI record with width=%.2f, height=%.2f, area=%.2f: %s", 
                        label, width, height, area, roi));
                
                // Draw the ROI rectangle
                imp.setRoi(roi.getX1(), roi.getY1(), roi.getX2()-roi.getX1()+1, roi.getY2()-roi.getY1()+1);
                IJ.setForegroundColor(255, 255, 0);
                IJ.run(imp, "Draw", "");
            }
            else {
                if (area < minRoiArea) {
                    logger.finest(String.format("Skipping, area %.2f is less than %.2f", area, minRoiArea));
                }
                if (!(bx > 1 && by > 1 && bx+width < w && by+height < h)) {
                    logger.finest(String.format("Skipping, ROI hits edge"));
                }
            }
        }
        imageLog.addImage(imp, "Adding ROIs to image");
        return rois;
    }
}
