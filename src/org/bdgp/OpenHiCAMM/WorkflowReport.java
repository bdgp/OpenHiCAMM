package org.bdgp.OpenHiCAMM;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.imageio.ImageIO;

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
import org.bdgp.OpenHiCAMM.DB.Task.Status;
import org.bdgp.OpenHiCAMM.Modules.ROIFinderDialog;
import org.bdgp.OpenHiCAMM.Modules.SlideImagerDialog;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Report;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.utils.ImageLabelComparator;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMSerializationException;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.NewImage;
import ij.gui.Roi;
import ij.process.Blitter;
import ij.process.ImageProcessor;

import static org.bdgp.OpenHiCAMM.Util.where;
import static org.bdgp.OpenHiCAMM.Tag.T.*;

public class WorkflowReport implements Report {
    public static final int SLIDE_PREVIEW_WIDTH = 1280; 
    public static final int ROI_GRID_PREVIEW_WIDTH = 425; 
    public static final boolean DEBUG=true;

    private WorkflowRunner workflowRunner;

    public void jsLog(String message) {
        IJ.log(String.format("[WorkflowReport:js] %s", message));
    }

    public void log(String message, Object... args) {
        if (DEBUG) {
            IJ.log(String.format("[WorkflowReport] %s", String.format(message, args)));
        }
    }
    
    @Override public void initialize(WorkflowRunner workflowRunner) {
        this.workflowRunner = workflowRunner;
    }
    
    @Override
    public String runReport() {
        Dao<Pool> poolDao = this.workflowRunner.getInstanceDb().table(Pool.class);
        Dao<PoolSlide> psDao = this.workflowRunner.getInstanceDb().table(PoolSlide.class);

        // Find SlideImager modules where there is no associated posListModuleId module config
        // This is the starting SlideImager module.
        return Html().indent().with(()->{
            Head().with(()->{
                // use bootstrap with the default theme
                Link().attr("rel", "stylesheet").
                    attr("href", "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap.min.css");
                Link().attr("rel", "stylesheet").
                    attr("href", "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap-theme.min.css");
                Script().attr("src", "https://code.jquery.com/jquery-2.1.4.min.js");
                Script().attr("src", "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/js/bootstrap.min.js");
                try {
                    // taken from https://github.com/kemayo/maphilight
                    Script().raw(Resources.toString(Resources.getResource("jquery.maphilight.js"), Charsets.UTF_8));
                    Script().raw(Resources.toString(Resources.getResource("WorkflowReport.js"), Charsets.UTF_8));
                } 
                catch (Exception e) {throw new RuntimeException(e);}
            });
            Body().with(()->{
                List<Runnable> runnables = new ArrayList<Runnable>();
                for (Config canImageSlides : this.workflowRunner.getModuleConfig().select(
                        where("key","canImageSlides").
                        and("value", "yes"))) 
                {
                    WorkflowModule slideImager = this.workflowRunner.getWorkflow().selectOneOrDie(
                            where("id", canImageSlides.getId()));
                    log("Working on slideImager: %s", slideImager);
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
                                log("Using loaderModule: %s", loaderModule);
                                ModuleConfig poolIdConf = this.workflowRunner.getModuleConfig().selectOne(
                                        where("id", loaderModule.getId()).
                                        and("key", "poolId"));
                                if (poolIdConf != null) {
                                    Pool pool = poolDao.selectOneOrDie(where("id", poolIdConf.getValue()));
                                    List<PoolSlide> pss = psDao.select(where("poolId", pool.getId()));
                                    if (!pss.isEmpty()) {
                                        for (PoolSlide ps : pss) {
                                            P().with(()->{
                                                A(String.format("Module %s, Slide %s", slideImager, ps)).
                                                    attr("href", String.format("#report-%s-PS%s", slideImager.getId(), ps.getId()));
                                            });
                                            runnables.add(()->{
                                                log("Calling runReport(startModule=%s, poolSlide=%s)", slideImager, ps);
                                                runReport(slideImager, ps);
                                            });
                                        }
                                        continue;
                                    }
                                }
                            }
                        }
                        P().with(()->{
                            A(String.format("Module %s", slideImager)).
                                attr("href", String.format("#report-%s", slideImager.getId()));
                        });
                        runnables.add(()->{
                            log("Calling runReport(startModule=%s, poolSlide=null)", slideImager);
                            runReport(slideImager, null);
                        });
                    }
                }
                Hr();
                for (Runnable runnable : runnables) {
                    runnable.run();
                }
            });
        }).toString();
    }

    private void runReport(WorkflowModule startModule, PoolSlide poolSlide) {
        log("Called runReport(startModule=%s, poolSlide=%s)", startModule, poolSlide);

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
        log("roiFinderModules = %s", roiFinderModules);

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
        log("roiImagers = %s", roiImagers);
        
        // display the title
        Slide slide;
        String slideId;
        if (poolSlide != null) {
            slide = slideDao.selectOneOrDie(where("id", poolSlide.getSlideId()));
            slideId = slide.getName();
            String title = String.format("SlideImager %s, Slide %s, Pool %d, Cartridge %d, Slide Position %d", 
                    startModule.getId(), 
                    slide.getName(), 
                    poolSlide.getPoolId(), 
                    poolSlide.getCartridgePosition(), 
                    poolSlide.getSlidePosition());
            A().attr("name", String.format("report-%s-PS%s", startModule.getId(), poolSlide.getId()));
            H1().text(title);
            log("title = %s", title);
        }
        else {
            slide = null;
            slideId = "slide";
            String title = String.format("SlideImager %s", startModule.getId());
            A().attr("name", String.format("report-%s", startModule.getId()));
            H1().text(title);
            log("title = %s", title);
        }

        // get the pixel size of this slide imager config
        Config pixelSizeConf = this.workflowRunner.getModuleConfig().selectOne(where("id", startModule.getId()).and("key","pixelSize"));
        Double pixelSize = pixelSizeConf != null? new Double(pixelSizeConf.getValue()) : SlideImagerDialog.DEFAULT_PIXEL_SIZE_UM;
        log("pixelSize = %f", pixelSize);

        // get invertXAxis and invertYAxis conf values
        Config invertXAxisConf = this.workflowRunner.getModuleConfig().selectOne(where("id", startModule.getId()).and("key","invertXAxis"));
        boolean invertXAxis = invertXAxisConf == null || invertXAxisConf.getValue().equals("yes");
        log("invertXAxis = %b", invertXAxis);

        Config invertYAxisConf = this.workflowRunner.getModuleConfig().selectOne(where("id", startModule.getId()).and("key","invertYAxis"));
        boolean invertYAxis = invertYAxisConf == null || invertYAxisConf.getValue().equals("yes");
        log("invertYAxis = %b", invertYAxis);

        // sort imageTasks by image position
        List<Task> imageTasks = this.workflowRunner.getTaskStatus().select(
                where("moduleId", startModule.getId()));
        Map<String,Task> imageTaskPosIdx = new TreeMap<String,Task>(new ImageLabelComparator());
        for (Task imageTask : imageTasks) {
            Config slideIdConf = this.workflowRunner.getTaskConfig().selectOne(
                    where("id", imageTask.getId()).and("key", "slideId"));
            if (poolSlide == null || new Integer(slideIdConf.getValue()).equals(poolSlide.getSlideId())) {
                Config imageLabelConf = this.workflowRunner.getTaskConfig().selectOne(
                        where("id", imageTask.getId()).and("key", "imageLabel"));
                if (imageLabelConf != null) {
                    imageTaskPosIdx.put(imageLabelConf.getValue(), imageTask);
                }
            }
        }
        imageTasks.clear();
        imageTasks.addAll(imageTaskPosIdx.values());
        log("imageTasks = %s", imageTasks);

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
                    log("Getting image size for image %s", image);
                    ImagePlus imp = null;
                    try { imp = image.getImagePlus(acqDao); }
                    catch (Throwable e) {
                        StringWriter sw = new StringWriter();
                        e.printStackTrace(new PrintWriter(sw));
                        log("Couldn't retrieve image %s from the image cache!%n%s", image, sw);
                    }
                    if (imp != null) {
                        imageWidth = imp.getWidth();
                        imageHeight = imp.getHeight();
                    }
                }
            }
        }
        Double minX = minX_, minY = minY_;
        log("minX = %f, minY = %f, maxX = %f, maxY = %f, imageWidth = %d, imageHeight = %d", 
                minX, minY, maxX, maxY, imageWidth, imageHeight);
        int slideWidthPx = (int)Math.floor(((maxX - minX) / pixelSize) + (double)imageWidth);
        int slideHeightPx = (int)Math.floor(((maxY - minY) / pixelSize) + (double)imageHeight);
        log("slideWidthPx = %d, slideHeightPx = %d", slideWidthPx, slideHeightPx);
        
        // this is the scale factor for creating the thumbnail images
        double scaleFactor = (double)SLIDE_PREVIEW_WIDTH / (double)slideWidthPx;
        log("scaleFactor = %f", scaleFactor);
        int slidePreviewHeight = (int)Math.floor(scaleFactor * slideHeightPx);
        log("slidePreviewHeight = %d", slidePreviewHeight);
        
        ImagePlus slideThumb = NewImage.createRGBImage("slideThumb", SLIDE_PREVIEW_WIDTH, slidePreviewHeight, 1, NewImage.FILL_WHITE);
        Map<Integer,Roi> imageRois = new LinkedHashMap<Integer,Roi>();
        List<Roi> roiRois = new ArrayList<Roi>();
        Map().attr("name",String.format("map-%s-%s", startModule.getId(), slideId)).with(()->{
            for (Task task : imageTasks) {
                Config imageIdConf = this.workflowRunner.getTaskConfig().selectOne(
                        where("id", new Integer(task.getId()).toString()).and("key", "imageId"));
                if (imageIdConf != null) {
                    MultiStagePosition msp = getMsp(task);

                    // Get a thumbnail of the image
                    Image image = imageDao.selectOneOrDie(where("id", new Integer(imageIdConf.getValue())));
                    log("Working on image; %s", image);
                    ImagePlus imp = null;
                    try { imp = image.getImagePlus(acqDao); }
                    catch (Throwable e) {
                        StringWriter sw = new StringWriter();
                        e.printStackTrace(new PrintWriter(sw));
                        log("Couldn't retrieve image %s from the image cache!%n%s", image, sw);
                    }
                    if (imp != null) {
                        int width = imp.getWidth(), height = imp.getHeight();
                        log("Image width: %d, height: %d", width, height);
                        imp.getProcessor().setInterpolationMethod(ImageProcessor.BILINEAR);
                        imp.setProcessor(imp.getTitle(), imp.getProcessor().resize(
                                (int)Math.floor(imp.getWidth() * scaleFactor), 
                                (int)Math.floor(imp.getHeight() * scaleFactor)));
                        log("Resized image width: %d, height: %d", imp.getWidth(), imp.getHeight());
                        
                        int xloc = (int)Math.floor(((msp.getX() - minX) / pixelSize));
                        int xlocInvert = invertXAxis? slideWidthPx - (xloc + width) : xloc;
                        int xlocScale = (int)Math.floor(xlocInvert * scaleFactor);
                        log("xloc = %d, xlocInvert = %d, xlocScale = %d", xloc, xlocInvert, xlocScale);
                        int yloc = (int)Math.floor(((msp.getY() - minY) / pixelSize));
                        int ylocInvert = invertYAxis? slideHeightPx - (yloc + height) : yloc;
                        int ylocScale = (int)Math.floor(ylocInvert * scaleFactor);
                        log("yloc = %d, ylocInvert = %d, ylocScale = %d", yloc, ylocInvert, ylocScale);

                        // draw the thumbnail image
                        slideThumb.getProcessor().copyBits(imp.getProcessor(), xlocScale, ylocScale, Blitter.COPY);

                        // save the image ROI for the image map
                        Roi imageRoi = new Roi(xlocScale, ylocScale, imp.getWidth(), imp.getHeight()); 
                        imageRoi.setName(image.getName());
                        imageRoi.setProperty("id", new Integer(image.getId()).toString());
                        imageRois.put(image.getId(), imageRoi);
                        log("imageRoi = %s", imageRoi);

                        for (ROI roi : roiDao.select(where("imageId", image.getId()))) {
                            int roiX = (int)Math.floor(xlocScale + (roi.getX1() * scaleFactor));
                            int roiY = (int)Math.floor(ylocScale + (roi.getY1() * scaleFactor));
                            int roiWidth = (int)Math.floor((roi.getX2()-roi.getX1()+1) * scaleFactor);
                            int roiHeight = (int)Math.floor((roi.getY2()-roi.getY1()+1) * scaleFactor);
                            Roi r = new Roi(roiX, roiY, roiWidth, roiHeight);
                            r.setName(roi.toString());
                            r.setProperty("id", new Integer(roi.getId()).toString());
                            r.setStrokeColor(new Color(1f, 0f, 0f, 0.4f));
                            r.setStrokeWidth(0.4);
                            roiRois.add(r);
                            log("roiRoi = %s", r);
                        }
                    }
                }
            }

            // write the ROI areas first so they take precedence
            for (Roi roi : roiRois) {
                Area().attr("shape","rect").
                       attr("coords", String.format("%d,%d,%d,%d", 
                               (int)Math.floor(roi.getXBase()), 
                               (int)Math.floor(roi.getYBase()), 
                               (int)Math.floor(roi.getXBase()+roi.getFloatWidth()), 
                               (int)Math.floor(roi.getYBase()+roi.getFloatHeight()))).
                        attr("href", String.format("#area-ROI-%s", roi.getProperty("id"))).
                        attr("title", roi.getName());
            }
            // next write the image ROIs
            for (Roi roi : imageRois.values()) {
                Area().attr("shape", "rect"). 
                       attr("coords", String.format("%d,%d,%d,%d",
                               (int)Math.floor(roi.getXBase()), 
                               (int)Math.floor(roi.getYBase()), 
                               (int)Math.floor(roi.getXBase()+roi.getFloatWidth()), 
                               (int)Math.floor(roi.getYBase()+roi.getFloatHeight()))).
                       attr("title", roi.getName()).
                       attr("onclick", String.format("report.showImage(%d)", new Integer(roi.getProperty("id"))));
            }
            // now draw the ROI rois in red
            for (Roi roiRoi : roiRois) {
                slideThumb.getProcessor().setColor(roiRoi.getStrokeColor());
                slideThumb.getProcessor().draw(roiRoi);
            }
        });

        // write the slide thumbnail as an embedded HTML image.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try { ImageIO.write(slideThumb.getBufferedImage(), "jpg", baos); } 
        catch (IOException e) {throw new RuntimeException(e);}
        Img().attr("src", String.format("data:image/jpg;base64,%s", 
                Base64.getMimeEncoder().encodeToString(baos.toByteArray()))).
                attr("width", slideThumb.getWidth()).
                attr("height", slideThumb.getHeight()).
                attr("usemap", String.format("#map-%s-%s", startModule.getId(), slideId)).
                attr("class","map").
                attr("style", "border: 1px solid black");
        
        // now render the individual ROI sections
        for (Task task : imageTasks) {
            Config imageIdConf = this.workflowRunner.getTaskConfig().selectOne(
                    where("id", new Integer(task.getId()).toString()).and("key", "imageId"));
            if (imageIdConf != null) {
                Image image = imageDao.selectOneOrDie(where("id", new Integer(imageIdConf.getValue())));

                // make sure this image was included in the slide thumbnail image
                if (!imageRois.containsKey(image.getId())) continue;

                List<ROI> rois = roiDao.select(where("imageId", image.getId())); 
                Collections.sort(rois, (a,b)->a.getId()-b.getId());
                for (ROI roi : rois) {
                    log("Working on ROI: %s", roi);
                    Hr();
                    A().attr("name", String.format("area-ROI-%s", roi.getId())).with(()->{
                        H2().text(String.format("Image %s, ROI %s", image, roi));
                    });
                    
                    // go through each attached SlideImager and look for MSP's with an ROI property
                    // that matches this ROI's ID.
                    for (Map.Entry<WorkflowModule, List<WorkflowModule>> entry : roiImagers.entrySet()) {
                        for (WorkflowModule imager : entry.getValue()) {
                            // get the hires pixel size for this imager
                            Config hiResPixelSizeConf = this.workflowRunner.getModuleConfig().selectOne(where("id", imager.getId()).and("key","pixelSize"));
                            Double hiResPixelSize = hiResPixelSizeConf != null? new Double(hiResPixelSizeConf.getValue()) : ROIFinderDialog.DEFAULT_HIRES_PIXEL_SIZE_UM;
                            log("hiResPixelSize = %f", hiResPixelSize);

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
                                                log("Getting image size for image %s", image2);
                                                ImagePlus imp = null;
                                                try { imp = image2.getImagePlus(acqDao); }
                                                catch (Throwable e) {
                                                    StringWriter sw = new StringWriter();
                                                    e.printStackTrace(new PrintWriter(sw));
                                                    log("Couldn't retrieve image %s from the image cache!%n%s", image2, sw);
                                                }
                                                if (imp != null) {
                                                    imageWidth2 = imp.getWidth();
                                                    imageHeight2 = imp.getHeight();
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Double minX2 = minX2_, minY2 = minY2_;
                            log("minX2 = %f, minY2 = %f, maxX2 = %f, maxY2 = %f, imageWidth2 = %d, imageHeight2 = %d",
                                    minX2, minY2, maxX2, maxY2, imageWidth2, imageHeight2);
                            if (!imagerTasks.isEmpty()) {
                                int gridWidthPx = (int)Math.floor(((maxX2 - minX2_) / hiResPixelSize) + (double)imageWidth2);
                                int gridHeightPx = (int)Math.floor(((maxY2 - minY2_) / hiResPixelSize) + (double)imageHeight2);
                                log("gridWidthPx = %d, gridHeightPx = %d", gridWidthPx, gridHeightPx);
                                
                                // this is the scale factor for creating the thumbnail images
                                double gridScaleFactor = (double)ROI_GRID_PREVIEW_WIDTH / (double)gridWidthPx;
                                int gridPreviewHeight = (int)Math.floor(gridScaleFactor * gridHeightPx);
                                log("gridScaleFactor = %f, gridPreviewHeight = %d", gridScaleFactor, gridPreviewHeight);

                                ImagePlus roiGridThumb = NewImage.createRGBImage(
                                        String.format("roiGridThumb-%s-ROI%d", imager.getId(), roi.getId()), 
                                        ROI_GRID_PREVIEW_WIDTH, 
                                        gridPreviewHeight, 
                                        1, 
                                        NewImage.FILL_WHITE);
                                log("roiGridThumb: width=%d, height=%d", roiGridThumb.getWidth(), roiGridThumb.getHeight());
                                
                                Table().attr("class","table table-bordered table-hover table-striped").
                                    with(()->{
                                    Thead().with(()->{
                                        Tr().with(()->{
                                            Th().text("Channel, Slice, Frame");
                                            Th().text("Source ROI Cutout");
                                            Th().text("Tiled ROI Images");
                                            Th().text("Stitched ROI Image");
                                        });
                                    });
                                    Tbody().with(()->{
                                        for (Map.Entry<String,List<Task>> imagerTaskEntry : imagerTasks.entrySet()) {
                                            String imageLabel = imagerTaskEntry.getKey();
                                            int[] indices = MDUtils.getIndices(imageLabel);
                                            int channel = indices[0], slice = indices[1], frame = indices[2];
                                            log("Working on channel %d, slice %d, frame %d", channel, slice, frame);

                                            Tr().with(()->{
                                                Th().text(String.format("Channel %d, Slice %d, Frame %d", channel, slice, frame));
                                                Td().with(()->{
                                                    ImagePlus imp = null;
                                                    try { imp = image.getImagePlus(acqDao); }
                                                    catch (Throwable e) {
                                                        StringWriter sw = new StringWriter();
                                                        e.printStackTrace(new PrintWriter(sw));
                                                        log("Couldn't retrieve image %s from the image cache!%n%s", image, sw);
                                                    }
                                                    if (imp != null) {
                                                        imp.setRoi(new Roi(roi.getX1(), roi.getY1(), roi.getX2()-roi.getX1()+1, roi.getY2()-roi.getY1()+1));

                                                        double roiScaleFactor = (double)ROI_GRID_PREVIEW_WIDTH / (double)imp.getWidth();
                                                        int roiPreviewHeight = (int)Math.floor(imp.getHeight() * roiScaleFactor);
                                                        imp.setProcessor(imp.getTitle(), imp.getProcessor().crop().resize(
                                                                ROI_GRID_PREVIEW_WIDTH, 
                                                                roiPreviewHeight));

                                                        ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
                                                        try { ImageIO.write(imp.getBufferedImage(), "jpg", baos2); } 
                                                        catch (IOException e) {throw new RuntimeException(e);}
                                                        Img().attr("src", String.format("data:image/jpg;base64,%s", 
                                                                    Base64.getMimeEncoder().encodeToString(baos2.toByteArray()))).
                                                                attr("width", imp.getWidth()).
                                                                attr("height", imp.getHeight()).
                                                                attr("title", roi.toString()).
                                                                attr("onclick", String.format("report.showImage(%d)", image.getId()));
                                                    }
                                                });
                                                Td().with(()->{
                                                    Map().attr("name", String.format("map-roi-%s-ROI%d", imager.getId(), roi.getId())).with(()->{
                                                        for (Task imagerTask : imagerTaskEntry.getValue()) {
                                                            Config imageIdConf2 = this.workflowRunner.getTaskConfig().selectOne(
                                                                    where("id", new Integer(imagerTask.getId()).toString()).and("key", "imageId"));
                                                            if (imageIdConf2 != null) {
                                                                MultiStagePosition msp = getMsp(imagerTask);

                                                                // Get a thumbnail of the image
                                                                Image image2 = imageDao.selectOneOrDie(where("id", new Integer(imageIdConf2.getValue())));
                                                                ImagePlus imp = null; 
                                                                try { imp = image2.getImagePlus(acqDao); } 
                                                                catch (Throwable e) {
                                                                    StringWriter sw = new StringWriter();
                                                                    e.printStackTrace(new PrintWriter(sw));
                                                                    log("Couldn't retrieve image %s from the image cache!%n%s", image2, sw);
                                                                }
                                                                if (imp != null) {
                                                                    int width = imp.getWidth(), height = imp.getHeight();
                                                                    log("imp: width=%d, height=%d", width, height);
                                                                    imp.getProcessor().setInterpolationMethod(ImageProcessor.BILINEAR);
                                                                    imp.setProcessor(imp.getTitle(), imp.getProcessor().resize(
                                                                            (int)Math.floor(imp.getWidth() * gridScaleFactor), 
                                                                            (int)Math.floor(imp.getHeight() * gridScaleFactor)));
                                                                    log("imp: resized width=%d, height=%d", imp.getWidth(), imp.getHeight());

                                                                    int xloc = (int)Math.floor((msp.getX() - minX2) / hiResPixelSize);
                                                                    int xlocInvert = invertXAxis? gridWidthPx - (xloc + width) : xloc;
                                                                    int xlocScale = (int)Math.floor(xlocInvert * gridScaleFactor);
                                                                    log("xloc=%d, xlocInvert=%d, xlocScale=%d", xloc, xlocInvert, xlocScale);
                                                                    int yloc = (int)Math.floor((msp.getY() - minY2) / hiResPixelSize);
                                                                    int ylocInvert = invertYAxis? gridHeightPx - (yloc + height) : yloc;
                                                                    int ylocScale = (int)Math.floor(ylocInvert * gridScaleFactor);
                                                                    log("yloc=%d, ylocInvert=%d, ylocScale=%d", yloc, ylocInvert, ylocScale);

                                                                    roiGridThumb.getProcessor().copyBits(imp.getProcessor(), xlocScale, ylocScale, Blitter.COPY);
                                                                    
                                                                    Roi tileRoi = new Roi(xlocScale, ylocScale, imp.getWidth(), imp.getHeight());
                                                                    // make the tile image clickable
                                                                    Area().attr("shape", "rect"). 
                                                                            attr("coords", String.format("%d,%d,%d,%d", 
                                                                                    (int)Math.floor(tileRoi.getXBase()), 
                                                                                    (int)Math.floor(tileRoi.getYBase()),
                                                                                    (int)Math.floor(tileRoi.getXBase()+tileRoi.getFloatWidth()),
                                                                                    (int)Math.floor(tileRoi.getYBase()+tileRoi.getFloatHeight()))).
                                                                            attr("title", image2.getName()).
                                                                            attr("onclick", String.format("report.showImage(%d)", image2.getId()));
                                                                }
                                                            }
                                                        }
                                                    });

                                                    // write the grid thumbnail as an embedded HTML image.
                                                    ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
                                                    try { ImageIO.write(roiGridThumb.getBufferedImage(), "jpg", baos2); } 
                                                    catch (IOException e) {throw new RuntimeException(e);}
                                                    Img().attr("src", String.format("data:image/jpg;base64,%s", 
                                                                Base64.getMimeEncoder().encodeToString(baos2.toByteArray()))).
                                                            attr("width", roiGridThumb.getWidth()).
                                                            attr("height", roiGridThumb.getHeight()).
                                                            attr("class", "map").
                                                            attr("usemap", String.format("#map-%s-ROI%d", imager.getId(), roi.getId()));
                                                });
                                                Td().with(()->{
                                                    // get the downstream stitcher tasks
                                                    Set<Task> stitcherTasks = new HashSet<Task>();
                                                    for (Task imagerTask : imagerTaskEntry.getValue()) {
                                                        for (TaskDispatch td : this.workflowRunner.getTaskDispatch().select(where("parentTaskId", imagerTask.getId()))) {
                                                            Task stitcherTask = this.workflowRunner.getTaskStatus().selectOneOrDie(
                                                                    where("id", td.getTaskId()));
                                                            if (!this.workflowRunner.getModuleConfig().select(
                                                                    where("id", stitcherTask.getModuleId()).
                                                                    and("key", "canStitchImages").
                                                                    and("value", "yes")).isEmpty() &&
                                                                 stitcherTask.getStatus().equals(Status.SUCCESS)) 
                                                            {
                                                                stitcherTasks.add(stitcherTask);
                                                            }
                                                        }
                                                    }
                                                    for (Task stitcherTask : stitcherTasks) {
                                                        log("Working on stitcher task: %s", stitcherTask);
                                                        Config stitchedImageFile = this.workflowRunner.getTaskConfig().selectOne(
                                                                where("id", new Integer(stitcherTask.getId()).toString()).and("key", "stitchedImageFile"));
                                                        log("stitchedImageFile = %s", stitchedImageFile.getValue());
                                                        if (stitchedImageFile != null && new File(stitchedImageFile.getValue()).exists()) {
                                                            // Get a thumbnail of the image
                                                            ImagePlus imp = new ImagePlus(stitchedImageFile.getValue());
                                                            log("stitchedImage width = %d, height = %d", imp.getWidth(), imp.getHeight());

                                                            double stitchScaleFactor = (double)ROI_GRID_PREVIEW_WIDTH / (double)imp.getWidth();
                                                            log("stitchScaleFactor = %f", stitchScaleFactor);
                                                            int stitchPreviewHeight = (int)Math.floor(imp.getHeight() * stitchScaleFactor);
                                                            log("stitchPreviewHeight = %d", stitchPreviewHeight);
                                                            imp.getProcessor().setInterpolationMethod(ImageProcessor.BILINEAR);
                                                            imp.setProcessor(imp.getTitle(), imp.getProcessor().resize(
                                                                    ROI_GRID_PREVIEW_WIDTH, 
                                                                    stitchPreviewHeight));
                                                            log("resized stitched image width=%d, height=%d", imp.getWidth(), imp.getHeight());

                                                            // write the stitched thumbnail as an embedded HTML image.
                                                            ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
                                                            try { ImageIO.write(imp.getBufferedImage(), "jpg", baos2); } 
                                                            catch (IOException e) {throw new RuntimeException(e);}
                                                            Img().attr("src", String.format("data:image/jpg;base64,%s", 
                                                                        Base64.getMimeEncoder().encodeToString(baos2.toByteArray()))).
                                                                    attr("width", imp.getWidth()).
                                                                    attr("height", imp.getHeight()).
                                                                    attr("title", stitchedImageFile.getValue()).
                                                                    attr("onclick", String.format("report.showImageFile(\"%s\")",  
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
        //log("called showImage(imageId=%d)", imageId);
        Dao<Image> imageDao = this.workflowRunner.getInstanceDb().table(Image.class);
        Dao<Acquisition> acqDao = this.workflowRunner.getInstanceDb().table(Acquisition.class);
        Image image = imageDao.selectOneOrDie(where("id", imageId));
        ImagePlus imp = image.getImagePlus(acqDao);
        imp.show();
    }

    public void showImageFile(String imagePath) {
        //log("called showImageFile(imagePath=%s)", imagePath);
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
