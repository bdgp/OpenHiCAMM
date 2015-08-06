package org.bdgp.OpenHiCAMM.Modules;

import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.miginfocom.swing.MigLayout;

import javax.swing.JLabel;

@SuppressWarnings("serial")
public class ROIFinderDialog extends JPanel {
	DoubleSpinner pixelSizeUm;
	DoubleSpinner minRoiArea;
	DoubleSpinner hiResPixelSizeUm;
	private JLabel lblMinRoiArea;
	private JLabel lblHiresPixelSize;
	private JLabel lblMinRelativeZ;
	private JLabel lblMaxRelativeZ;
	private JLabel lblZStepum;
	DoubleSpinner minZ;
	DoubleSpinner maxZ;
	DoubleSpinner zStep;

	public static final double DEFAULT_PIXEL_SIZE_UM = 0.4735;
	public static final double DEFAULT_MIN_ROI_AREA = 2000.0;
	public static final double DEFAULT_MIN_Z = -20.0;
	public static final double DEFAULT_MAX_Z = 20.0;
	public static final double DEFAULT_Z_STEP = 2.0;

	public ROIFinderDialog() {
		this.setLayout(new MigLayout("", "[][grow]", "[][][][][][]"));
        
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
        hiResPixelSizeUm.setValue(new Double(DEFAULT_PIXEL_SIZE_UM));
        add(hiResPixelSizeUm, "cell 1 2");
        
        lblMinRelativeZ = new JLabel("Z-start (um):");
        add(lblMinRelativeZ, "cell 0 3");
        
        minZ = new DoubleSpinner();
        minZ.setValue(new Double(DEFAULT_MIN_Z));
        add(minZ, "cell 1 3");
        
        lblMaxRelativeZ = new JLabel("Z-end (um):");
        add(lblMaxRelativeZ, "cell 0 4");
        
        maxZ = new DoubleSpinner();
        maxZ.setValue(new Double(DEFAULT_MAX_Z));
        add(maxZ, "cell 1 4");
        
        lblZStepum = new JLabel("Z-step (um):");
        add(lblZStepum, "cell 0 5");
        
        zStep = new DoubleSpinner();
        zStep.setValue(new Double(DEFAULT_Z_STEP));
        add(zStep, "cell 1 5");
	}
	
    public static class DoubleSpinner extends JSpinner {

        private static final long serialVersionUID = 1L;
        private static final double STEP_RATIO = 0.1;

        private SpinnerNumberModel model;

        public DoubleSpinner() {
            super();
            // Model setup
            model = new SpinnerNumberModel(0.0, -1000000.0, 1000000.0, 1.0);
            this.setModel(model);

            // Step recalculation
            this.addChangeListener(new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent e) {
                    Double value = getDouble();
                    // Steps are sensitive to the current magnitude of the value
                    long magnitude = Math.round(Math.log10(value));
                    double stepSize = STEP_RATIO * Math.pow(10, magnitude);
                    model.setStepSize(stepSize);
                }
            });
        }

        /**
         * Returns the current value as a Double
         */
        public Double getDouble() {
            return (Double)getValue();
        }
    }
}
