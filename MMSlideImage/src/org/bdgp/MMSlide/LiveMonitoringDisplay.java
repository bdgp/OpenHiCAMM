package org.bdgp.MMSlide;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import ij.ImagePlus;
import ij.gui.ImageCanvas;

import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONObject;
import org.micromanager.MMStudioMainFrame;
// import org.micromanager.acquisition.engine.SequenceSettings;
import org.micromanager.api.AcquisitionEngine;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.ChannelSpec;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMImageWindow;
import org.micromanager.utils.ReportingUtils;

import org.bdgp.MMSlide.engine.SequenceSettings;

/**
 *
 * @author arthur
 */
public class LiveMonitoringDisplay extends Thread {
   private static int untitledID_ = 0;
   protected final CMMCore core_;
   protected int imgCount_;
   SequenceSettings acqSettings_;
   private boolean diskCached_ = false;
   private BlockingQueue<TaggedImage>  imageProducingQueue_;
   private MMImageCache imageCache_ = null;
   TaggedImageStorage imageFileManager_;
   private MMImageWindow display_ = null; // Image cache stuff gets normally done in MMVirtualAcquisitionDisplay

   private AcquisitionEngine eng_;
   private int MaxQueue = 20;

   LiveMonitoringDisplay(CMMCore core,
           BlockingQueue<TaggedImage> imageProducingQueue,
           SequenceSettings acqSettings,
           ArrayList<ChannelSpec> channels,
           boolean diskCached,
           MMStudioMainFrame gui, // inserted for MMWindow
           AcquisitionEngine eng) {
      core_ = core;
      acqSettings_ = acqSettings;
      diskCached_ = diskCached;
      imageProducingQueue_ = imageProducingQueue;
      eng_ = eng;

      String acqPath;
      try {
         TaggedImageStorage imageFileManager;

         acqPath = createAcqPath(acqSettings.root, acqSettings.prefix);
         imageFileManager = imageFileManager_ = new TaggedImageStorageTrackEM2(acqPath, true, null);
         
         JSONObject summaryMetadata = makeSummaryMetadata(acqSettings);

         imageCache_ = new MMImageCache(imageFileManager);
         imageCache_.setSummaryMetadata(summaryMetadata);
         imageFileManager_.setSummaryMetadata(summaryMetadata); // ripped from MMImageCache functionality

         display_ = new MMImageWindow(core_, gui);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   private static String getUniqueUntitledName() {
      ++untitledID_;
      return "Untitled" + untitledID_;
   }

   private JSONObject makeSummaryMetadata(SequenceSettings acqSettings) {
      JSONObject md = new JSONObject();
      try {
      md.put("KeepShutterOpenChannels",acqSettings.keepShutterOpenChannels + "");
      md.put("KeepShutterOpenSlices", acqSettings.keepShutterOpenSlices + "");
      md.put("IntervalMs", acqSettings.intervalMs);
      md.put("SlicesFirst", acqSettings.slicesFirst + "");
      md.put("TimeFirst", acqSettings.timeFirst + "");
      md.put("Slices", acqSettings.slices.size());
      md.put("Frames", acqSettings.numFrames);
      md.put("Channels", acqSettings.channels.size());
      md.put("Positions", acqSettings.positions.size());
      md.put("Comment", acqSettings.comment);
      md.put("MetadataVersion", 10);
      md.put("Source", "Micro-Manager");
      md.put("PixelSize_um", core_.getPixelSizeUm());
      md.put("PixelAspect", 1.0);
      md.put("GridColumn", 0);
      md.put("GridRow", 0);
      md.put("ComputerName", getComputerName());
      md.put("Interval_ms", acqSettings.intervalMs);
      md.put("UserName", System.getProperty("user.name"));
      md.put("z-step_um", getZStep(acqSettings));
      setDimensions(md);
      setChannelTags(acqSettings, md);
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
      try {
         MDUtils.addRandomUUID(md);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
      return md;
   }

   private double getZStep(SequenceSettings acqSettings) {
      double zStep;
      if (acqSettings.slices.size() > 1) {
         zStep = acqSettings.slices.get(1) - acqSettings.slices.get(0);
      } else {
         zStep = 0;
      }
      return zStep;
   }

   private String getComputerName() {
      try {
         return InetAddress.getLocalHost().getHostName();
      } catch (Exception e) {
         return "";
      }
   }

   private void setChannelTags(SequenceSettings acqSettings, JSONObject md) {
      JSONArray channelColors = new JSONArray();
      JSONArray channelNames = new JSONArray();
      for (ChannelSpec channel : acqSettings.channels) {
         channelColors.put(channel.color_.getRGB());
         channelNames.put(channel.config_);
      }
      try {
         md.put("ChColors", channelColors);
         md.put("ChNames", channelNames);
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

   private void setDimensions(JSONObject summaryMetadata) {
      int type = 0;
      long depth = core_.getBytesPerPixel();
      type = getTypeFromDepth(depth);
      try {
         MDUtils.setWidth(summaryMetadata, (int) core_.getImageWidth());
         MDUtils.setHeight(summaryMetadata, (int) core_.getImageHeight());
         MDUtils.setPixelType(summaryMetadata, type);
         summaryMetadata.put("Depth", (int) depth);
         summaryMetadata.put("IJType", type);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }
   
   private int getTypeFromDepth(long depth) {
      int type = 0;
      if (depth == 1) {
         type = ImagePlus.GRAY8;
      }
      if (depth == 2) {
         type = ImagePlus.GRAY16;
      }
      if (depth == 4) {
         type = ImagePlus.COLOR_RGB;
      }
      if (depth == 8) {
         type = 64;
      }
      return type;
   }

   public void run() {
      long t1 = System.currentTimeMillis();

      try {
         while (true) {
        	 if ( imageProducingQueue_.size() > MaxQueue && eng_.isPaused() == false ) {
        		 eng_.setPause(true);
        		 ReportingUtils.logMessage("EF Queue size is " + imageProducingQueue_.size() + " -> Imaging paused");
        	 } else if ( imageProducingQueue_.size() <= MaxQueue && eng_.isPaused() == true ) {
        		 eng_.setPause(false);
        		 ReportingUtils.logMessage("EF Queue size less than MaxQueue - Imaging resumed");
        	 }
        	 
            TaggedImage image = imageProducingQueue_.poll(1, TimeUnit.SECONDS);
            if (image != null) {
               if (TaggedImageQueue.isPoison(image)) {
                  break;
               }
               displayImage(image);
            } else if ( eng_.isPaused() == true ) {
            	eng_.setPause(false);
            	ReportingUtils.logMessage("EF Queue zero - Imaging resumed");
            }
         }
      } catch (Exception ex2) {
         ReportingUtils.logError(ex2);
      }

      long t2 = System.currentTimeMillis();
      ReportingUtils.logMessage(imgCount_ + " images in " + (t2 - t1) + " ms.");

      cleanup();
   }

   protected void cleanup() {
      try {
         imageCache_.finished();
         imageFileManager_.finished(); // ripped from MMImageCache functionality
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

   private void displayImage(TaggedImage taggedImg) {
      try {
         display_.newImage(taggedImg.pix);
         ImageCanvas canvas = display_.getCanvas();
         canvas.fitToWindow();
         imageFileManager_.putImage(taggedImg); // ripped from MMImageCache functionality, for file IO
//         imageCache_.putImage(taggedImg); // for caching in memory
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

   private String createAcqPath(String root, String prefix) throws Exception {
      File rootDir = JavaUtils.createDirectory(root);
      int curIndex = getCurrentMaxDirIndex(rootDir, prefix + "_");

      File acqDir = null;
      acqDir = JavaUtils.createDirectory(root + "/" + prefix + "_" + (1 + curIndex));

      if (acqDir != null) {
         return acqDir.getAbsolutePath();
      } else {
         return "";
      }
   }

   private int getCurrentMaxDirIndex(File rootDir, String prefix) throws NumberFormatException {
      int maxNumber = 0;
      int number;
      String theName;
      for (File acqDir : rootDir.listFiles()) {
         theName = acqDir.getName();
         if (theName.startsWith(prefix)) {
            try {
               number = Integer.parseInt(theName.substring(prefix.length()));
               if (number >= maxNumber) {
                  maxNumber = number;
               }
            } catch (NumberFormatException e) {
            } // Do nothing.
         }
      }
      return maxNumber;
   }
}
