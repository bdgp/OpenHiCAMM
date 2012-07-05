package org.bdgp.MMSlide;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.acquisition.TaggedImageStorageDiskDefault;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

public class TaggedImageStorageTrackEM2 extends TaggedImageStorageDiskDefault {

    final private String TrackEM2Fn_ = "trackem.txt";
    final private int MaxQueue_ = 3;

    private String dir_;
    private boolean createdTEMstream_ = false;
    private Writer trackEM2stream_ = null;

    public TaggedImageStorageTrackEM2(String dir, boolean newDataSet,
            JSONObject summaryMetadata) throws Exception {
        super(dir, newDataSet, summaryMetadata);

        dir_ = dir;
    }

    protected void superSaveImageFile(Object img, JSONObject md, String path, String tiffFileName) {
        ImagePlus imp;
        try {
            ImageProcessor ip = null;
            int width = MDUtils.getWidth(md);
            int height = MDUtils.getHeight(md);
            String pixelType = MDUtils.getPixelType(md);
            if (pixelType.equals("GRAY8")) {
                ip = new ByteProcessor(width, height);
                ip.setPixels((byte[]) img);
                saveImageProcessor(ip, md, path, tiffFileName);
            } else if (pixelType.equals("GRAY16")) {
                ip = new ShortProcessor(width, height);
                ip.setPixels((short[]) img);
                saveImageProcessor(ip, md, path, tiffFileName);
            } else if (pixelType.equals("RGB32")) {
                byte[][] planes = ImageUtils.getColorPlanesFromRGB32((byte []) img);
                ColorProcessor cp = new ColorProcessor(width, height);
                cp.setRGB(planes[0],planes[1],planes[2]);
                saveImageProcessor(cp, md, path, tiffFileName);
            } else if (pixelType.equals("RGB64")) {
                short[][] planes = ImageUtils.getColorPlanesFromRGB64((short []) img);
                ImageStack stack = new ImageStack(width, height);
                stack.addSlice("Red", planes[0]);
                stack.addSlice("Green", planes[1]);
                stack.addSlice("Blue", planes[2]);
                imp = new ImagePlus(path + "/" + tiffFileName, stack);
                imp.setDimensions(3, 1, 1);
                imp = new CompositeImage(imp, CompositeImage.COLOR);
                saveImagePlus(imp, md, path, tiffFileName);
            }
        } catch (Exception ex) {
            ReportingUtils.logError(ex);
        }
    }

    protected void saveImageProcessor(ImageProcessor ip, JSONObject md, String path, String tiffFileName) {
        if (ip != null) {
            ImagePlus imp = new ImagePlus(path + "/" + tiffFileName, ip);
            saveImagePlus(imp, md, path, tiffFileName);
        }
    }

    protected void saveImageFile(Object img, JSONObject md, String path, String tiffFileName) {
        superSaveImageFile(img, md, path, tiffFileName);

        try {
            if ( trackEM2stream_ == null ) {
                // make sure we try creating the file only once per run
                if ( createdTEMstream_ == false ) {
                    createdTEMstream_ = true;
                    trackEM2stream_ = new BufferedWriter(new FileWriter(dir_ + "/" + TrackEM2Fn_));
                }
            } 
            if ( trackEM2stream_ != null ) {
                String stageName;
                int x, y, z;

                trackEM2stream_.write(path + "/" + tiffFileName + "\t");

                try {
                    stageName = md.getString("Core-XYStage");
                    x = md.getInt("Acquisition-"+stageName+"RequestedXPosition");
                    y = md.getInt("Acquisition-"+stageName+"RequestedYPosition");
                    z = 0;
                    trackEM2stream_.write(x + "\t" + y + "\t" + z + "\n");

                } catch (JSONException e) {
                    // TODO Auto-generated catch block
                    // e.printStackTrace();
                    ReportingUtils.logError(e);
                    trackEM2stream_.write("ERROR - no xyz data\n");
                }

                trackEM2stream_.flush();
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            ReportingUtils.logError(e);
            trackEM2stream_ = null; 
        }

    }


    public void finished() {
        super.finished();
        try {
            trackEM2stream_.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
