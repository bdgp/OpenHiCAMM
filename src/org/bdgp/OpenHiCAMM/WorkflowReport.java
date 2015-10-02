package org.bdgp.OpenHiCAMM;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JFrame;

import org.bdgp.OpenHiCAMM.DB.Acquisition;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.Image;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.WorkflowModule;
import org.bdgp.OpenHiCAMM.Modules.ROIFinder;
import org.bdgp.OpenHiCAMM.Modules.SlideImager;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.utils.MMException;

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

import static org.bdgp.OpenHiCAMM.Util.where;
import static org.bdgp.OpenHiCAMM.Tag.*;

@SuppressWarnings("serial")
public class WorkflowReport extends JFrame {
	WorkflowRunner workflowRunner;
    public static final int SLIDE_PREVIEW_WIDTH = 1280; 

	public WorkflowReport(WorkflowRunner workflowRunner) {
		this.workflowRunner = workflowRunner;

		final JFXPanel fxPanel = new JFXPanel();
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
        final WebView browser = new WebView();
        final WebEngine webEngine = browser.getEngine();

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

	private void runReport(String startModuleId) {
	    WorkflowModule startModule = this.workflowRunner.getWorkflow().selectOneOrDie(where("id", startModuleId));

	    // get the loader module
	    WorkflowModule loaderModule = null;
	    if (startModule.getParentId() != null) {
	        WorkflowModule loader = this.workflowRunner.getWorkflow().selectOneOrDie(where("id", startModule.getParentId()));
	        if (this.workflowRunner.getModuleConfig().selectOne(
	                where("id", loader.getId()).and("key", "canLoadSlides").and("value", "yes")) != null) 
	        {
	            loaderModule = loader;
	        }
	    }
	    
	    // get the ROIFinder module(s)
	    List<WorkflowModule> roiFinderModules = this.workflowRunner.getWorkflow().select(
	            where("parentId", startModule.getId()).and("moduleName", ROIFinder.class.getName()));

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
	    
	    // get the position list
        Config posListConf = this.workflowRunner.getModuleConfig().selectOneOrDie(where("id",startModuleId).and("key","posListFile"));
        File posListFile = new File(posListConf.getValue());
        if (!posListFile.exists()) {
            throw new RuntimeException("Cannot find position list file "+posListFile.getPath());
        }
        PositionList positionList = null;
        try { 
            positionList = new PositionList();
            positionList.load(posListFile.getPath()); 
        } 
        catch (MMException e) {throw new RuntimeException(e);}
        
        // compute the width and height of the imaging grid in pixels from the position list
        Double minX = null;
        Double minY = null;
        Double maxX = null;
        Double maxY = null;
        Double maxOverlapUmX = null;
        Double maxOverlapUmY = null;
        Double maxOverlapPixelsX = null;
        Double maxOverlapPixelsY = null;
        Integer maxGridCol = null;
        Integer maxGridRow = null;
        for (MultiStagePosition msp : positionList.getPositions()) {
            if (minX == null || msp.getX() < minX) minX = msp.getX();
            if (maxX == null || msp.getX() > maxX) maxX = msp.getX();
            if (minY == null || msp.getY() < minY) minY = msp.getY();
            if (maxY == null || msp.getY() > maxY) maxY = msp.getY();
            if (maxGridCol == null || msp.getGridColumn()+1 > maxGridCol) maxGridCol = msp.getGridColumn()+1;
            if (maxGridRow == null || msp.getGridRow()+1 > maxGridRow) maxGridRow = msp.getGridRow()+1;
            if (msp.getProperty("OverlapUmX") != null && 
                    (maxOverlapUmX == null || new Double(msp.getProperty("OverlapUmX")) > maxOverlapUmX)) 
            {
                maxOverlapUmX = new Double(msp.getProperty("OverlapUmX"));
            }
            if (msp.getProperty("OverlapUmY") != null && 
                    (maxOverlapUmY == null || new Double(msp.getProperty("OverlapUmY")) > maxOverlapUmY)) 
            {
                maxOverlapUmY = new Double(msp.getProperty("OverlapUmY"));
            }
            if (msp.getProperty("OverlapPixelsX") != null && 
                    (maxOverlapPixelsX == null || new Double(msp.getProperty("OverlapPixelsX")) > maxOverlapPixelsX)) 
            {
                maxOverlapPixelsX = new Double(msp.getProperty("OverlapPixelsX"));
            }
            if (msp.getProperty("OverlapPixelsY") != null && 
                    (maxOverlapPixelsY == null || new Double(msp.getProperty("OverlapPixelsY")) > maxOverlapPixelsY)) 
            {
                maxOverlapPixelsY = new Double(msp.getProperty("OverlapPixelsY"));
            }
        }
        double cellWidthUm = ((maxX - minX) + (maxOverlapUmX * (maxGridCol-1))) / (maxGridCol-1);
        double cellHeightUm = ((maxY - minY) + (maxOverlapUmY * (maxGridRow-1))) / (maxGridRow-1);
        double gridWidthUm = (maxX - minX) + cellWidthUm;
        double gridHeightUm =  (maxY - minY) + cellHeightUm;
        double pixelSizeX = maxOverlapUmX / maxOverlapPixelsX;
        double pixelSizeY = maxOverlapUmY / maxOverlapPixelsY;
        int gridWidthPx = (int)Math.floor(gridWidthUm / pixelSizeX);
        int gridHeightPx = (int)Math.floor(gridHeightUm / pixelSizeY);
        
        // this is the scale factor for creating the thumbnail images
        double scaleFactor = SLIDE_PREVIEW_WIDTH / gridWidthPx;
        int slidePreviewHeight = (int)Math.floor(scaleFactor * gridHeightPx);
        
        Dao<Image> imageDao = this.workflowRunner.getInstanceDb().table(Image.class);
        Dao<Acquisition> acqDao = this.workflowRunner.getInstanceDb().table(Acquisition.class);
        ImagePlus slideThumb = NewImage.createRGBImage("slideThumb", SLIDE_PREVIEW_WIDTH, slidePreviewHeight, 1, NewImage.FILL_WHITE);
        ImageProcessor slideThumbIp = slideThumb.getProcessor();
        List<Task> imageTasks = this.workflowRunner.getTaskStatus().select(where("moduleId", startModuleId));
        for (Task task : imageTasks) {
            Config imageIdConf = this.workflowRunner.getTaskConfig().selectOne(
                    where("id", new Integer(task.getId()).toString()).and("key", "imageId"));
            if (imageIdConf != null) {
                // Get a thumbnail of the image
                Image image = imageDao.selectOneOrDie(where("id", new Integer(imageIdConf.getValue())));
                ImagePlus imp = image.getImagePlus(acqDao);
                ImageProcessor ip = imp.getProcessor();
                ip.setInterpolate(true);
                imp.setProcessor(imp.getTitle(), ip.resize(
                        (int)Math.floor(ip.getWidth() * scaleFactor), 
                        (int)Math.floor(ip.getHeight() * scaleFactor)));
                // TODO: compute xloc, yloc
                //slideThumbIp.copyBits(ip, xloc, yloc, Blitter.COPY);
            }
        }
	}
}
