package org.bdgp.OpenHiCAMM.Modules;

import javax.swing.JPanel;

import org.bdgp.OpenHiCAMM.DoubleSpinner;
import org.bdgp.OpenHiCAMM.ImageLog.ImageLogRunner;
import org.bdgp.OpenHiCAMM.Logger;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.Image;
import org.json.JSONException;
import org.micromanager.utils.MDUtils;

import mmcorej.TaggedImage;
import net.miginfocom.swing.MigLayout;

import javax.swing.JLabel;
import javax.swing.JButton;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;
import java.awt.event.ActionEvent;
import javax.swing.JSpinner;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

@SuppressWarnings("serial")
public class CustomMacroROIFinderDialog extends JPanel {

	public static final double DEFAULT_MIN_ROI_AREA = 215.0;
	public static final double DEFAULT_OVERLAP_PCT = 25.0;
	public static final double DEFAULT_ROI_MARGIN_PCT = 2.0;
	public static final double DEFAULT_HIRES_PIXEL_SIZE_UM = 0.1253;
	public static final double DEFAULT_ROI_IMAGE_SCALE_FACTOR = 0.5;

	public static final int DEFAULT_IMAGE_WIDTH = 4928;
	public static final int DEFAULT_IMAGE_HEIGHT = 3264;

	public static final String DEFAULT_CUSTOM_MACRO = 
	        "run(\"8-bit\");\n"+
            "setAutoThreshold(\"Otsu\");\n"+
	        "run(\"Gray Morphology\", \"radius=7 type=circle operator=erode\");\n"+
            "run(\"Analyze Particles...\", \"size=500-50000 exclude clear summarize add in_situ\");\n";
	private JButton btnRoiTest;
	private JLabel overlapPctLabel;
	private JLabel lblHiresPixelSize;

	DoubleSpinner overlapPct;
	DoubleSpinner hiResPixelSize;
	DoubleSpinner roiMarginPct;
	private JLabel lblImageScaling;
	DoubleSpinner roiImageScaleFactor;
	private JLabel lblHiresImageWidth;
	private JLabel lblHiresImageHeight;
	JSpinner imageWidth;
	JSpinner imageHeight;
	private JButton btnSetImageDimensions;
	private JScrollPane scrollPane;
	JTextArea customMacro;
	private JLabel lblCustomRoiFinding;

	public CustomMacroROIFinderDialog(final ROIFinder roiFinder) {
		this.setLayout(new MigLayout("", "[][grow]", "[][][][][][][][grow][]"));
        
        overlapPctLabel = new JLabel("Tile Overlap Percentage:");
        add(overlapPctLabel, "cell 0 0");
        
        overlapPct = new DoubleSpinner();
        overlapPct.setValue(new Double(DEFAULT_OVERLAP_PCT));
        add(overlapPct, "cell 1 0");
        
        lblHiresPixelSize = new JLabel("HIRes Pixel Size: ");
        add(lblHiresPixelSize, "cell 0 1");
        
        hiResPixelSize = new DoubleSpinner();
        hiResPixelSize.setValue(DEFAULT_HIRES_PIXEL_SIZE_UM);
        add(hiResPixelSize, "cell 1 1");
        
        JLabel lblRoiMarginPercentage = new JLabel("ROI Margin Percentage:");
        add(lblRoiMarginPercentage, "cell 0 2");
        
        roiMarginPct = new DoubleSpinner();
        roiMarginPct.setValue(DEFAULT_ROI_MARGIN_PCT);
        add(roiMarginPct, "cell 1 2");
        
        lblImageScaling = new JLabel("ROI Image Scale Factor (0.0-1.0):");
        add(lblImageScaling, "cell 0 3");
        
        roiImageScaleFactor = new DoubleSpinner();
        roiImageScaleFactor.setValue(DEFAULT_ROI_IMAGE_SCALE_FACTOR);
        add(roiImageScaleFactor, "cell 1 3");
        
        lblHiresImageWidth = new JLabel("HiRes Image Width:");
        add(lblHiresImageWidth, "cell 0 4");
        
        imageWidth = new JSpinner();
        imageWidth.setValue(DEFAULT_IMAGE_WIDTH);
        add(imageWidth, "cell 1 4");
        
        lblHiresImageHeight = new JLabel("HiRes Image Height:");
        add(lblHiresImageHeight, "cell 0 5");
        
        imageHeight = new JSpinner();
        imageHeight.setValue(DEFAULT_IMAGE_HEIGHT);
        add(imageHeight, "cell 1 5");
        
        btnSetImageDimensions = new JButton("Set Image Dimensions");
        btnSetImageDimensions.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try { roiFinder.script.getMMCore().snapImage(); } 
                catch (Exception e1) {throw new RuntimeException(e1);}
                TaggedImage taggedImage;
                try { taggedImage = roiFinder.script.getMMCore().getTaggedImage(); } 
                catch (Exception e1) {throw new RuntimeException(e1);}
                if (taggedImage != null) {
                    try { imageWidth.setValue(MDUtils.getWidth(taggedImage.tags)); } 
                    catch (JSONException e1) { /* do nothing */ }
                    try { imageHeight.setValue(MDUtils.getHeight(taggedImage.tags)); } 
                    catch (JSONException e1) { /* do nothing */ }

                }
            }
        });
        add(btnSetImageDimensions, "cell 1 6");
        
        btnRoiTest = new JButton("ROI Test");
        btnRoiTest.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try { roiFinder.script.getMMCore().snapImage(); } 
                catch (Exception e1) {throw new RuntimeException(e1);}
                TaggedImage taggedImage;
                try { taggedImage = roiFinder.script.getMMCore().getTaggedImage(); } 
                catch (Exception e1) {throw new RuntimeException(e1);}

                ImageLogRunner imageLogRunner = new ImageLogRunner("Test");
                Logger logger = Logger.create(null, "ROIFinder Test", null);
                Image image = new Image();
                Map<String,Config> config = new HashMap<String,Config>();
                roiFinder.process(image, taggedImage, logger, imageLogRunner, config);
                imageLogRunner.display();
            }
        });
        
        lblCustomRoiFinding = new JLabel("Custom ROI Finding Macro:");
        add(lblCustomRoiFinding, "cell 0 7");
        
        scrollPane = new JScrollPane();
        add(scrollPane, "cell 1 7,grow");
        
        customMacro = new JTextArea();
        customMacro.setText(DEFAULT_CUSTOM_MACRO);
        scrollPane.setViewportView(customMacro);
        add(btnRoiTest, "cell 0 8");
	}
	
}
