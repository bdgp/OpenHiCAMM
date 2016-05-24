package org.bdgp.OpenHiCAMM;

import java.awt.Color;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.bdgp.OpenHiCAMM.DB.Acquisition;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.Image;
import org.bdgp.OpenHiCAMM.DB.ModuleConfig;
import org.bdgp.OpenHiCAMM.DB.Pool;
import org.bdgp.OpenHiCAMM.DB.PoolSlide;
import org.bdgp.OpenHiCAMM.DB.ROI;
import org.bdgp.OpenHiCAMM.DB.Slide;
import org.bdgp.OpenHiCAMM.DB.SlidePosList;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.TaskConfig;
import org.bdgp.OpenHiCAMM.DB.TaskDispatch;
import org.bdgp.OpenHiCAMM.DB.WorkflowModule;
import org.bdgp.OpenHiCAMM.DB.Task.Status;
import org.bdgp.OpenHiCAMM.Modules.ROIFinderDialog;
import org.bdgp.OpenHiCAMM.Modules.SlideImager;
import org.bdgp.OpenHiCAMM.Modules.SlideImagerDialog;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Module;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Report;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudio;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.api.StagePosition;
import org.micromanager.utils.ImageLabelComparator;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMSerializationException;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageRoi;
import ij.gui.NewImage;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.process.Blitter;
import ij.process.ImageProcessor;
import javafx.scene.web.WebEngine;
import mmcorej.CMMCore;

import static org.bdgp.OpenHiCAMM.Util.where;
import static org.bdgp.OpenHiCAMM.Tag.T.*;

public class WorkflowReport implements Report {
    public static final int SLIDE_PREVIEW_WIDTH = 1280; 
    public static final int ROI_GRID_PREVIEW_WIDTH = 425; 
    public static final boolean DEBUG=true;

    private WorkflowRunner workflowRunner;
    private WebEngine webEngine;
    private String reportDir;
    private String reportIndex;
    private Integer prevPoolSlideId;
    private Map<Integer,Boolean> isLoaderInitialized;

    public void jsLog(String message) {
        IJ.log(String.format("[WorkflowReport:js] %s", message));
    }

    public void log(String message, Object... args) {
        if (DEBUG) {
            IJ.log(String.format("[WorkflowReport] %s", String.format(message, args)));
        }
    }
    
    @Override public void initialize(WorkflowRunner workflowRunner, WebEngine webEngine, String reportDir, String reportIndex) {
        this.workflowRunner = workflowRunner;
        this.webEngine = webEngine;
        this.reportDir = reportDir;
        this.reportIndex = reportIndex;
        isLoaderInitialized = new HashMap<Integer,Boolean>();
    }
    
    @Override
    public void runReport() {
        Dao<Pool> poolDao = this.workflowRunner.getInstanceDb().table(Pool.class);
        Dao<PoolSlide> psDao = this.workflowRunner.getInstanceDb().table(PoolSlide.class);

        // Find SlideImager modules where there is no associated posListModuleId module config
        // This is the starting SlideImager module.
        int[] reportCounter = new int[]{1};
        Map<String,Runnable> runnables = new LinkedHashMap<String,Runnable>();
        Tag index = Html().indent().with(()->{
            Head().with(()->{
                try {
                    // CSS
                    Style().raw(Resources.toString(Resources.getResource("bootstrap.min.css"), Charsets.UTF_8));
                    Style().raw(Resources.toString(Resources.getResource("bootstrap-theme.min.css"), Charsets.UTF_8));
                    Style().raw(Resources.toString(Resources.getResource("jquery.powertip.min.css"), Charsets.UTF_8));
                    // Javascript
                    Script().raw(Resources.toString(Resources.getResource("jquery-2.1.4.min.js"), Charsets.UTF_8));
                    Script().raw(Resources.toString(Resources.getResource("bootstrap.min.js"), Charsets.UTF_8));
                    Script().raw(Resources.toString(Resources.getResource("jquery.maphilight.js"), Charsets.UTF_8));
                    Script().raw(Resources.toString(Resources.getResource("jquery.powertip.min.js"), Charsets.UTF_8));
                    Script().raw(Resources.toString(Resources.getResource("WorkflowReport.js"), Charsets.UTF_8));
                } 
                catch (Exception e) {throw new RuntimeException(e);}
            });
            Body().with(()->{
                SLIDE_IMAGERS:
                for (Config canImageSlides : this.workflowRunner.getModuleConfig().select(
                        where("key","canImageSlides").
                        and("value", "yes"))) 
                {
                    WorkflowModule slideImager = this.workflowRunner.getWorkflow().selectOneOrDie(
                            where("id", canImageSlides.getId()));
                    log("Working on slideImager: %s", slideImager);
                    if (workflowRunner.getModuleConfig().selectOne(
                            where("id", slideImager.getId()).
                            and("key", "posListModule")) == null) 
                    {
                       // get the loader module
                        Integer loaderModuleId = slideImager.getParentId();
                        while (loaderModuleId != null) {
                            WorkflowModule loaderModule = this.workflowRunner.getWorkflow().selectOneOrDie(
                                    where("id", loaderModuleId));
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
                                            String reportFile = String.format("report%03d.%s.cartridge%d.pos%02d.slide%05d.html", 
                                                        reportCounter[0]++, 
                                                        slideImager.getName(),
                                                        ps.getCartridgePosition(), ps
                                                        .getSlidePosition(), 
                                                        ps.getSlideId());
                                            P().with(()->{
                                                A(String.format("Module %s, Slide %s", slideImager.getName(), ps)).
                                                    attr("href", reportFile);
                                            });
                                            Integer loaderModuleId_ = loaderModuleId;
                                            runnables.put(reportFile, ()->{
                                                log("Calling runReport(startModule=%s, poolSlide=%s, loaderModuleId=%s)", 
                                                        slideImager, ps, loaderModuleId_);
                                                WorkflowModule lm = this.workflowRunner.getWorkflow().selectOneOrDie(where("id", loaderModuleId_));
                                                runReport(slideImager, ps, lm);
                                            });
                                        }
                                        continue SLIDE_IMAGERS;
                                    }
                                }
                            }
                            loaderModuleId = loaderModule.getParentId();
                        }
                        String reportFile = String.format("report%03d.%s.html", reportCounter[0]++, slideImager.getName()); 
                        P().with(()->{
                            A(String.format("Module %s", slideImager)).
                                attr("href", reportFile);
                        });
                        runnables.put(reportFile, ()->{
                            log("Calling runReport(startModule=%s, poolSlide=null, loaderModuleId=null)", slideImager);
                            runReport(slideImager, null, null);
                        });
                    }
                }
            });
        });

        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<?>> futures = new ArrayList<>();
        for (Map.Entry<String,Runnable> entry : runnables.entrySet()) {
            String reportFile = entry.getKey();
            Runnable runnable = entry.getValue();

            futures.add(pool.submit(()->{
                Html().indent().with(()->{
                    Head().with(()->{
                        try {
                            // CSS
                            Style().raw(Resources.toString(Resources.getResource("bootstrap.min.css"), Charsets.UTF_8));
                            Style().raw(Resources.toString(Resources.getResource("bootstrap-theme.min.css"), Charsets.UTF_8));
                            Style().raw(Resources.toString(Resources.getResource("jquery.powertip.min.css"), Charsets.UTF_8));
                            // Javascript
                            Script().raw(Resources.toString(Resources.getResource("jquery-2.1.4.min.js"), Charsets.UTF_8));
                            Script().raw(Resources.toString(Resources.getResource("bootstrap.min.js"), Charsets.UTF_8));
                            Script().raw(Resources.toString(Resources.getResource("jquery.maphilight.js"), Charsets.UTF_8));
                            Script().raw(Resources.toString(Resources.getResource("jquery.powertip.min.js"), Charsets.UTF_8));
                            Script().raw(Resources.toString(Resources.getResource("WorkflowReport.js"), Charsets.UTF_8));
                        } 
                        catch (Exception e) {throw new RuntimeException(e);}
                    });
                    Body().with(()->{
                        runnable.run();
                    });
                }).write(new File(reportDir, reportFile));
            }));
        }
        for (Future<?> f : futures) { 
            try { f.get(); } 
            catch (InterruptedException | ExecutionException e) { 
                throw new RuntimeException(e);
            } 
        }

        // write the index last
        index.write(new File(this.reportDir, this.reportIndex));
    }

    private void runReport(WorkflowModule startModule, PoolSlide poolSlide, WorkflowModule loaderModule) {
        log("Called runReport(startModule=%s, poolSlide=%s)", startModule, poolSlide);

        Dao<Slide> slideDao = this.workflowRunner.getInstanceDb().table(Slide.class);
        Dao<Image> imageDao = this.workflowRunner.getInstanceDb().table(Image.class);
        Dao<Acquisition> acqDao = this.workflowRunner.getInstanceDb().table(Acquisition.class);
        Dao<ROI> roiDao = this.workflowRunner.getInstanceDb().table(ROI.class);
        Dao<SlidePosList> slidePosListDao = this.workflowRunner.getInstanceDb().table(SlidePosList.class);
        
        // get a ROI ID -> moduleId(s) mapping from the slideposlist
        Map<Integer, Set<Integer>> roiModules = new HashMap<>();
        for (SlidePosList spl : slidePosListDao.select()) {
            if (spl.getModuleId() != null) {
                PositionList posList = spl.getPositionList();
                for (MultiStagePosition msp : posList.getPositions()) {
                    String roiIdConf = msp.getProperty("ROI");
                    if (roiIdConf != null) {
                        Integer roiId = new Integer(roiIdConf);
                        if (!roiModules.containsKey(roiId)) roiModules.put(roiId, new HashSet<>());
                        roiModules.get(roiId).add(spl.getModuleId());
                    }
                }
            }
        }
        
        // get the associated hi res slide imager modules for each ROI finder module
        // ROIFinder module ID -> List of associated imager module IDs
        Map<String,Set<String>> roiImagers = new HashMap<>(); 
        for (Task task : workflowRunner.getTaskStatus().select(
                where("moduleId", startModule.getId()))) 
        {
            TaskConfig imageIdConf = workflowRunner.getTaskConfig().selectOne(
                    where("id", task.getId()).
                    and("key", "imageId"));
            if (imageIdConf != null) {
                for (ROI roi : roiDao.select(where("imageId", imageIdConf.getValue()))) {
                    Set<Integer> roiModuleSet = roiModules.get(roi.getId());
                    if (roiModuleSet == null) {
                        log(String.format("No ROI modules found for ROI %s!", roi));
                    }
                    else {
                        for (Integer roiModuleId : roiModuleSet) {
                            WorkflowModule roiModule = this.workflowRunner.getWorkflow().selectOne(where("id", roiModuleId));
                            if (roiModule != null) {
                                for (ModuleConfig posListModuleConf : this.workflowRunner.getModuleConfig().select(
                                        where("key", "posListModule").
                                        and("value", roiModule.getName()))) 
                                {
                                    if (this.workflowRunner.getModuleConfig().selectOne(
                                            where("id", posListModuleConf.getId()).
                                            and("key", "canImageSlides").
                                            and("value", "yes")) != null) 
                                    {
                                        WorkflowModule imagerModule = this.workflowRunner.getWorkflow().selectOne(
                                                where("id", posListModuleConf.getId()));
                                        if (imagerModule != null) {
                                            if (!roiImagers.containsKey(roiModule.getName())) roiImagers.put(roiModule.getName(), new HashSet<>());
                                            roiImagers.get(roiModule.getName()).add(imagerModule.getName());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // display the title
        Slide slide;
        String slideName;
        if (poolSlide != null) {
            slide = slideDao.selectOneOrDie(where("id", poolSlide.getSlideId()));
            slideName = slide.getName();
            String title = String.format("SlideImager %s, Slide %s, Pool %d, Cartridge %d, Slide Position %d", 
                    startModule.getName(), 
                    slide.getName(), 
                    poolSlide.getPoolId(), 
                    poolSlide.getCartridgePosition(), 
                    poolSlide.getSlidePosition());

            P().with(()->{
                A("Back to Index Page").attr("href", this.reportIndex);
            });
            A().attr("name", String.format("report-%s-PS%s", startModule.getName(), poolSlide.getId()));
            H1().text(title);
            log("title = %s", title);
        }
        else {
            slide = null;
            slideName = "slide";
            String title = String.format("SlideImager %s", startModule.getName());
            A().attr("name", String.format("report-%s", startModule.getName()));
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
        Integer imageWidth_ = null, imageHeight_ = null;
        for (Task task : imageTasks) {
            MultiStagePosition msp = getMsp(task);

            if (minX_ == null || msp.getX() < minX_) minX_ = msp.getX();
            if (maxX == null || msp.getX() > maxX) maxX = msp.getX();
            if (minY_ == null || msp.getY() < minY_) minY_ = msp.getY();
            if (maxY == null || msp.getY() > maxY) maxY = msp.getY();
            if (imageWidth_ == null || imageHeight_ == null) {
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
                        imageWidth_ = imp.getWidth();
                        imageHeight_ = imp.getHeight();
                    }
                }
            }
        }
        Double minX = minX_, minY = minY_;
        Integer imageWidth = imageWidth_, imageHeight = imageHeight_;
        log("minX = %f, minY = %f, maxX = %f, maxY = %f, imageWidth = %d, imageHeight = %d", 
                minX, minY, maxX, maxY, imageWidth, imageHeight);

        if (minX != null && minY != null && maxX != null && maxY != null && imageWidth != null && imageHeight != null) {
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
            Map().attr("name",String.format("map-%s-%s", startModule.getName(), slideName)).with(()->{
                // TODO: parallelize
                for (Task task : imageTasks) {
                    Config imageIdConf = this.workflowRunner.getTaskConfig().selectOne(
                            where("id", task.getId()).and("key", "imageId"));
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
                           //attr("title", roi.getName()).
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
                    attr("usemap", String.format("#map-%s-%s", startModule.getName(), slideName)).
                    attr("class","map stageCoords").
                    attr("data-min-x", minX - imageWidth / 2.0 * pixelSize * (invertXAxis? -1.0 : 1.0)).
                    attr("data-max-x", maxX + imageWidth / 2.0 * pixelSize * (invertXAxis? -1.0 : 1.0)).
                    attr("data-min-y", minY - imageHeight / 2.0 * pixelSize * (invertYAxis? -1.0 : 1.0)).
                    attr("data-max-y", maxY + imageHeight / 2.0 * pixelSize * (invertYAxis? -1.0 : 1.0)).
                    attr("style", "border: 1px solid black");
            
            // now render the individual ROI sections
            // TODO: parallelize
            for (Task task : imageTasks) {
                Config imageIdConf = this.workflowRunner.getTaskConfig().selectOne(
                        where("id", task.getId()).and("key", "imageId"));
                if (imageIdConf != null) {
                    Image image = imageDao.selectOneOrDie(where("id", new Integer(imageIdConf.getValue())));

                    // make sure this image was included in the slide thumbnail image
                    if (!imageRois.containsKey(image.getId())) continue;

                    MultiStagePosition msp = getMsp(task);
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
                        for (Map.Entry<String, Set<String>> entry : roiImagers.entrySet()) {
                            for (String imagerModuleName : entry.getValue()) {
                                WorkflowModule imager = this.workflowRunner.getWorkflow().selectOneOrDie(
                                        where("name", imagerModuleName));
                                // get the hires pixel size for this imager
                                Config hiResPixelSizeConf = this.workflowRunner.getModuleConfig().selectOne(where("id", imager.getId()).and("key","pixelSize"));
                                Double hiResPixelSize = hiResPixelSizeConf != null? new Double(hiResPixelSizeConf.getValue()) : ROIFinderDialog.DEFAULT_HIRES_PIXEL_SIZE_UM;
                                log("hiResPixelSize = %f", hiResPixelSize);

                                // determine the stage coordinate bounds of this ROI tile grid.
                                // also get the image width and height of the acqusition.
                                Double minX2_=null, minY2_=null, maxX2_=null, maxY2_=null;
                                Integer imageWidth2_ = null, imageHeight2_ = null;
                                Map<String,List<Task>> imagerTasks = new TreeMap<String,List<Task>>(new ImageLabelComparator());
                                for (Task imagerTask : this.workflowRunner.getTaskStatus().select(where("moduleId", imager.getId()))) {
                                    MultiStagePosition imagerMsp = getMsp(imagerTask);
                                    if (imagerMsp.hasProperty("ROI") && imagerMsp.getProperty("ROI").equals(new Integer(roi.getId()).toString())) 
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
                                            
                                            if (minX2_ == null || imagerMsp.getX() < minX2_) minX2_ = imagerMsp.getX();
                                            if (minY2_ == null || imagerMsp.getY() < minY2_) minY2_ = imagerMsp.getY();
                                            if (maxX2_ == null || imagerMsp.getX() > maxX2_) maxX2_ = imagerMsp.getX();
                                            if (maxY2_ == null || imagerMsp.getY() > maxY2_) maxY2_ = imagerMsp.getY();
                                            if (imageWidth2_ == null || imageHeight2_ == null) {
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
                                                        imageWidth2_ = imp.getWidth();
                                                        imageHeight2_ = imp.getHeight();
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Double minX2 = minX2_, minY2 = minY2_, maxX2 = maxX2_, maxY2 = maxY2_; 
                                Integer imageWidth2 = imageWidth2_, imageHeight2 = imageHeight2_;
                                log("minX2 = %f, minY2 = %f, maxX2 = %f, maxY2 = %f, imageWidth2 = %d, imageHeight2 = %d",
                                        minX2, minY2, maxX2_, maxY2_, imageWidth2_, imageHeight2_);
                                if (!imagerTasks.isEmpty() && 
                                    minX2 != null && 
                                    minY2 != null && 
                                    maxX2_ != null && 
                                    maxY2_ != null && 
                                    imageWidth2_ != null && 
                                    imageHeight2_ != null) 
                                {
                                    int gridWidthPx = (int)Math.floor(((maxX2_ - minX2_) / hiResPixelSize) + (double)imageWidth2_);
                                    int gridHeightPx = (int)Math.floor(((maxY2_ - minY2_) / hiResPixelSize) + (double)imageHeight2_);
                                    log("gridWidthPx = %d, gridHeightPx = %d", gridWidthPx, gridHeightPx);
                                    
                                    // this is the scale factor for creating the thumbnail images
                                    double gridScaleFactor = (double)ROI_GRID_PREVIEW_WIDTH / (double)gridWidthPx;
                                    int gridPreviewHeight = (int)Math.floor(gridScaleFactor * gridHeightPx);
                                    log("gridScaleFactor = %f, gridPreviewHeight = %d", gridScaleFactor, gridPreviewHeight);

                                    ImagePlus roiGridThumb = NewImage.createRGBImage(
                                            String.format("roiGridThumb-%s-ROI%d", imager.getName(), roi.getId()), 
                                            ROI_GRID_PREVIEW_WIDTH, 
                                            gridPreviewHeight, 
                                            1, 
                                            NewImage.FILL_WHITE);
                                    log("roiGridThumb: width=%d, height=%d", roiGridThumb.getWidth(), roiGridThumb.getHeight());
                                    
                                    Table().attr("class","table table-bordered table-hover table-striped").
                                        with(()->{
                                        Thead().with(()->{
                                            Tr().with(()->{
                                                Th().text("Image Properties");
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
                                                    Th().with(()->{
                                                        P(String.format("Channel %d, Slice %d, Frame %d", channel, slice, frame)); 

                                                        // get the average stage position of all the tiled ROI images
                                                        int posCount = 0;
                                                        double xPos = 0.0;
                                                        double yPos = 0.0;
                                                        for (Task imagerTask : imagerTaskEntry.getValue()) {
                                                            MultiStagePosition imagerMsp = getMsp(imagerTask);
                                                            Config imageIdConf2 = this.workflowRunner.getTaskConfig().selectOne(
                                                                    where("id", imagerTask.getId()).and("key", "imageId"));
                                                            if (imageIdConf2 != null) {
                                                                for (int i=0; i<imagerMsp.size(); ++i) {
                                                                    StagePosition sp = imagerMsp.get(i);
                                                                    if (sp.numAxes == 2 && sp.stageName.compareTo(imagerMsp.getDefaultXYStage()) == 0) {
                                                                        xPos += sp.x;
                                                                        yPos += sp.y;
                                                                        ++posCount;
                                                                        break;
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        // add a link to load the slide and go to the stage position
                                                        if (posCount > 0) {
                                                            Double xPos_ = xPos / posCount;
                                                            Double yPos_ = yPos / posCount;
                                                            P().with(()->{
                                                                A().attr("href", "#").
                                                                    attr("onclick", 
                                                                        String.format("report.goToPosition(%d,%d,%f,%f)", 
                                                                                loaderModule.getId(),
                                                                                poolSlide != null? poolSlide.getId() : -1, 
                                                                                        xPos_, 
                                                                                        yPos_)).
                                                                    text(String.format("Go To Stage Position: (%.2f,%.2f)", xPos_, yPos_));
                                                            });

                                                            // add another link to put the slide back
                                                            if (poolSlide != null) {
                                                                P().with(()->{
                                                                    A().attr("href", "#").
                                                                        attr("onclick", String.format("report.returnSlide(%d)",
                                                                                loaderModule.getId())).
                                                                        text("Return slide to loader");
                                                                });
                                                            }
                                                        }
                                                    });
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

                                                            double roiScaleFactor = (double)ROI_GRID_PREVIEW_WIDTH / (double)(roi.getX2()-roi.getX1()+1);
                                                            int roiPreviewHeight = (int)Math.floor((roi.getY2()-roi.getY1()+1) * roiScaleFactor);
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
                                                                    attr("data-min-x", msp.getX() + (roi.getX1() - (imp.getWidth() / 2.0)) * pixelSize * (invertXAxis? -1.0 : 1.0)).
                                                                    attr("data-max-x", msp.getX() + (roi.getX2() - (imp.getWidth() / 2.0)) * pixelSize * (invertXAxis? -1.0 : 1.0)).
                                                                    attr("data-min-y", msp.getY() + (roi.getY1() - (imp.getHeight() / 2.0)) * pixelSize * (invertYAxis? -1.0 : 1.0)).
                                                                    attr("data-max-y", msp.getY() + (roi.getY2() - (imp.getHeight() / 2.0)) * pixelSize * (invertYAxis? -1.0 : 1.0)).
                                                                    //attr("title", roi.toString()).
                                                                    attr("class", "stageCoords").
                                                                    attr("onclick", String.format("report.showImage(%d)", image.getId()));
                                                        }
                                                    });
                                                    Td().with(()->{
                                                        List<Runnable> makeLinks = new ArrayList<>();
                                                        Map().attr("name", String.format("map-roi-%s-ROI%d", imager.getName(), roi.getId())).with(()->{
                                                            for (Task imagerTask : imagerTaskEntry.getValue()) {
                                                                Config imageIdConf2 = this.workflowRunner.getTaskConfig().selectOne(
                                                                        where("id", imagerTask.getId()).and("key", "imageId"));
                                                                if (imageIdConf2 != null) {
                                                                    MultiStagePosition imagerMsp = getMsp(imagerTask);

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

                                                                        int xloc = (int)Math.floor((imagerMsp.getX() - minX2) / hiResPixelSize);
                                                                        int xlocInvert = invertXAxis? gridWidthPx - (xloc + width) : xloc;
                                                                        int xlocScale = (int)Math.floor(xlocInvert * gridScaleFactor);
                                                                        log("xloc=%d, xlocInvert=%d, xlocScale=%d", xloc, xlocInvert, xlocScale);
                                                                        int yloc = (int)Math.floor((imagerMsp.getY() - minY2) / hiResPixelSize);
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
                                                                        
                                                                        makeLinks.add(()->{
                                                                            P().with(()->{
                                                                                A().attr("onclick",String.format("report.checkPosCalibration(%d,%d,%f,%f,%f,%f,%f,%f,%f,%f)",
                                                                                        image.getId(), 
                                                                                        image2.getId(), 
                                                                                        pixelSize,
                                                                                        hiResPixelSize,
                                                                                        invertXAxis? -1.0 : 1.0,
                                                                                        invertYAxis? -1.0 : 1.0,
                                                                                        msp.getX(), 
                                                                                        msp.getY(),
                                                                                        imagerMsp.getX(), 
                                                                                        imagerMsp.getY())).
                                                                                    attr("href","#").
                                                                                    text(String.format("Check pos calibration for image %s", image2.getName()));
                                                                            });
                                                                        });
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
                                                                attr("class", "map stageCoords").
                                                                attr("data-min-x", minX2 - imageWidth2 / 2.0 * hiResPixelSize * (invertXAxis? -1.0 : 1.0)).
                                                                attr("data-max-x", maxX2 + imageWidth2 / 2.0 * hiResPixelSize * (invertXAxis? -1.0 : 1.0)).
                                                                attr("data-min-y", minY2 - imageHeight2 / 2.0 * hiResPixelSize * (invertYAxis? -1.0 : 1.0)).
                                                                attr("data-max-y", maxY2 + imageHeight2 / 2.0 * hiResPixelSize * (invertYAxis? -1.0 : 1.0)).
                                                                attr("usemap", String.format("#map-roi-%s-ROI%d", imager.getName(), roi.getId()));
                                                        
                                                        for (Runnable r : makeLinks) {
                                                            r.run();
                                                        }
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
                                                                    where("id", stitcherTask.getId()).and("key", "stitchedImageFile"));
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
    }
    
    public void showImage(int imageId) {
        //log("called showImage(imageId=%d)", imageId);
        Dao<Image> imageDao = this.workflowRunner.getInstanceDb().table(Image.class);
        Dao<Acquisition> acqDao = this.workflowRunner.getInstanceDb().table(Acquisition.class);
        Image image = imageDao.selectOneOrDie(where("id", imageId));
        ImagePlus imp = image.getImagePlus(acqDao);
        imp.show();
    }
    
    public void goToPosition(Integer loaderModuleId, int poolSlideId, double xPos, double yPos) {
        synchronized(this) {
            Dao<PoolSlide> poolSlideDao = this.workflowRunner.getInstanceDb().table(PoolSlide.class);
            PoolSlide poolSlide = poolSlideId > 0? poolSlideDao.selectOne(where("id", poolSlideId)) : null;

            SwingUtilities.invokeLater(()->{
                Double xPos_ = xPos;
                Double yPos_ = yPos;
                PoolSlide poolSlide_ = poolSlide;
                Integer loaderModuleId_ = loaderModuleId;
                Integer poolSlideId_ = poolSlideId;
                Integer cartridgePosition = poolSlide_.getCartridgePosition();
                Integer slidePosition = poolSlide_.getSlidePosition();
                String slideMessage = poolSlide_ != null && loaderModuleId_ != null && prevPoolSlideId != poolSlideId_?
                                        String.format(" on cartridge %d, slide %d?", cartridgePosition, slidePosition) 
                                        : "?";
                String message = String.format("Would you like to move the stage to position (%.2f,%.2f)%s", 
                                xPos_, yPos_, slideMessage);
                if (JOptionPane.showConfirmDialog(null, 
                        message,
                        "Move To Position", 
                        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) 
                {
                    new Thread(()->{
                        // load the slide
                        if (poolSlideId_ > 0 && loaderModuleId_ != null && this.prevPoolSlideId != poolSlideId_) {
                            WorkflowModule loaderModule = this.workflowRunner.getWorkflow().selectOneOrDie(
                                    where("id", loaderModuleId_));
                            Module module = this.workflowRunner.getModuleInstances().get(loaderModule.getId());
                            if (module == null) throw new RuntimeException(String.format("Could not find instance for module %s!", loaderModule));

                            // init loader and scan for slides
                            if (this.isLoaderInitialized.get(loaderModuleId_) == null || !this.isLoaderInitialized.get(loaderModuleId_)) {
                                try {
                                    Method initSlideLoader = module.getClass().getDeclaredMethod("initSlideLoader", boolean.class);
                                    initSlideLoader.invoke(module, true);

                                    Method scanForSlides = module.getClass().getDeclaredMethod("scanForSlides");
                                    scanForSlides.invoke(module);
                                    
                                    this.isLoaderInitialized.put(loaderModuleId_, true);
                                } 
                                catch (Throwable e) { 
                                    StringWriter sw = new StringWriter();
                                    e.printStackTrace(new PrintWriter(sw));
                                    log("Couldn't scan for slides!%n%s", sw);
                                    return;
                                }
                            }
                            
                            // unload the previous slide first
                            if (this.prevPoolSlideId != null) {
                                PoolSlide prevPoolSlide = poolSlideDao.selectOneOrDie(where("id", prevPoolSlideId));
                                try {
                                    Method unloadSlide = module.getClass().getDeclaredMethod("unloadSlide", PoolSlide.class);
                                    unloadSlide.invoke(module, prevPoolSlide);
                                } 
                                catch (Throwable e) { 
                                    StringWriter sw = new StringWriter();
                                    e.printStackTrace(new PrintWriter(sw));
                                    log("Couldn't unload slide %s from stage!%n%s", prevPoolSlide, sw);
                                    return;
                                }
                            }
                            
                            // now load the slide
                            try {
                                Method loadSlide = module.getClass().getDeclaredMethod("loadSlide", PoolSlide.class);
                                loadSlide.invoke(module, poolSlide_);

                                // set previous pool slide to this slide
                                this.prevPoolSlideId = poolSlideId_;
                            } 
                            catch (Throwable e) { 
                                StringWriter sw = new StringWriter();
                                e.printStackTrace(new PrintWriter(sw));
                                log("Couldn't unload slide %s from stage!%n%s", poolSlide, sw);
                                return;
                            }
                        }
                        
                        // move the stage
                        CMMCore core = MMStudio.getInstance().getMMCore();
                        try { SlideImager.moveStage(this.getClass().getSimpleName(), core, xPos_, yPos_, null); } 
                        catch (Exception e) {
                            StringWriter sw = new StringWriter();
                            e.printStackTrace(new PrintWriter(sw));
                            log("Error while attempting to move stage to coordinates: (%.2f,%.2f)%n%s", xPos_, yPos_, sw);
                            return;
                        }
                        
                        // autofocus?
                        SwingUtilities.invokeLater(()->{
                            if (JOptionPane.showConfirmDialog(null, 
                                    "Autofocus now?",
                                    "Move To Position", 
                                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) 
                            {
                                new Thread(()->{
                                    MMStudio.getInstance().autofocusNow();
                                }).start();
                            }
                        });
                    }).start();
                }
            });
        }
    }
    
    public void returnSlide(Integer loaderModuleId) {
        synchronized(this) {
            if (this.prevPoolSlideId != null) {
                SwingUtilities.invokeLater(()->{
                    if (JOptionPane.showConfirmDialog(null, 
                            String.format("Would you like to return the slide to the loader?"),
                            "Return Slide to Stage", 
                            JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)  
                    {
                        new Thread(()->{
                            WorkflowModule loaderModule = this.workflowRunner.getWorkflow().selectOneOrDie(
                                    where("id", loaderModuleId));
                            Module module = this.workflowRunner.getModuleInstances().get(loaderModule.getId());
                            if (module == null) throw new RuntimeException(String.format("Could not find instance for module %s!", loaderModule));
                            
                            Dao<PoolSlide> poolSlideDao = this.workflowRunner.getInstanceDb().table(PoolSlide.class);
                            PoolSlide prevPoolSlide = poolSlideDao.selectOneOrDie(where("id", prevPoolSlideId));
                            try {
                                Method unloadSlide = module.getClass().getDeclaredMethod("unloadSlide", PoolSlide.class);
                                unloadSlide.invoke(module, prevPoolSlide);
                                
                                this.prevPoolSlideId = null;
                            } 
                            catch (Throwable e) { 
                                StringWriter sw = new StringWriter();
                                e.printStackTrace(new PrintWriter(sw));
                                log("Couldn't unload slide %s from stage!%n%s", prevPoolSlide, sw);
                                return;
                            }
                        }).start();
                    }
                });
            }
        }
    }

    public void showImageFile(String imagePath) {
        //log("called showImageFile(imagePath=%s)", imagePath);
        ImagePlus imp = new ImagePlus(imagePath);
        imp.show();
    }
    
    public void goToURL(String url) {
        try { url = Paths.get(this.reportDir).resolve(url).toUri().toURL().toString(); } 
        catch (MalformedURLException e) {throw new RuntimeException(e);}
        this.webEngine.load(url);
    }
    
    public void checkPosCalibration(
            int image1Id, 
            int image2Id, 
            double pixelSize, 
            double hiResPixelSize, 
            double invertX, 
            double invertY,
            double image1X,
            double image1Y,
            double image2X,
            double image2Y) 
    {
        Dao<Acquisition> acqDao = this.workflowRunner.getInstanceDb().table(Acquisition.class);
        Dao<Image> imageDao = this.workflowRunner.getInstanceDb().table(Image.class);
        Image image1 = imageDao.selectOneOrDie(where("id", image1Id));
        ImagePlus imp1 = image1.getImagePlus(acqDao);
        Image image2 = imageDao.selectOneOrDie(where("id", image2Id));
        ImagePlus imp2 = image2.getImagePlus(acqDao);
        
        imp2.setProcessor(imp2.getProcessor().resize(
                (int)Math.floor(imp2.getWidth() * (hiResPixelSize / pixelSize)), 
                (int)Math.floor(imp2.getHeight() * (hiResPixelSize / pixelSize))));
        
        int roiX = (int)Math.floor((image2X - image1X) / pixelSize * invertX + (imp1.getWidth() / 2.0) - (imp2.getWidth() / 2.0));
        int roiY = (int)Math.floor((image2Y - image1Y) / pixelSize * invertY + (imp1.getHeight() / 2.0) - (imp2.getHeight() / 2.0));
        int roiWidth = imp2.getWidth();
        int roiHeight = imp2.getHeight();
        ImageRoi roi = new ImageRoi(roiX, roiY, imp2.getProcessor());
        roi.setName(image2.getName());
        roi.setOpacity(0.3);

        Overlay overlayList = imp1.getOverlay();
        if (overlayList == null) overlayList = new Overlay();
        overlayList.add(roi);
        imp1.setOverlay(overlayList);

        // pop open the window
        imp1.setHideOverlay(false);
        imp1.show();
        
        // wait for window to close, then calculate the ROI diff
        imp1.getWindow().addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                Overlay overlayList = imp1.getOverlay();
                if (overlayList != null) {
                    for (int i=0; i<overlayList.size(); ++i) {
                        Roi roi2 = overlayList.get(i);
                        if (Objects.equals(roi2.getName(), image2.getName())) {
                            int diffXPx = (int)Math.floor(
                                    (roi2.getXBase() + roi2.getFloatWidth() / 2.0) 
                                    - (roiX + roiWidth / 2.0));
                            int diffYPx = (int)Math.floor(
                                    (roi2.getYBase() + roi2.getFloatHeight() / 2.0) 
                                    - (roiY + roiHeight / 2.0));
                            double diffX = diffXPx * pixelSize * invertX;
                            double diffY = diffYPx * pixelSize * invertY;

                            // write the diff to the log and stderr
                            String message = String.format("PosCalibrator\tref_image\t%s\tcompare_image\t%s\tpixel_offset\t%d\t%d\tstage_offset\t%.2f\t%.2f", 
                                    image1.getName(), image2.getName(), diffXPx, diffYPx, diffX, diffY);
                            IJ.log(message);
                            System.err.println(message);
                        }
                    }
                }
            }
        });
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
