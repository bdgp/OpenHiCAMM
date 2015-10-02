package org.bdgp.OpenHiCAMM;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import org.bdgp.OpenHiCAMM.DB.Acquisition;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.Image;
import org.bdgp.OpenHiCAMM.DB.PoolSlide;
import org.bdgp.OpenHiCAMM.DB.ROI;
import org.bdgp.OpenHiCAMM.DB.Slide;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.TaskConfig;
import org.bdgp.OpenHiCAMM.DB.WorkflowModule;
import org.bdgp.OpenHiCAMM.Modules.SlideImager;
import org.bdgp.OpenHiCAMM.Modules.SlideImagerDialog;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMSerializationException;

import com.sun.org.apache.xml.internal.security.utils.Base64;

import ij.ImagePlus;
import ij.gui.NewImage;
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
import jdk.nashorn.api.scripting.JSObject;

import static org.bdgp.OpenHiCAMM.Util.where;
import static org.bdgp.OpenHiCAMM.Tag.*;

@SuppressWarnings("serial")
public class WorkflowReport extends JFrame {
	WorkflowRunner workflowRunner;

	// TODO: convert to configurable values
    public static final int SLIDE_PREVIEW_WIDTH = 1280; 

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
	    // Find SlideImager modules where there is no associated posListModuleId module config
	    // This is the starting SlideImager module.
		return Html().with(()->{
			Body().with(()->{
                List<WorkflowModule> slideImagers = this.workflowRunner.getWorkflow().select(where("moduleName", SlideImager.class.getName()));
                for (WorkflowModule slideImager : slideImagers) {
                    if (!workflowRunner.getTaskConfig().select(where("moduleId", slideImager.getId()).and("key", "posListFile")).isEmpty()) {
                        runReport(slideImager.getId());
                    }
                }
			});
		}).toString();
	}

    Double minX;
    Double minY;
    Double maxX;
    Double maxY;
    Integer imageWidth;
    Integer imageHeight;

	private void runReport(String startModuleId) {
	    WorkflowModule startModule = this.workflowRunner.getWorkflow().selectOneOrDie(where("id", startModuleId));

	    // get the loader module
	    // get the ROIFinder module(s)
	    WorkflowModule loaderModule = null;
	    List<WorkflowModule> roiFinderModules = new ArrayList<WorkflowModule>();
	    if (startModule.getParentId() != null) {
	        WorkflowModule wm = this.workflowRunner.getWorkflow().selectOneOrDie(where("id", startModule.getParentId()));
	        if (this.workflowRunner.getModuleConfig().selectOne(
	                where("id", wm.getId()).and("key", "canLoadSlides").and("value", "yes")) != null) 
	        {
	            loaderModule = wm;
	        }
	        if (this.workflowRunner.getModuleConfig().selectOne(
	                where("id", wm.getId()).and("key", "canProduceROIs").and("value", "yes")) != null) 
	        {
	            roiFinderModules.add(wm);
	        }
	    }

	    // get the associated hi res ROI slide imager modules for each ROI finder module
	    Map<WorkflowModule,List<WorkflowModule>> roiImagers = new HashMap<WorkflowModule,List<WorkflowModule>>();
	    for (WorkflowModule roiFinderModule : roiFinderModules) {
            List<WorkflowModule> slideImagers = this.workflowRunner.getWorkflow().select(where("moduleName", SlideImager.class.getName()));
            for (WorkflowModule slideImager : slideImagers) {
                if (!workflowRunner.getTaskConfig().select(
                        where("moduleId", slideImager.getId()).and("key", "posListModuleId").and("value", roiFinderModule.getId())).isEmpty()) 
                {
                    if (!roiImagers.containsKey(roiFinderModule)) {
                        roiImagers.put(roiFinderModule, new ArrayList<WorkflowModule>());
                    }
                    roiImagers.get(roiFinderModule).add(slideImager);
                }
            }
	    }
	    
	    // display the title
        Dao<PoolSlide> psDao = this.workflowRunner.getInstanceDb().table(PoolSlide.class);
        Dao<Slide> slideDao = this.workflowRunner.getInstanceDb().table(Slide.class);
        Map<String,Config> loaderConfig = new HashMap<String,Config>();
        List<PoolSlide> ps = new ArrayList<PoolSlide>();
        Slide slide;
        String slideId;
	    if (loaderModule != null) {
	    	List<TaskConfig> loaderConfigs = this.workflowRunner.getTaskConfig().select(where("id", loaderModule.getId()));
	    	for (Config conf : loaderConfigs) {
	    		loaderConfig.put(conf.getKey(), conf);
	    	}
	    	if (loaderConfig.containsKey("slideId")) {
	    		slide = slideDao.selectOneOrDie(where("id", loaderConfig.get("slideId")));
	    		ps.addAll(psDao.select(where("slideId", slide.getId())));
	    		slideId = loaderConfig.get("slideId").getValue();
	    	}
	    	else {
	    		slide = null;
	    		slideId = "slide";
	    	}

            H1().with(()->text(
            		String.format("SlideImager %s%s", 
            				startModuleId, 
            				loaderConfig.containsKey("slideId")? 
            						String.format(", Slide %s%s", 
            								slide.getName(),
            								!ps.isEmpty()? 
            										ps.stream().map(p->String.format(", Pool %d, Cartridge %d, Slide Position %d", 
            												p.getPoolId(), p.getCartridgePosition(), p.getSlidePosition())).
            										collect(Collectors.joining("; ")) 
            										: "") 
            						: "")));
	    }
	    else {
            H1().with(()->text(String.format("SlideImager %s", startModuleId)));
            slide = null;
            slideId = "slide";
	    }

        Dao<Image> imageDao = this.workflowRunner.getInstanceDb().table(Image.class);
        Dao<Acquisition> acqDao = this.workflowRunner.getInstanceDb().table(Acquisition.class);

        // get the pixel size of this slide imager config
        Config pixelSizeConf = this.workflowRunner.getModuleConfig().selectOne(where("id", startModuleId).and("key","pixelSize"));
        Double pixelSize = pixelSizeConf != null? new Double(pixelSizeConf.getValue()) : SlideImagerDialog.DEFAULT_PIXEL_SIZE_UM;

        // get invertXAxis and invertYAxis conf values
        Config invertXAxisConf = this.workflowRunner.getModuleConfig().selectOne(where("id", startModuleId).and("key","invertXAxis"));
        boolean invertXAxis = invertXAxisConf == null || invertXAxisConf.equals("yes");
        Config invertYAxisConf = this.workflowRunner.getModuleConfig().selectOne(where("id", startModuleId).and("key","invertYAxis"));
        boolean invertYAxis = invertYAxisConf == null || invertYAxisConf.equals("yes");

        // compute the width and height of the imaging grid in pixels from the position list
        minX = null;
        minY = null;
        maxX = null;
        maxY = null;
        imageWidth = null;
        imageHeight = null;
        List<Task> imageTasks = this.workflowRunner.getTaskStatus().select(where("moduleId", startModuleId));
        for (Task task : imageTasks) {
        	MultiStagePosition msp = getMsp(task);

            if (minX == null || msp.getX() < minX) minX = msp.getX();
            if (maxX == null || msp.getX() > maxX) maxX = msp.getX();
            if (minY == null || msp.getY() < minY) minY = msp.getY();
            if (maxY == null || msp.getY() > maxY) maxY = msp.getY();
            if (imageWidth == null || imageHeight == null) {
                Config imageIdConf = this.workflowRunner.getTaskConfig().selectOne(
                        where("id", new Integer(task.getId()).toString()).and("key", "imageId"));
                if (imageIdConf != null) {
                    Image image = imageDao.selectOneOrDie(where("id", new Integer(imageIdConf.getValue())));
                    ImagePlus imp = image.getImagePlus(acqDao);
                    imageWidth = imp.getWidth();
                    imageHeight = imp.getHeight();
                }
            }
        }
        int slideWidthPx = (int)Math.floor(((maxX - minX) / pixelSize) + imageWidth);
        int slideHeightPx = (int)Math.floor(((maxY - minY) / pixelSize) + imageHeight);
        
        // this is the scale factor for creating the thumbnail images
        double scaleFactor = SLIDE_PREVIEW_WIDTH / slideWidthPx;
        int slidePreviewHeight = (int)Math.floor(scaleFactor * slideHeightPx);
        
        Dao<ROI> roiDao = this.workflowRunner.getInstanceDb().table(ROI.class);
        ImagePlus slideThumb = NewImage.createRGBImage("slideThumb", SLIDE_PREVIEW_WIDTH, slidePreviewHeight, 1, NewImage.FILL_WHITE);
        ImageProcessor slideThumbIp = slideThumb.getProcessor();
        Map(name->String.format("map-%s", slideId)).with(()->{
            for (Task task : imageTasks) {
                Config imageIdConf = this.workflowRunner.getTaskConfig().selectOne(
                        where("id", new Integer(task.getId()).toString()).and("key", "imageId"));
                if (imageIdConf != null) {
                    MultiStagePosition msp = getMsp(task);

                    // Get a thumbnail of the image
                    Image image = imageDao.selectOneOrDie(where("id", new Integer(imageIdConf.getValue())));
                    ImagePlus imp = image.getImagePlus(acqDao);
                    ImageProcessor ip = imp.getProcessor();
                    ip.setInterpolate(true);
                    imp.setProcessor(imp.getTitle(), ip.resize(
                            (int)Math.floor(ip.getWidth() * scaleFactor), 
                            (int)Math.floor(ip.getHeight() * scaleFactor)));
                    
                    int xloc1 = (int)Math.floor(((msp.getX() - minX) / pixelSize) - (imp.getWidth() / 2.0));
                    int xloc = invertXAxis? slideWidthPx - (xloc1 + imp.getWidth()) : xloc1;
                    int yloc1 = (int)Math.floor(((msp.getY() - minY) / pixelSize) - (imp.getHeight() / 2.0));
                    int yloc = invertYAxis? yloc = slideHeightPx - (yloc1 + imp.getHeight()) : yloc1;
                    slideThumbIp.copyBits(ip, xloc, yloc, Blitter.COPY);
                    slideThumbIp.setColor(new Color(0, 0, 0));
                    slideThumbIp.drawRect(xloc, yloc, ip.getWidth(), ip.getHeight());
                    
                    // write the ROI areas first so they take precedence
                    for (ROI roi : roiDao.select(where("imageId", image.getId()))) {
                    	int roiX = (int)Math.floor(xloc + (roi.getX1() * scaleFactor));
                    	int roiY = (int)Math.floor(yloc + (roi.getY1() * scaleFactor));
                    	int roiWidth = (int)Math.floor((roi.getX2()-roi.getX1()+1) * scaleFactor);
                        int roiHeight = (int)Math.floor((roi.getY2()-roi.getY1()+1) * scaleFactor);
                        slideThumbIp.setColor(new Color(255, 0, 0));
                    	slideThumbIp.drawRect(roiX, roiY, roiWidth, roiHeight);
                    	
                    	Area(shape->"rect",
                    			coords->String.format("%d,%d,%d,%d", roiX, roiY, roiX+roiWidth, roiY+roiHeight),
                    			href->String.format("#area-ROI-%s", roi.getId()),
                    			title->roi.toString());
                    }
                    
                    Area(shape->"rect", 
                    		coords->String.format("%d,%d,%d,%d", xloc, yloc, xloc+imp.getWidth(), yloc+imp.getHeight()),
                    		title->image.getName(),
                    		onClick->String.format("workflowReport.showImage(%d)", image.getId()));
                }
            }
        });

        // write the slide thumbnail as an embedded HTML image.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try { ImageIO.write(slideThumb.getBufferedImage(), "jpg", baos); } 
        catch (IOException e) {throw new RuntimeException(e);}
        Img(src->String.format("data:image/jpeg;base64,%s", Base64.encode(baos.toByteArray())),
        		width->slideThumb.getWidth(),
        		height->slideThumb.getHeight(),
        		usemap->String.format("#map-%s", slideId));
        
        Map<Task,Integer> taskPosition = new HashMap<Task,Integer>();
        for (Task task : imageTasks) {
            Config imageLabelConf = this.workflowRunner.getTaskConfig().selectOneOrDie(
                    where("id", task.getId()).and("key", "imageLabel"));
            int[] indices = MDUtils.getIndices(imageLabelConf.getValue());
        	taskPosition.put(task, indices[3]);
        }
        
        
	}
	
	public void showImage(int imageId) {
        // Get a thumbnail of the image
        Dao<Image> imageDao = this.workflowRunner.getInstanceDb().table(Image.class);
        Dao<Acquisition> acqDao = this.workflowRunner.getInstanceDb().table(Acquisition.class);
        Image image = imageDao.selectOneOrDie(where("id", imageId));
        ImagePlus imp = image.getImagePlus(acqDao);
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
