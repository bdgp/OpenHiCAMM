package org.bdgp.OpenHiCAMM.Modules;

import javax.swing.JPanel;

import org.bdgp.OpenHiCAMM.DoubleSpinner;
import org.bdgp.OpenHiCAMM.ImageLog.ImageLogRunner;
import org.bdgp.OpenHiCAMM.Logger;
import org.bdgp.OpenHiCAMM.DB.Image;

import mmcorej.TaggedImage;
import net.miginfocom.swing.MigLayout;

import javax.swing.JLabel;
import javax.swing.JButton;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

@SuppressWarnings("serial")
public class ROIFinderDialog extends JPanel {
	DoubleSpinner pixelSizeUm;
	DoubleSpinner minRoiArea;
	DoubleSpinner hiResPixelSizeUm;

	public static final double DEFAULT_PIXEL_SIZE_UM = 0.48;
	public static final double DEFAULT_HIRES_PIXEL_SIZE_UM = 0.1253;
	public static final double DEFAULT_MIN_ROI_AREA = 300000.0;
	private JLabel lblMinRoiArea;
	private JLabel lblHiresPixelSize;
	private JButton btnRoiTest;

	public ROIFinderDialog(final ROIFinder roiFinder) {
		this.setLayout(new MigLayout("", "[][grow]", "[][][][]"));
        
        lblMinRoiArea = new JLabel("Min ROI Area");
        add(lblMinRoiArea, "cell 0 0");
        
        minRoiArea = new DoubleSpinner();
        minRoiArea.setValue(new Double(DEFAULT_MIN_ROI_AREA));
        add(minRoiArea, "cell 1 0");
        
        JLabel lblPixelSizeum = new JLabel("Pixel Size (um)");
        add(lblPixelSizeum, "cell 0 1");

        pixelSizeUm = new DoubleSpinner();
        pixelSizeUm.setValue(new Double(DEFAULT_PIXEL_SIZE_UM));
        add(pixelSizeUm, "cell 1 1");
        
        lblHiresPixelSize = new JLabel("HiRes Pixel Size (um)");
        add(lblHiresPixelSize, "cell 0 2");
        
        hiResPixelSizeUm = new DoubleSpinner();
        hiResPixelSizeUm.setValue(new Double(DEFAULT_HIRES_PIXEL_SIZE_UM));
        add(hiResPixelSizeUm, "cell 1 2");
        
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
                roiFinder.process(image, taggedImage, logger, imageLogRunner, (Double)minRoiArea.getValue());
                imageLogRunner.display();
            }
        });
        add(btnRoiTest, "cell 0 3");
	}
	
}
