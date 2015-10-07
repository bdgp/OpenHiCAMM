package org.bdgp.OpenHiCAMM;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import org.bdgp.OpenHiCAMM.DB.Acquisition;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.Image;
import org.bdgp.OpenHiCAMM.DB.ModuleConfig;
import org.bdgp.OpenHiCAMM.DB.Pool;
import org.bdgp.OpenHiCAMM.DB.PoolSlide;
import org.bdgp.OpenHiCAMM.DB.ROI;
import org.bdgp.OpenHiCAMM.DB.Slide;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.TaskConfig;
import org.bdgp.OpenHiCAMM.DB.TaskDispatch;
import org.bdgp.OpenHiCAMM.DB.WorkflowModule;
import org.bdgp.OpenHiCAMM.Modules.ROIFinderDialog;
import org.bdgp.OpenHiCAMM.Modules.SlideImagerDialog;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.utils.ImageLabelComparator;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMSerializationException;

import com.sun.org.apache.xml.internal.security.utils.Base64;

import ij.ImagePlus;
import ij.gui.NewImage;
import ij.gui.Roi;
import ij.process.Blitter;
import ij.process.ImageProcessor;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import static org.bdgp.OpenHiCAMM.Util.where;
import static org.bdgp.OpenHiCAMM.Tag.T.*;

@SuppressWarnings("serial")
public class WorkflowReport extends JFrame {
	WorkflowRunner workflowRunner;

    public static final int SLIDE_PREVIEW_WIDTH = 1280; 
    public static final int ROI_GRID_PREVIEW_WIDTH = 512; 

	public WorkflowReport(WorkflowRunner workflowRunner) {
		this.workflowRunner = workflowRunner;

		JFXPanel fxPanel = new JFXPanel();
        this.add(fxPanel);
        this.setSize(1024, 768);
        this.setVisible(true);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Platform.runLater(new Runnable() {
            @Override public void run() {
                initFX(fxPanel);
            }
       });
	}
	
	public void initFX(JFXPanel fxPanel) {
        Scene scene = new Scene(new Group());
        VBox root = new VBox();     
        WebView browser = new WebView();
        WebEngine webEngine = browser.getEngine();
        JSObject jsobj = (JSObject) webEngine.executeScript("window");
        jsobj.setMember("workflowReport", this);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(browser);
        String html = runReport();
        webEngine.loadContent(html);

        root.getChildren().addAll(scrollPane);
        scene.setRoot(root);
        fxPanel.setScene(scene);
	}
	
	public String runReport() {
        Dao<Pool> poolDao = this.workflowRunner.getInstanceDb().table(Pool.class);
        Dao<PoolSlide> psDao = this.workflowRunner.getInstanceDb().table(PoolSlide.class);

	    // Find SlideImager modules where there is no associated posListModuleId module config
	    // This is the starting SlideImager module.
		return Html().with(()->{
			Body().with(()->{
                for (Config canImageSlides : this.workflowRunner.getModuleConfig().select(
                        where("key","canImageSlides").
                        and("value", "yes"))) 
                {
                    WorkflowModule slideImager = this.workflowRunner.getWorkflow().selectOneOrDie(
                            where("id", canImageSlides.getId()));

                    if (workflowRunner.getModuleConfig().selectOne(
                            where("id", slideImager.getId()).
                            and("key", "posListModuleId")) == null) 
                    {
                        // get the loader module
                        if (slideImager.getParentId() != null) {
                            WorkflowModule loaderModule = this.workflowRunner.getWorkflow().selectOneOrDie(
                                    where("id", slideImager.getParentId()));
                            if (this.workflowRunner.getModuleConfig().selectOne(
                                    where("id", loaderModule.getId()).
                                    and("key", "canLoadSlides").
                                    and("value", "yes")) != null) 
                            {
                                ModuleConfig poolIdConf = this.workflowRunner.getModuleConfig().selectOne(
                                        where("id", loaderModule.getId()).
                                        and("key", "poolId"));
                                if (poolIdConf != null) {
                                    Pool pool = poolDao.selectOneOrDie(where("id", poolIdConf.getValue()));
                                    List<PoolSlide> pss = psDao.select(where("poolId", pool.getId()));
                                    if (!pss.isEmpty()) {
                                        for (PoolSlide ps : pss) {
                                            runReport(slideImager, ps);
                                        }
                                        return;
                                    }
                                }
                            }
                        }
                        runReport(slideImager, null);
                    }
                }
			});
		}).toString();
	}

	private void runReport(WorkflowModule startModule, PoolSlide poolSlide) {
        Dao<Slide> slideDao = this.workflowRunner.getInstanceDb().table(Slide.class);
        Dao<Image> imageDao = this.workflowRunner.getInstanceDb().table(Image.class);
        Dao<Acquisition> acqDao = this.workflowRunner.getInstanceDb().table(Acquisition.class);
        Dao<ROI> roiDao = this.workflowRunner.getInstanceDb().table(ROI.class);

	    // get the ROIFinder module(s)
	    List<WorkflowModule> roiFinderModules = new ArrayList<WorkflowModule>();
	    for (WorkflowModule wm : this.workflowRunner.getWorkflow().select(where("parentId", startModule.getId()))) {
            if (this.workflowRunner.getModuleConfig().selectOne(
                    where("id", wm.getId()).
                    and("key", "canProduceROIs").
                    and("value", "yes")) != null) 
            {
                roiFinderModules.add(wm);
            }
	    }

	    // get the associated hi res ROI slide imager modules for each ROI finder module
	    Map<WorkflowModule,List<WorkflowModule>> roiImagers = new HashMap<WorkflowModule,List<WorkflowModule>>();
	    for (WorkflowModule roiFinderModule : roiFinderModules) {
            for (Config canImageSlides : this.workflowRunner.getModuleConfig().select(
                    where("key","canImageSlides").
                    and("value", "yes"))) 
            {
                WorkflowModule slideImager = this.workflowRunner.getWorkflow().selectOneOrDie(
                        where("id", canImageSlides.getId()));
                if (!workflowRunner.getModuleConfig().select(
                        where("id", slideImager.getId()).
                        and("key", "posListModuleId").
                        and("value", roiFinderModule.getId())).isEmpty()) 
                {
                    if (!roiImagers.containsKey(roiFinderModule)) {
                        roiImagers.put(roiFinderModule, new ArrayList<WorkflowModule>());
                    }
                    roiImagers.get(roiFinderModule).add(slideImager);
                }
            }
	    }
	    
	    // display the title
        Slide slide;
        String slideId;
	    if (poolSlide != null) {
	        slide = slideDao.selectOneOrDie(where("id", poolSlide.getSlideId()));
	        slideId = slide.getName();
            H1().text(String.format("SlideImager %s, Slide %s, Pool %d, Cartridge %d, Slide Position %d", 
                    startModule.getId(), 
                    slide.getName(), 
                    poolSlide.getPoolId(), 
                    poolSlide.getCartridgePosition(), 
                    poolSlide.getSlidePosition()));
	    }
	    else {
            slide = null;
            slideId = "slide";
            H1().text(String.format("SlideImager %s", startModule.getId()));
	    }

        // get the pixel size of this slide imager config
        Config pixelSizeConf = this.workflowRunner.getModuleConfig().selectOne(where("id", startModule.getId()).and("key","pixelSize"));
        Double pixelSize = pixelSizeConf != null? new Double(pixelSizeConf.getValue()) : SlideImagerDialog.DEFAULT_PIXEL_SIZE_UM;

        // get invertXAxis and invertYAxis conf values
        Config invertXAxisConf = this.workflowRunner.getModuleConfig().selectOne(where("id", startModule.getId()).and("key","invertXAxis"));
        boolean invertXAxis = invertXAxisConf == null || invertXAxisConf.getValue().equals("yes");
        Config invertYAxisConf = this.workflowRunner.getModuleConfig().selectOne(where("id", startModule.getId()).and("key","invertYAxis"));
        boolean invertYAxis = invertYAxisConf == null || invertYAxisConf.getValue().equals("yes");

        // sort imageTasks by image position
        List<Task> imageTasks = this.workflowRunner.getTaskStatus().select(where("moduleId", startModule.getId()));
        Map<String,Task> imageTaskPosIdx = new TreeMap<String,Task>(new ImageLabelComparator());
        for (Task imageTask : imageTasks) {
            Config imageLabelConf = this.workflowRunner.getTaskConfig().selectOne(
                    where("id", imageTask.getId()).and("key", "imageLabel"));
            if (imageLabelConf != null) {
                imageTaskPosIdx.put(imageLabelConf.getValue(), imageTask);
            }
        }
        imageTasks.clear();
        imageTasks.addAll(imageTaskPosIdx.values());

        // determine the bounds of the stage coordinates
        Double minX_ = null, minY_ = null, maxX = null, maxY = null; 
        Integer imageWidth = null, imageHeight = null;
        for (Task task : imageTasks) {
        	MultiStagePosition msp = getMsp(task);

            if (minX_ == null || msp.getX() < minX_) minX_ = msp.getX();
            if (maxX == null || msp.getX() > maxX) maxX = msp.getX();
            if (minY_ == null || msp.getY() < minY_) minY_ = msp.getY();
            if (maxY == null || msp.getY() > maxY) maxY = msp.getY();
            if (imageWidth == null || imageHeight == null) {
                Config imageIdConf = this.workflowRunner.getTaskConfig().selectOne(
                        where("id", task.getId()).and("key", "imageId"));
                if (imageIdConf != null) {
                    Image image = imageDao.selectOneOrDie(where("id", new Integer(imageIdConf.getValue())));
                    ImagePlus imp = image.getImagePlus(acqDao);
                    imageWidth = imp.getWidth();
                    imageHeight = imp.getHeight();
                }
            }
        }
        Double minX = minX_, minY = minY_;
        int slideWidthPx = (int)Math.floor(((maxX - minX) / pixelSize) + (double)imageWidth);
        int slideHeightPx = (int)Math.floor(((maxY - minY) / pixelSize) + (double)imageHeight);
        
        // this is the scale factor for creating the thumbnail images
        double scaleFactor = (double)SLIDE_PREVIEW_WIDTH / (double)slideWidthPx;
        int slidePreviewHeight = (int)Math.floor(scaleFactor * slideHeightPx);
        
        ImagePlus slideThumb = NewImage.createRGBImage("slideThumb", SLIDE_PREVIEW_WIDTH, slidePreviewHeight, 1, NewImage.FILL_WHITE);
        ImageProcessor slideThumbIp = slideThumb.getProcessor();
        List<Roi> imageRois = new ArrayList<Roi>();
        List<Roi> roiRois = new ArrayList<Roi>();
        Map(name->String.format("map-%s-%s", startModule.getId(), slideId)).with(()->{
            for (Task task : imageTasks) {
                Config imageIdConf = this.workflowRunner.getTaskConfig().selectOne(
                        where("id", new Integer(task.getId()).toString()).and("key", "imageId"));
                if (imageIdConf != null) {
                    MultiStagePosition msp = getMsp(task);

                    // Get a thumbnail of the image
                    Image image = imageDao.selectOneOrDie(where("id", new Integer(imageIdConf.getValue())));
                    ImagePlus imp = image.getImagePlus(acqDao);
                    int width = imp.getWidth(), height = imp.getHeight();
                    ImageProcessor ip = imp.getProcessor();
                    ip.setInterpolate(true);
                    imp.setProcessor(imp.getTitle(), ip.resize(
                            (int)Math.floor(imp.getWidth() * scaleFactor), 
                            (int)Math.floor(imp.getHeight() * scaleFactor)));
                    
                    int xloc = (int)Math.floor(((msp.getX() - minX) / pixelSize));
                    int xlocInvert = invertXAxis? slideWidthPx - (xloc + width) : xloc;
                    int xlocScale = (int)Math.floor(xlocInvert * scaleFactor);
                    int yloc = (int)Math.floor(((msp.getY() - minY) / pixelSize));
                    int ylocInvert = invertYAxis? slideHeightPx - (yloc + height) : yloc;
                    int ylocScale = (int)Math.floor(ylocInvert * scaleFactor);

                    slideThumbIp.copyBits(ip, xlocScale, ylocScale, Blitter.COPY);
                    Roi imageRoi = new Roi(xlocScale, ylocScale, imp.getWidth(), imp.getHeight()); 
                    imageRoi.setName(image.getName());
                    imageRoi.setProperty("id", new Integer(image.getId()).toString());
                    imageRois.add(imageRoi);
                    
                    for (ROI roi : roiDao.select(where("imageId", image.getId()))) {
                    	int roiX = (int)Math.floor(xlocScale + (roi.getX1() * scaleFactor));
                    	int roiY = (int)Math.floor(ylocScale + (roi.getY1() * scaleFactor));
                    	int roiWidth = (int)Math.floor((roi.getX2()-roi.getX1()+1) * scaleFactor);
                        int roiHeight = (int)Math.floor((roi.getY2()-roi.getY1()+1) * scaleFactor);
                        Roi r = new Roi(roiX, roiY, roiWidth, roiHeight);
                        r.setName(roi.toString());
                        r.setProperty("id", new Integer(roi.getId()).toString());
                        roiRois.add(r);
                    }
                    
                }
            }

            // write the ROI areas first so they take precedence
            for (Roi roi : roiRois) {
                Area(shape->"rect",
                        coords->String.format("%d,%d,%d,%d", 
                                (int)Math.floor(roi.getXBase()), 
                                (int)Math.floor(roi.getYBase()), 
                                (int)Math.floor(roi.getXBase()+roi.getFloatWidth()), 
                                (int)Math.floor(roi.getYBase()+roi.getFloatHeight())),
                        href->String.format("#area-ROI-%s", roi.getProperty("id")),
                        title->roi.getName());
            }
            // next write the image ROIs
            for (Roi roi : imageRois) {
                Area(shape->"rect", 
                        coords->String.format("%d,%d,%d,%d", 
                                (int)Math.floor(roi.getXBase()), 
                                (int)Math.floor(roi.getYBase()), 
                                (int)Math.floor(roi.getXBase()+roi.getFloatWidth()), 
                                (int)Math.floor(roi.getYBase()+roi.getFloatHeight())),
                        title->roi.getName(),
                        onClick->String.format("workflowReport.showImage(%d)", new Integer(roi.getProperty("id"))));
            }
            // now draw the image ROIs in black
            slideThumbIp.setColor(new Color(0, 0, 0));
            for (Roi imageRoi : imageRois) {
                slideThumbIp.draw(imageRoi);
            }
            // now draw the ROI rois in red
            slideThumbIp.setColor(new Color(255, 0, 0));
            for (Roi roiRoi : roiRois) {
                slideThumbIp.draw(roiRoi);
            }
        });

        // write the slide thumbnail as an embedded HTML image.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try { ImageIO.write(slideThumb.getBufferedImage(), "jpg", baos); } 
        catch (IOException e) {throw new RuntimeException(e);}
        Img(src->String.format("data:image/jpeg;base64,%s", Base64.encode(baos.toByteArray())),
        		width->slideThumb.getWidth(),
        		height->slideThumb.getHeight(),
        		usemap->String.format("#map-%s-%s", startModule.getId(), slideId));
        
        // now render the individual ROI sections
        for (Task task : imageTasks) {
            Config imageIdConf = this.workflowRunner.getTaskConfig().selectOne(
                    where("id", new Integer(task.getId()).toString()).and("key", "imageId"));
            if (imageIdConf != null) {
                Image image = imageDao.selectOneOrDie(where("id", new Integer(imageIdConf.getValue())));
                List<ROI> rois = roiDao.select(where("imageId", image.getId())); 
                Collections.sort(rois, (a,b)->a.getId()-b.getId());
                for (ROI roi : rois) {
                    Hr();
                    A(name->String.format("area-ROI-%s", roi.getId()));
                    H2().text(String.format("Image %s, ROI %s", image, roi));
                    
                    // go through each attached SlideImager and look for MSP's with an ROI property
                    // that matches this ROI's ID.
                    for (Map.Entry<WorkflowModule, List<WorkflowModule>> entry : roiImagers.entrySet()) {
                        for (WorkflowModule imager : entry.getValue()) {
                            // get the hires pixel size for this imager
                            Config hiResPixelSizeConf = this.workflowRunner.getModuleConfig().selectOne(where("id", imager.getId()).and("key","pixelSize"));
                            Double hiResPixelSize = hiResPixelSizeConf != null? new Double(hiResPixelSizeConf.getValue()) : ROIFinderDialog.DEFAULT_HIRES_PIXEL_SIZE_UM;

                            // determine the stage coordinate bounds of this ROI tile grid.
                            // also get the image width and height of the acqusition.
                            Double minX2_=null, minY2_=null, maxX2=null, maxY2=null;
                            Integer imageWidth2 = null, imageHeight2 = null;
                            Map<String,List<Task>> imagerTasks = new TreeMap<String,List<Task>>(new ImageLabelComparator());
                            for (Task imagerTask : this.workflowRunner.getTaskStatus().select(where("moduleId", imager.getId()))) {
                                MultiStagePosition msp = getMsp(imagerTask);
                                if (msp.hasProperty("ROI") && msp.getProperty("ROI").equals(new Integer(roi.getId()).toString())) 
                                {
                                    TaskConfig imageLabelConf = this.workflowRunner.getTaskConfig().selectOne(
                                            where("id", imagerTask.getId()).
                                            and("key", "imageLabel"));
                                    int[] indices = MDUtils.getIndices(imageLabelConf.getValue());
                                    if (indices != null && indices.length >= 4) {
                                        String imageLabel = MDUtils.generateLabel(indices[0], indices[1], indices[2], 0);
                                        if (!imagerTasks.containsKey(imageLabel)) {
                                            imagerTasks.put(imageLabel, new ArrayList<Task>());
                                        }
                                        imagerTasks.get(imageLabel).add(imagerTask);
                                        
                                        if (minX2_ == null || msp.getX() < minX2_) minX2_ = msp.getX();
                                        if (minY2_ == null || msp.getY() < minY2_) minY2_ = msp.getY();
                                        if (maxX2 == null || msp.getX() > maxX2) maxX2 = msp.getX();
                                        if (maxY2 == null || msp.getY() > maxY2) maxY2 = msp.getY();
                                        if (imageWidth2 == null || imageHeight2 == null) {
                                            Config imageIdConf2 = this.workflowRunner.getTaskConfig().selectOne(
                                                    where("id", imagerTask.getId()).
                                                    and("key", "imageId"));
                                            if (imageIdConf2 != null) {
                                                Image image2 = imageDao.selectOneOrDie(
                                                        where("id", new Integer(imageIdConf2.getValue())));
                                                ImagePlus imp = image2.getImagePlus(acqDao);
                                                imageWidth2 = imp.getWidth();
                                                imageHeight2 = imp.getHeight();
                                            }
                                        }
                                    }
                                }
                            }
                            Double minX2 = minX2_, minY2 = minY2_;
                            if (!imagerTasks.isEmpty()) {
                                int gridWidthPx = (int)Math.floor(((maxX2 - minX2_) / hiResPixelSize) + (double)imageWidth2);
                                int gridHeightPx = (int)Math.floor(((maxY2 - minY2_) / hiResPixelSize) + (double)imageHeight2);
                                
                                // this is the scale factor for creating the thumbnail images
                                double gridScaleFactor = (double)ROI_GRID_PREVIEW_WIDTH / (double)gridWidthPx;
                                int gridPreviewHeight = (int)Math.floor(gridScaleFactor * gridHeightPx);

                                ImagePlus roiGridThumb = NewImage.createRGBImage(
                                        String.format("roiGridThumb-%s-ROI%d", imager.getId(), roi.getId()), 
                                        ROI_GRID_PREVIEW_WIDTH, 
                                        gridPreviewHeight, 
                                        1, 
                                        NewImage.FILL_WHITE);
                                ImageProcessor roiGridThumbIp = roiGridThumb.getProcessor();
                                
                                Table().with(()->{
                                    Thead().with(()->{
                                        Tr().with(()->{
                                            Th().text("Channel, Slice, Frame");
                                            Th().text("Tiled ROI Images");
                                            Th().text("Stitched ROI Image");
                                        });
                                    });
                                    Tbody().with(()->{
                                        for (Map.Entry<String,List<Task>> imagerTaskEntry : imagerTasks.entrySet()) {
                                            String imageLabel = imagerTaskEntry.getKey();
                                            int[] indices = MDUtils.getIndices(imageLabel);
                                            int channel = indices[0], slice = indices[1], frame = indices[2];

                                            Tr().with(()->{
                                                Th().text(String.format("Channel %d, Slice %d, Frame %d", channel, slice, frame));
                                                Td().with(()->{
                                                    Map(name->String.format("map-roi-%s-ROI%d", imager.getId(), roi.getId())).with(()->{
                                                        for (Task imagerTask : imagerTaskEntry.getValue()) {
                                                            Config imageIdConf2 = this.workflowRunner.getTaskConfig().selectOne(
                                                                    where("id", new Integer(imagerTask.getId()).toString()).and("key", "imageId"));
                                                            if (imageIdConf2 != null) {
                                                                MultiStagePosition msp = getMsp(imagerTask);

                                                                // Get a thumbnail of the image
                                                                Image image2 = imageDao.selectOneOrDie(where("id", new Integer(imageIdConf2.getValue())));
                                                                ImagePlus imp = image2.getImagePlus(acqDao);
                                                                int width = imp.getWidth(), height = imp.getHeight();
                                                                ImageProcessor ip = imp.getProcessor();
                                                                ip.setInterpolate(true);
                                                                imp.setProcessor(imp.getTitle(), ip.resize(
                                                                        (int)Math.floor(imp.getWidth() * gridScaleFactor), 
                                                                        (int)Math.floor(imp.getHeight() * gridScaleFactor)));

                                                                int xloc = (int)Math.floor((msp.getX() - minX2) / hiResPixelSize);
                                                                int xlocInvert = invertXAxis? gridWidthPx - (xloc + width) : xloc;
                                                                int xlocScale = (int)Math.floor(xlocInvert * gridScaleFactor);
                                                                int yloc = (int)Math.floor((msp.getY() - minY2) / hiResPixelSize);
                                                                int ylocInvert = invertYAxis? gridHeightPx - (yloc + height) : yloc;
                                                                int ylocScale = (int)Math.floor(ylocInvert * gridScaleFactor);

                                                                roiGridThumbIp.copyBits(ip, xlocScale, ylocScale, Blitter.COPY);

                                                                // make the tile image clickable
                                                                Area(shape->"rect", 
                                                                        coords->String.format("%d,%d,%d,%d", 
                                                                                xlocScale, 
                                                                                ylocScale,
                                                                                xlocScale+imp.getWidth(),
                                                                                ylocScale+imp.getHeight()),
                                                                        title->image.getName(),
                                                                        onClick->String.format("workflowReport.showImage(%d)", image.getId()));

                                                                // draw a black rectangle around the image
                                                                roiGridThumbIp.setColor(new Color(0, 0, 0));
                                                                roiGridThumbIp.drawRect(xlocScale, ylocScale, imp.getWidth(), imp.getHeight());
                                                            }
                                                        }
                                                    });

                                                    // write the slide thumbnail as an embedded HTML image.
                                                    ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
                                                    try { ImageIO.write(roiGridThumb.getBufferedImage(), "jpg", baos2); } 
                                                    catch (IOException e) {throw new RuntimeException(e);}
                                                    Img(src->String.format("data:image/jpeg;base64,%s", Base64.encode(baos2.toByteArray())),
                                                            width->roiGridThumb.getWidth(),
                                                            height->roiGridThumb.getHeight(),
                                                            usemap->String.format("#map-%s-ROI%d", imager.getId(), roi.getId()));
                                                });
                                                Td().with(()->{
                                                    // get the downstream stitcher tasks
                                                    Set<Task> stitcherTasks = new HashSet<Task>();
                                                    for (Task imagerTask : imagerTaskEntry.getValue()) {
                                                        for (TaskDispatch td : this.workflowRunner.getTaskDispatch().select(where("parentTaskId", imagerTask.getId()))) {
                                                            Task stitcherTask = this.workflowRunner.getTaskStatus().selectOneOrDie(where("id", td.getTaskId()));
                                                            if (!this.workflowRunner.getModuleConfig().select(
                                                                    where("id", stitcherTask.getModuleId()).
                                                                    and("key", "canStitchImages").
                                                                    and("value", "yes")).isEmpty()) 
                                                            {
                                                                stitcherTasks.add(stitcherTask);
                                                            }
                                                        }
                                                    }
                                                    for (Task stitcherTask : stitcherTasks) {
                                                        Config stitchedImageFile = this.workflowRunner.getTaskConfig().selectOne(
                                                                where("id", new Integer(stitcherTask.getId()).toString()).and("key", "stitchedImageFile"));
                                                        if (stitchedImageFile != null && new File(stitchedImageFile.getValue()).exists()) {
                                                            // Get a thumbnail of the image
                                                            ImagePlus imp = new ImagePlus(stitchedImageFile.getValue());
                                                            ImageProcessor ip = imp.getProcessor();
                                                            ip.setInterpolate(true);

                                                            double stitchScaleFactor = (double)ROI_GRID_PREVIEW_WIDTH / (double)imp.getWidth();
                                                            int stitchPreviewHeight = (int)Math.floor(imp.getHeight() * stitchScaleFactor);
                                                            imp.setProcessor(imp.getTitle(), ip.resize(
                                                                    ROI_GRID_PREVIEW_WIDTH, 
                                                                    stitchPreviewHeight));

                                                            // write the stitched thumbnail as an embedded HTML image.
                                                            ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
                                                            try { ImageIO.write(imp.getBufferedImage(), "jpg", baos2); } 
                                                            catch (IOException e) {throw new RuntimeException(e);}
                                                            Img(src->String.format("data:image/jpeg;base64,%s", Base64.encode(baos2.toByteArray())),
                                                                    width->imp.getWidth(),
                                                                    height->imp.getHeight(),
                                                                    title->stitchedImageFile.getValue(),
                                                                    onClick->String.format("workflowReport.showImageFile(%s)", 
                                                                            Util.escapeJavaStyleString(stitchedImageFile.getValue())));
                                                        }
                                                    }
                                                });
                                            });   
                                        }
                                    }); 
                                });
                            }
                        }
                    }
                }
            }
        }
	}
	
	public void showImage(int imageId) {
        Dao<Image> imageDao = this.workflowRunner.getInstanceDb().table(Image.class);
        Dao<Acquisition> acqDao = this.workflowRunner.getInstanceDb().table(Acquisition.class);
        Image image = imageDao.selectOneOrDie(where("id", imageId));
        ImagePlus imp = image.getImagePlus(acqDao);
        imp.show();
	}

	public void showImageFile(String imagePath) {
        ImagePlus imp = new ImagePlus(imagePath);
        imp.show();
	}
	
	private MultiStagePosition getMsp(Task task) {
        // get the MSP from the task config
        PositionList posList = new PositionList();
        try {
            Config mspConf = this.workflowRunner.getTaskConfig().selectOneOrDie(where("id", task.getId()).and("key","MSP"));
            JSONObject posListJson = new JSONObject().
                    put("POSITIONS", new JSONArray().put(new JSONObject(mspConf.getValue()))).
                    put("VERSION", 3).
                    put("ID","Micro-Manager XY-position list");
            posList.restore(posListJson.toString());
        } 
        catch (JSONException | MMSerializationException e) {throw new RuntimeException(e);}
        MultiStagePosition msp = posList.getPosition(0);
        return msp;
	}
}
