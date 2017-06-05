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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import com.google.common.io.Files;
import com.google.common.io.Resources;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageRoi;
import ij.gui.NewImage;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.process.Blitter;
import ij.process.ImageProcessor;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import mmcorej.CMMCore;

import static org.bdgp.OpenHiCAMM.Util.where;
import static org.bdgp.OpenHiCAMM.Tag.T.*;

public class WorkflowReport implements Report {
    public static final int SLIDE_PREVIEW_WIDTH = 1280; 
    public static final int ROI_GRID_PREVIEW_WIDTH = 425; 

    private WorkflowRunner workflowRunner;
    private WebEngine webEngine;
    private String reportDir;
    private String reportIndex;
    private Integer prevPoolSlideId;
    private Map<Integer,Boolean> isLoaderInitialized;
    private Map<Integer,MultiStagePosition> msps;
    private Map<Integer,Map<String,ModuleConfig>> moduleConfig;
    private Map<Integer,Map<String,TaskConfig>> taskConfig;
    
    private Alert alert = null;

    public void jsLog(String message) {
        IJ.log(String.format("[WorkflowReport:js] %s", message));
    }

    public void log(String message, Object... args) {
        IJ.log(String.format("[WorkflowReport] %s", String.format(message, args)));
    }
    
    @Override public void initialize(WorkflowRunner workflowRunner, WebEngine webEngine, String reportDir, String reportIndex) {
        this.workflowRunner = workflowRunner;
        this.webEngine = webEngine;
        this.reportDir = reportDir;
        this.reportIndex = reportIndex;
        isLoaderInitialized = new HashMap<Integer,Boolean>();
        this.msps = new HashMap<>();
        this.moduleConfig = new HashMap<>();
        this.taskConfig = new HashMap<>();
    }
    
    private ModuleConfig getModuleConfig(int id, String key) {
        Map<String,ModuleConfig> confs = moduleConfig.get(id);
        if (confs == null) return null;
        return confs.get(key);
    }
    private TaskConfig getTaskConfig(int id, String key) {
        Map<String,TaskConfig> confs = taskConfig.get(id);
        if (confs == null) return null;
        return confs.get(key);
    }
    
    @Override
    public void runReport() {
        Dao<Pool> poolDao = this.workflowRunner.getWorkflowDb().table(Pool.class);
        Dao<PoolSlide> psDao = this.workflowRunner.getWorkflowDb().table(PoolSlide.class);
        Dao<Slide> slideDao = this.workflowRunner.getWorkflowDb().table(Slide.class);
        
        // cache the module config 
        log("Caching the module config...");
        for (ModuleConfig moduleConf : this.workflowRunner.getModuleConfig().select()) {
            moduleConfig.putIfAbsent(moduleConf.getId(), new HashMap<>());
            moduleConfig.get(moduleConf.getId()).put(moduleConf.getKey(), moduleConf);
        }
        // cache the task config
        log("Caching the task config...");
        for (TaskConfig taskConf : this.workflowRunner.getTaskConfig().select()) {
            taskConfig.putIfAbsent(taskConf.getId(), new HashMap<>());
            taskConfig.get(taskConf.getId()).put(taskConf.getKey(), taskConf);
        }
        // get the MSP from the task config
        PositionList posList = new PositionList();
        List<TaskConfig> mspConfs = this.workflowRunner.getTaskConfig().select(
                where("key","MSP"));
        log("Creating task->MSP table for %s tasks...", mspConfs.size());
        for (TaskConfig mspConf : mspConfs) {
            try {
                JSONObject posListJson = new JSONObject().
                        put("POSITIONS", new JSONArray().put(new JSONObject(mspConf.getValue()))).
                        put("VERSION", 3).
                        put("ID","Micro-Manager XY-position list");
                posList.restore(posListJson.toString());
                MultiStagePosition msp = posList.getPosition(0);
                msps.put(mspConf.getId(), msp);
            } 
            catch (JSONException | MMSerializationException e) {throw new RuntimeException(e);}
        }

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
                    Script().raw(Resources.toString(Resources.getResource("notify.min.js"), Charsets.UTF_8));
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
                    WorkflowModule slideImager = this.workflowRunner.getWorkflow().selectOne(
                            where("id", canImageSlides.getId()));
                    if (slideImager != null && 
                        getModuleConfig(slideImager.getId(), "posListModule") == null)
                    {
                        log("Working on slideImager: %s", slideImager);
                       // get the loader module
                        Integer loaderModuleId = slideImager.getParentId();
                        while (loaderModuleId != null) {
                            WorkflowModule loaderModule = this.workflowRunner.getWorkflow().selectOneOrDie(
                                    where("id", loaderModuleId));
                            Config canLoadSlides = getModuleConfig(loaderModule.getId(), "canLoadSlides");
                            if (canLoadSlides != null && "yes".equals(canLoadSlides.getValue())) {
                                log("Using loaderModule: %s", loaderModule);
                                Config poolIdConf = getModuleConfig(loaderModule.getId(), "poolId");
                                if (poolIdConf != null) {
                                    Pool pool = poolDao.selectOneOrDie(where("id", poolIdConf.getValue()));
                                    List<PoolSlide> pss = psDao.select(where("poolId", pool.getId()));
                                    if (!pss.isEmpty()) {
                                        for (PoolSlide ps : pss) {
                                            Slide slide = slideDao.selectOneOrDie(where("id", ps.getSlideId()));
                                            String reportFile = String.format("report%03d.%s.cartridge%d.pos%02d.slide%05d.html", 
                                                        reportCounter[0]++, 
                                                        slideImager.getName(),
                                                        ps.getCartridgePosition(), ps
                                                        .getSlidePosition(), 
                                                        ps.getSlideId());
                                            P().with(()->{
                                                A(String.format("Module %s, Experiment %s, Slide %s", slideImager.getName(), slide.getExperimentId(), ps)).
                                                    attr("href", reportFile);
                                            });
                                            Integer loaderModuleId_ = loaderModuleId;
                                            runnables.put(reportFile, ()->{
                                                log("Calling runReport(startModule=%s, poolSlide=%s, loaderModuleId=%s)", 
                                                        slideImager, ps, loaderModuleId_);
                                                WorkflowModule lm = this.workflowRunner.getWorkflow().selectOneOrDie(where("id", loaderModuleId_));
                                                runReport(slideImager, ps, lm, reportFile);
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
                            runReport(slideImager, null, null, reportFile);
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

            if (!new File(reportDir, reportFile).exists()) {
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
                                Script().raw(Resources.toString(Resources.getResource("notify.min.js"), Charsets.UTF_8));
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

    private void runReport(WorkflowModule startModule, PoolSlide poolSlide, WorkflowModule loaderModule, String reportFile) {
        log("Called runReport(startModule=%s, poolSlide=%s)", startModule, poolSlide);

        Dao<Slide> slideDao = this.workflowRunner.getWorkflowDb().table(Slide.class);
        Dao<Image> imageDao = this.workflowRunner.getWorkflowDb().table(Image.class);
        Dao<Acquisition> acqDao = this.workflowRunner.getWorkflowDb().table(Acquisition.class);
        Dao<ROI> roiDao = this.workflowRunner.getWorkflowDb().table(ROI.class);
        Dao<SlidePosList> slidePosListDao = this.workflowRunner.getWorkflowDb().table(SlidePosList.class);
        
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
            TaskConfig imageIdConf = getTaskConfig(task.getId(), "imageId");
            if (imageIdConf != null) {
                for (ROI roi : roiDao.select(where("imageId", imageIdConf.getValue()))) {
                    Set<Integer> roiModuleSet = roiModules.get(roi.getId());
                    if (roiModuleSet == null) {
                        //log(String.format("No ROI modules found for ROI %s!", roi));
                    }
                    else {
                        for (Integer roiModuleId : roiModuleSet) {
                            WorkflowModule roiModule = this.workflowRunner.getWorkflow().selectOne(where("id", roiModuleId));
                            if (roiModule != null) {
                                for (ModuleConfig posListModuleConf : this.workflowRunner.getModuleConfig().select(
                                        where("key", "posListModule").
                                        and("value", roiModule.getName()))) 
                                {
                                    Config canImageSlides = getModuleConfig(posListModuleConf.getId(), "canImageSlides");
                                    if (canImageSlides != null && "yes".equals(canImageSlides.getValue())) {
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
            String title = String.format("SlideImager %s, Experiment %s, Slide %s, Pool %d, Cartridge %d, Slide Position %d", 
                    startModule.getName(), 
                    slide.getExperimentId(),
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
        Config pixelSizeConf = getModuleConfig(startModule.getId(), "pixelSize");
        Double pixelSize = pixelSizeConf != null? new Double(pixelSizeConf.getValue()) : SlideImagerDialog.DEFAULT_PIXEL_SIZE_UM;
        log("pixelSize = %f", pixelSize);

        // get invertXAxis and invertYAxis conf values
        Config invertXAxisConf = getModuleConfig(startModule.getId(), "invertXAxis");
        boolean invertXAxis = invertXAxisConf == null || invertXAxisConf.getValue().equals("yes");
        log("invertXAxis = %b", invertXAxis);

        Config invertYAxisConf = getModuleConfig(startModule.getId(), "invertYAxis");
        boolean invertYAxis = invertYAxisConf == null || invertYAxisConf.getValue().equals("yes");
        log("invertYAxis = %b", invertYAxis);

        // sort imageTasks by image position
        List<Task> imageTasks = this.workflowRunner.getTaskStatus().select(
                where("moduleId", startModule.getId()));
        Map<String,Task> imageTaskPosIdx = new TreeMap<String,Task>(new ImageLabelComparator());
        for (Task imageTask : imageTasks) {
            Config slideIdConf = getTaskConfig(imageTask.getId(), "slideId");
            if (poolSlide == null || new Integer(slideIdConf.getValue()).equals(poolSlide.getSlideId())) {
                Config imageLabelConf = getTaskConfig(imageTask.getId(), "imageLabel");
                if (imageLabelConf != null) {
                    imageTaskPosIdx.put(imageLabelConf.getValue(), imageTask);
                }
            }
        }
        imageTasks.clear();
        imageTasks.addAll(imageTaskPosIdx.values());
        log("imageTasks = %s", imageTasks);
        
        final String acquisitionTime;
        if (!imageTasks.isEmpty()) {
            Config acquisitionTimeConf = getTaskConfig(imageTasks.get(0).getId(), "acquisitionTime");
            if (acquisitionTimeConf != null) {
                try {
                    acquisitionTime = new SimpleDateFormat("yyyyMMddHHmmss").format(
                            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse(
                                    acquisitionTimeConf.getValue()));
                } 
                catch (ParseException e) { throw new RuntimeException(e); }
            }
            else {
                acquisitionTime = null;
            }
        }
        else {
            acquisitionTime = null;
        }

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
                Config imageIdConf = getTaskConfig(task.getId(), "imageId");
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
                    Config imageIdConf = getTaskConfig(task.getId(), "imageId");
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
                           attr("onClick", String.format("report.showImage(%d); return false", new Integer(roi.getProperty("id"))));
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
                Config imageIdConf = getTaskConfig(task.getId(), "imageId");
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
                                Config hiResPixelSizeConf = getModuleConfig(imager.getId(), "pixelSize");
                                Double hiResPixelSize = hiResPixelSizeConf != null? new Double(hiResPixelSizeConf.getValue()) : ROIFinderDialog.DEFAULT_HIRES_PIXEL_SIZE_UM;
                                log("hiResPixelSize = %f", hiResPixelSize);

                                // determine the stage coordinate bounds of this ROI tile grid.
                                // also get the image width and height of the acqusition.
                                Double minX2_=null, minY2_=null, maxX2_=null, maxY2_=null;
                                Integer imageWidth2_ = null, imageHeight2_ = null;
                                Map<String,List<Task>> imagerTasks = new TreeMap<String,List<Task>>(new ImageLabelComparator());
                                for (Task imagerTask : this.workflowRunner.getTaskStatus().select(
                                        where("moduleId", imager.getId()))) 
                                {
                                    Config imageIdConf2 = getTaskConfig(imagerTask.getId(), "imageId");
                                    if (imageIdConf2 != null) {
                                        MultiStagePosition imagerMsp = getMsp(imagerTask);
                                        if (imagerMsp.hasProperty("ROI") && imagerMsp.getProperty("ROI").equals(new Integer(roi.getId()).toString())) 
                                        {
                                            TaskConfig imageLabelConf = getTaskConfig(imagerTask.getId(), "imageLabel");
                                            if (imageLabelConf == null || 
                                                imageLabelConf.getValue() == null || 
                                                imageLabelConf.getValue().isEmpty()) 
                                            {
                                                continue;
                                            }
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
                                                Th().text("Curation");
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
                                                            Config imageIdConf2 = getTaskConfig(imagerTask.getId(), "imageId");
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
                                                                A().attr("onClick", 
                                                                        String.format("report.goToPosition(%d,%d,%f,%f); return false", 
                                                                                loaderModule.getId(),
                                                                                poolSlide != null? poolSlide.getId() : -1, 
                                                                                        xPos_, 
                                                                                        yPos_)).
                                                                    text(String.format("Go To Stage Position: (%.2f,%.2f)", xPos_, yPos_));
                                                            });

                                                            // add another link to put the slide back
                                                            if (poolSlide != null) {
                                                                P().with(()->{
                                                                    A().attr("onClick", String.format("report.returnSlide(%d); return false",
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
                                                            //String dirs = new File(reportDir, reportFile).getPath().replaceAll("\\.html$", "");
                                                            //new File(dirs).mkdirs();
                                                            //new FileSaver(imp).saveAsJpeg(new File(dirs, String.format("ROI%s.roi_thumbnail.jpg", roi.getId())).getPath());
                                                            try { ImageIO.write(imp.getBufferedImage(), "jpg", baos2); } 
                                                            catch (IOException e) {throw new RuntimeException(e);}
                                                            ImagePlus imp_ = imp;
                                                            A().attr("onClick", String.format("report.showImage(%d); return false", image.getId())).
                                                                with(()->{
                                                                    Img().attr("src", String.format("data:image/jpg;base64,%s", 
                                                                                Base64.getMimeEncoder().encodeToString(baos2.toByteArray()))).
                                                                            attr("width", ROI_GRID_PREVIEW_WIDTH).
                                                                            attr("data-min-x", msp.getX() + (roi.getX1() - (imp_.getWidth() / 2.0)) * pixelSize * (invertXAxis? -1.0 : 1.0)).
                                                                            attr("data-max-x", msp.getX() + (roi.getX2() - (imp_.getWidth() / 2.0)) * pixelSize * (invertXAxis? -1.0 : 1.0)).
                                                                            attr("data-min-y", msp.getY() + (roi.getY1() - (imp_.getHeight() / 2.0)) * pixelSize * (invertYAxis? -1.0 : 1.0)).
                                                                            attr("data-max-y", msp.getY() + (roi.getY2() - (imp_.getHeight() / 2.0)) * pixelSize * (invertYAxis? -1.0 : 1.0)).
                                                                            //attr("title", roi.toString()).
                                                                            attr("class", "stageCoords");
                                                                    });
                                                        }
                                                    });
                                                    Td().with(()->{
                                                        List<Runnable> makeLinks = new ArrayList<>();
                                                        Map().attr("name", String.format("map-roi-%s-ROI%d", imager.getName(), roi.getId())).with(()->{
                                                            for (Task imagerTask : imagerTaskEntry.getValue()) {
                                                                Config imageIdConf2 = getTaskConfig(imagerTask.getId(), "imageId");
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
                                                                                attr("onClick", String.format("report.showImage(%d); return false", image2.getId()));
                                                                        
                                                                        makeLinks.add(()->{
                                                                            P().with(()->{
                                                                                A().attr("onClick",String.format("report.checkPosCalibration(%d,%d,%f,%f,%f,%f,%f,%f,%f,%f); return false",
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
                                                                                    text(String.format("Check pos calibration for image %s", image2.getName()));
                                                                            });
                                                                        });
                                                                    }
                                                                }
                                                            }
                                                        });

                                                        //String dirs = new File(reportDir, reportFile).getPath().replaceAll("\\.html$", "");
                                                        //new File(dirs).mkdirs();
                                                        //new FileSaver(roiGridThumb).saveAsJpeg(new File(dirs, String.format("ROI%s.grid_thumbnail.jpg", roi.getId())).getPath());
                                                        // write the grid thumbnail as an embedded HTML image.
                                                        ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
                                                        try { ImageIO.write(roiGridThumb.getBufferedImage(), "jpg", baos2); } 
                                                        catch (IOException e) {throw new RuntimeException(e);}
                                                        Img().attr("src", String.format("data:image/jpg;base64,%s", 
                                                                    Base64.getMimeEncoder().encodeToString(baos2.toByteArray()))).
                                                                attr("width", ROI_GRID_PREVIEW_WIDTH).
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
                                                    List<String> stitchedImageFiles = new ArrayList<>();
                                                    Td().with(()->{
                                                        // get the downstream stitcher tasks
                                                        Set<Task> stitcherTasks = new HashSet<Task>();
                                                        for (Task imagerTask : imagerTaskEntry.getValue()) {
                                                            for (TaskDispatch td : this.workflowRunner.getTaskDispatch().select(where("parentTaskId", imagerTask.getId()))) {
                                                                Task stitcherTask = this.workflowRunner.getTaskStatus().selectOneOrDie(
                                                                        where("id", td.getTaskId()));
                                                                Config canStitchImages = getModuleConfig(stitcherTask.getModuleId(), "canStitchImages");
                                                                if (canStitchImages != null && 
                                                                        "yes".equals(canStitchImages.getValue()) &&
                                                                        stitcherTask.getStatus().equals(Status.SUCCESS)) 
                                                                {
                                                                    stitcherTasks.add(stitcherTask);
                                                                }
                                                            }
                                                        }
                                                        for (Task stitcherTask : stitcherTasks) {
                                                            log("Working on stitcher task: %s", stitcherTask);
                                                            Config stitchedImageConf = getTaskConfig(stitcherTask.getId(), "stitchedImageFile");
                                                            if (stitchedImageConf != null && stitchedImageConf.getValue() != null && !stitchedImageConf.getValue().isEmpty()) {
                                                                String stitchedImage = stitchedImageConf.getValue();
                                                                if (!Paths.get(stitchedImage).isAbsolute()) {
                                                                    stitchedImage = Paths.get(workflowRunner.getWorkflowDir().getPath()).resolve(Paths.get(stitchedImage)).toString();
                                                                }
                                                                if (!new File(stitchedImage).exists()) continue;

                                                                // see if there is an edited image. If so, display that instead.
                                                                String editedImagePath = getEditedImagePath(stitchedImage);
                                                                String stitchedImagePath = editedImagePath != null && new File(editedImagePath).exists()?
                                                                        editedImagePath : 
                                                                        stitchedImage;
                                                                log("stitchedImagePath = %s", stitchedImagePath);
                                                                String stitchedImageRelPath;
                                                                stitchedImageRelPath = Paths.get(workflowRunner.getWorkflowDir().getPath()).relativize(Paths.get(stitchedImagePath)).toString();
                                                                stitchedImageFiles.add(stitchedImageRelPath);
                                                                // Get a thumbnail of the image
                                                                ImagePlus imp = new ImagePlus(stitchedImagePath);
                                                                log("stitchedImage width = %d, height = %d", imp.getWidth(), imp.getHeight());

                                                                // crop the image
                                                                cropImage(imp);
                                                                // resize the image to thumbnail size
                                                                double stitchScaleFactor = (double)ROI_GRID_PREVIEW_WIDTH / (double)imp.getWidth();
                                                                log("stitchScaleFactor = %f", stitchScaleFactor);
                                                                int stitchPreviewHeight = (int)Math.floor(imp.getHeight() * stitchScaleFactor);
                                                                log("stitchPreviewHeight = %d", stitchPreviewHeight);
                                                                imp.getProcessor().setInterpolationMethod(ImageProcessor.BILINEAR);
                                                                imp.setProcessor(imp.getTitle(), imp.getProcessor().resize(
                                                                        ROI_GRID_PREVIEW_WIDTH, 
                                                                        stitchPreviewHeight));
                                                                log("resized stitched image width=%d, height=%d", imp.getWidth(), imp.getHeight());

                                                                //String dirs = new File(reportDir, reportFile).getPath().replaceAll("\\.html$", "");
                                                                //new File(dirs).mkdirs();
                                                                //new FileSaver(imp).saveAsJpeg(new File(dirs, String.format("ROI%s.stitched_thumbnail.jpg", roi.getId())).getPath());
                                                                // write the stitched thumbnail as an embedded HTML image.
                                                                ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
                                                                try { ImageIO.write(imp.getBufferedImage(), "jpg", baos2); } 
                                                                catch (IOException e) {throw new RuntimeException(e);}
                                                                A().attr("onClick", String.format("report.showImageFile(\"%s\"); return false",  
                                                                            Util.escapeJavaStyleString(stitchedImageRelPath))).
                                                                    with(()->{
                                                                        Img().attr("src", String.format("data:image/jpg;base64,%s", 
                                                                                    Base64.getMimeEncoder().encodeToString(baos2.toByteArray()))).
                                                                                attr("width", ROI_GRID_PREVIEW_WIDTH).
                                                                                attr("class", "stitched").
                                                                                attr("data-path", stitchedImageRelPath).
                                                                                attr("id", new File(stitchedImageRelPath).getName()).
                                                                                attr("title", stitchedImageRelPath);
                                                                    });
                                                            }
                                                        }
                                                    });
                                                    // Curation buttons
                                                    String[] orientations = new String[]{"lateral","ventral","dorsal"};
                                                    String[] stages = new String[]{"1-3","4-6","7-8","9-10","11-12","13-16"};
                                                    String experimentId = String.format("%s.%s", 
                                                        poolSlide != null && slide != null? String.format("C%sS%s.SLIDE%s.%s", 
                                                                poolSlide.getCartridgePosition(),
                                                                poolSlide.getSlidePosition(),
                                                                slide.getId(),
                                                                slide.getExperimentId()) :
                                                        slide != null? String.format("SLIDE%s.%s", 
                                                                slide.getId(),
                                                                slide.getExperimentId()) :
                                                        acquisitionTime != null? acquisitionTime : 
                                                        "slide", 
                                                        roi.getId()).replaceAll("[\\/ :_]+","."); 
                                                    Td().with(()->{
                                                        Table().with(()->{
                                                            Tbody().with(()->{
                                                                for (String orientation : orientations) {
                                                                    Tr().with(()->{
                                                                        for (String stage : stages) {
                                                                            Td().with(()->{
                                                                                StringBuilder sb = new StringBuilder();
                                                                                for (String stitchedImageFile : stitchedImageFiles) {
                                                                                    sb.append(String.format("report.curate(\"%s\",\"%s\",\"%s\",\"%s\");", 
                                                                                            Util.escapeJavaStyleString(stitchedImageFile),
                                                                                            Util.escapeJavaStyleString(experimentId), 
                                                                                            Util.escapeJavaStyleString(orientation), 
                                                                                            Util.escapeJavaStyleString(stage)));
                                                                                }
                                                                                Button(String.format("%s %s", orientation, stage)).
                                                                                    attr("type","button").
                                                                                    attr("onclick", sb.toString());
                                                                            });
                                                                        }
                                                                    });
                                                                }
                                                            });
                                                        });
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
        Dao<Image> imageDao = this.workflowRunner.getWorkflowDb().table(Image.class);
        Dao<Acquisition> acqDao = this.workflowRunner.getWorkflowDb().table(Acquisition.class);
        Image image = imageDao.selectOneOrDie(where("id", imageId));
        ImagePlus imp = image.getImagePlus(acqDao);
        imp.show();
    }
    
    public void goToPosition(Integer loaderModuleId, int poolSlideId, double xPos, double yPos) {
        synchronized(this) {
            Dao<PoolSlide> poolSlideDao = this.workflowRunner.getWorkflowDb().table(PoolSlide.class);
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
                            
                            Dao<PoolSlide> poolSlideDao = this.workflowRunner.getWorkflowDb().table(PoolSlide.class);
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
    
    public void showInstructions() {
        if (alert == null) {
            String instructions = Div().with(()->{
                H1("Manual Curation Instructions:");
                Ul().with(()->{
                    Li("Make sure image or Fiji toolbar is in focus");
                    Li("To flip the image horizontally, select Image -> Transform - Flip Horizontally from the menu");
                    Li("To flip the image vertically, select Image -> Transform - Flip Vertically from the menu");
                    Li("Rotation").with(()->{
                        Ul().with(()->{
                            Li("To rotate the image, select Image -> Transform -> Rotate");
                            Li("Check the \"Preview\" box to see the rotated image");
                            Li("Select the angle of rotation");
                        });
                    });
                    Li("Cropping").with(()->{
                        Ul().with(()->{
                            Li("To crop the image, first select Edit -> Selection -> Specify from the menu");
                            Li(String.format("Set the width to %s and the height to %s", CURATE_IMAGE_WIDTH, CURATE_IMAGE_HEIGHT));
                            Li("Using the mouse, drag the upper-left corner of the selection box to the upper-left corner of the ROI shape");
                            Li("Hold down the ALT key, then using the mouse, drag the lower-right corner of the selection box to the lower-right corner of the ROI shape");
                            Li("Using the mouse, drag the selection box to reposition the selection box until it completely covers the ROI shape");
                            Li("Once the selection box is positioned correctly, select Image -> Crop from the menu");
                        });
                    });
                    Li("Resizing").with(()->{
                        Ul().with(()->{
                            Li("To resize the image, select Image -> Adjust -> Size from the menu");
                            Li(String.format("Set the width to %s and the height to %s", CURATE_IMAGE_WIDTH, CURATE_IMAGE_HEIGHT));
                            Li("Uncheck constrain aspect ratio if necessary");
                        });
                    });
                    Li("To save the edited image, select File -> Save from the menu");
                    Li("To close the edited image window, select File -> Close from the menu");
                    Li("The updated image should now appear in the report page. Press a curation button to transfer the file into the database");
                });
            }).toString();

            alert = new Alert(AlertType.INFORMATION);
            alert.initModality(Modality.NONE);
            alert.setHeaderText("Manual Curation Instructions");
            WebView webView = new WebView();
            webView.getEngine().loadContent(instructions);
            webView.setPrefSize(1280, 1024);
            alert.getDialogPane().setContent(webView);
        }
        alert.show();
    }
    
    private Map<String,Boolean> changedImages = new HashMap<>();
    private String getEditedImagePath(String imagePath) {
        return imagePath.replaceFirst("([.][^.]+)?$", ".edited$1");
    }
    public synchronized String changedImages() {
        List<String> changedImageList = new ArrayList<>();
        for (Map.Entry<String,Boolean> entry : changedImages.entrySet()) {
            String imagePath = entry.getKey();
            String editedImagePath = getEditedImagePath(imagePath);
            File editedImageFile = new File(editedImagePath);
            Boolean changed = entry.getValue();
            ImagePlus imp = WindowManager.getImage(editedImageFile.getName());
            if (imp == null) {
                changedImages.remove(imagePath);
                changedImageList.add(imagePath);
            }
            else if (imp.changes != changed) {
                changedImageList.add(imagePath);
            }
        }
        String changedImages = String.join("\n", changedImageList);
        //IJ.log(String.format("changedImages=%s", changedImages));
        return changedImages;
    }
    public boolean isEdited(String imagePath) {
        if (!Paths.get(imagePath).isAbsolute()) {
            imagePath = Paths.get(workflowRunner.getWorkflowDir().getPath()).resolve(Paths.get(imagePath)).toString();
        }
        String editedImagePath = getEditedImagePath(imagePath);
        File editedImageFile = new File(editedImagePath);
        return editedImageFile.exists();
    }
    public String getImageBase64(String imagePath) {
        if (!Paths.get(imagePath).isAbsolute()) {
            imagePath = Paths.get(workflowRunner.getWorkflowDir().getPath()).resolve(Paths.get(imagePath)).toString();
        }
        String editedImagePath = getEditedImagePath(imagePath);
        File editedImageFile = new File(editedImagePath);
        ImagePlus imp = editedImageFile.exists()? new ImagePlus(editedImagePath) : new ImagePlus(imagePath);
        cropImage(imp);

        double stitchScaleFactor = (double)ROI_GRID_PREVIEW_WIDTH / (double)imp.getWidth();
        int stitchPreviewHeight = (int)Math.floor(imp.getHeight() * stitchScaleFactor);
        imp.getProcessor().setInterpolationMethod(ImageProcessor.BILINEAR);
        imp.setProcessor(imp.getTitle(), imp.getProcessor().resize(
                ROI_GRID_PREVIEW_WIDTH, 
                stitchPreviewHeight));
        ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
        try { ImageIO.write(imp.getBufferedImage(), "jpg", baos2); } 
        catch (IOException e) {throw new RuntimeException(e);}
        String base64 = String.format("data:image/jpg;base64,%s", 
                Base64.getMimeEncoder().encodeToString(baos2.toByteArray()));
        //IJ.log(String.format("image=%s, base64=%s", imagePath, base64));
        return base64;
    }

    public synchronized void showImageFile(String imagePath) {
        if (!Paths.get(imagePath).isAbsolute()) {
            imagePath = Paths.get(workflowRunner.getWorkflowDir().getPath()).resolve(Paths.get(imagePath)).toString();
        }
        //log("called showImageFile(imagePath=%s)", imagePath);
        String editedImagePath = getEditedImagePath(imagePath);
        File editedImageFile = new File(editedImagePath);
        ImagePlus imp = WindowManager.getImage(editedImageFile.getName());
        if (imp == null) {
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                throw new RuntimeException(String.format("Could not find image file %s!", imagePath));
            }
            if (editedImageFile.exists()) {
                Alert alert = new Alert(AlertType.CONFIRMATION);
                alert.setTitle("Confirmation Dialog");
                alert.setHeaderText("Keep your edits?");
                alert.setContentText(String.format("Edited file %s already exists, keep your edits?", editedImagePath));
                alert.getButtonTypes().clear();
                alert.getButtonTypes().addAll(ButtonType.YES, ButtonType.NO);
                Optional<ButtonType> result = alert.showAndWait();
                if (result.get() == ButtonType.NO) {
                    try { Files.copy(imageFile, editedImageFile); } 
                    catch (IOException e) {throw new RuntimeException(e);}
                }
            }
            else {
                try { Files.copy(imageFile, editedImageFile); } 
                catch (IOException e) {throw new RuntimeException(e);}
            }
            imp = new ImagePlus(editedImagePath);
        }
        showInstructions();
        imp.show();
        changedImages.put(imagePath, imp.changes);
    }
    public static final int CURATE_IMAGE_WIDTH = 1520;
    public static final int CURATE_IMAGE_HEIGHT = 1080;
    
    public void cropImage(ImagePlus imp) {
        // resize to smaller source dimension maintaining aspect ratio
        int image_width = imp.getWidth() < imp.getHeight()? 
                CURATE_IMAGE_WIDTH : 
                (int)Math.ceil((double)imp.getWidth()*((double)CURATE_IMAGE_HEIGHT/(double)imp.getHeight()));
        int image_height = imp.getWidth() < imp.getHeight()?
                (int)Math.ceil((double)imp.getHeight()*((double)CURATE_IMAGE_WIDTH/(double)imp.getWidth())) :
                CURATE_IMAGE_HEIGHT; 
        imp.getProcessor().setInterpolationMethod(ImageProcessor.BILINEAR);
        imp.setProcessor(imp.getTitle(), imp.getProcessor().resize(image_width, image_height));
        // then do a centered crop
        ImageProcessor processor = imp.getProcessor();
        processor.setRoi(
                Math.max(0, (int)Math.floor(((double)imp.getWidth()/2.0) - ((double)CURATE_IMAGE_WIDTH/2.0))), 
                Math.max(0, (int)Math.floor(((double)imp.getHeight()/2.0) - ((double)CURATE_IMAGE_HEIGHT/2.0))), 
                CURATE_IMAGE_WIDTH, CURATE_IMAGE_HEIGHT);
        processor = processor.crop();
        imp.setProcessor(imp.getTitle(), processor);
    }
    
    /**
     * If the user presses a curation button, copy and rename the stitched image into the curation folder.
     * @param stitchedImagePath The path to the stitched image
     * @param orientation The embryo's orientation
     * @param stage Curated timepoint in hours
     */
    public void curate(String stitchedImagePath, String experimentId, String orientation, String stage) {
        if (!Paths.get(stitchedImagePath).isAbsolute()) {
            stitchedImagePath = Paths.get(workflowRunner.getWorkflowDir().getPath()).resolve(Paths.get(stitchedImagePath)).toString();
        }
        // /data/insitu_images/images/stage${stage}/lm(l|d|v)_${experiment_id}_${stage}.jpg
        String imagesFolder = "/data/insitu_images/images";
        String stageFolderName = String.format("stage%s",stage);
        String fileName = String.format("lm%s_%s_%s.jpg", orientation.charAt(0), experimentId, stage);
        String outputFile = Paths.get(imagesFolder, stageFolderName, fileName).toString();

        String editedImagePath = getEditedImagePath(stitchedImagePath);
        File editedImageFile = new File(editedImagePath);
        ImagePlus imp = editedImageFile.exists()? new ImagePlus(editedImagePath) : new ImagePlus(stitchedImagePath);
        cropImage(imp);

        if (new FileSaver(imp).saveAsJpeg(outputFile)) {
            this.webEngine.executeScript(String.format(
                    "(function(f,d){$.notify('Image \"'+f+'\" was created in \"'+d+'\".','success')})(\"%s\",\"%s\")", 
                    Util.escapeJavaStyleString(fileName),
                    Util.escapeJavaStyleString(Paths.get(imagesFolder, stageFolderName).toString())));
        }
        else {
            this.webEngine.executeScript(String.format(
                    "(function(f){$.notify('Failed to write image \"'+f+'\"!','error')})(\"%s\")", 
                    Util.escapeJavaStyleString(outputFile)));
        }
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
        Dao<Acquisition> acqDao = this.workflowRunner.getWorkflowDb().table(Acquisition.class);
        Dao<Image> imageDao = this.workflowRunner.getWorkflowDb().table(Image.class);
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
        MultiStagePosition msp = this.msps.get(task.getId());
        if (msp == null) throw new RuntimeException(String.format("Could not find MSP conf for task %s!", task));
        return msp;
    }
    
}
