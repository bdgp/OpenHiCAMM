package org.bdgp.OpenHiCAMM.Modules;

import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import net.miginfocom.swing.MigLayout;

import javax.swing.JLabel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

@SuppressWarnings("serial")
public class ROIFinderDialog extends JPanel {
    DoubleSpinner pxPerUm;

	public ROIFinderDialog() {
		this.setLayout(new MigLayout("", "[grow]", "[][]"));
		
		JLabel lblSetPixelSize = new JLabel("Set Pixel Size Per um:");
		add(lblSetPixelSize, "flowx,cell 0 0");
		
		pxPerUm = new DoubleSpinner();
		pxPerUm.setValue(new Double(1.383));
		add(pxPerUm, "cell 0 0");
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
