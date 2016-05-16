package org.bdgp.OpenHiCAMM.Modules;

import javax.swing.JPanel;

import org.bdgp.OpenHiCAMM.DoubleSpinner;
import org.bdgp.OpenHiCAMM.ImageLog.ImageLogRunner;
import org.bdgp.OpenHiCAMM.Logger;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.Image;

import mmcorej.TaggedImage;
import net.miginfocom.swing.MigLayout;

import javax.swing.JLabel;
import javax.swing.JButton;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;
import java.awt.event.ActionEvent;

@SuppressWarnings("serial")
public class ROIFinderDialog extends JPanel {
	DoubleSpinner minRoiArea;

	public static final double DEFAULT_MIN_ROI_AREA = 250000.0;
	public static final double DEFAULT_OVERLAP_PCT = 25.0;
	public static final double DEFAULT_ROI_MARGIN_PCT = 2.0;
	public static final double DEFAULT_HIRES_PIXEL_SIZE_UM = 0.1253;

	private JLabel lblMinRoiArea;
	private JButton btnRoiTest;
	private JLabel overlapPctLabel;
	private JLabel lblHiresPixelSize;

	DoubleSpinner overlapPct;
	DoubleSpinner hiResPixelSize;
	DoubleSpinner roiMarginPct;

	public ROIFinderDialog(final ROIFinder roiFinder) {
		this.setLayout(new MigLayout("", "[][grow]", "[][][][][]"));
        
        lblMinRoiArea = new JLabel("Min ROI Area");
        add(lblMinRoiArea, "cell 0 0");
        
        minRoiArea = new DoubleSpinner();
        minRoiArea.setValue(new Double(DEFAULT_MIN_ROI_AREA));
        add(minRoiArea, "cell 1 0");
        
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
                config.put("minRoiArea", new Config(roiFinder.workflowModule.getId(), 
                        "minRoiArea", new Double((Double)minRoiArea.getValue()).toString()));
                roiFinder.process(image, taggedImage, logger, imageLogRunner, config);
                imageLogRunner.display();
            }
        });
        
        overlapPctLabel = new JLabel("Tile Overlap Percentage:");
        add(overlapPctLabel, "cell 0 1");
        
        overlapPct = new DoubleSpinner();
        overlapPct.setValue(new Double(DEFAULT_OVERLAP_PCT));
        add(overlapPct, "cell 1 1");
        
        lblHiresPixelSize = new JLabel("HIRes Pixel Size: ");
        add(lblHiresPixelSize, "cell 0 2");
        
        hiResPixelSize = new DoubleSpinner();
        hiResPixelSize.setValue(DEFAULT_HIRES_PIXEL_SIZE_UM);
        add(hiResPixelSize, "cell 1 2");
        
        JLabel lblRoiMarginPercentage = new JLabel("ROI Margin Percentage:");
        add(lblRoiMarginPercentage, "cell 0 3");
        
        roiMarginPct = new DoubleSpinner();
        roiMarginPct.setValue(DEFAULT_ROI_MARGIN_PCT);
        add(roiMarginPct, "cell 1 3");
        add(btnRoiTest, "cell 0 4");
	}
	
}
