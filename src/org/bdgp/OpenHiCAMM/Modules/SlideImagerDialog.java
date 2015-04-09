package org.bdgp.OpenHiCAMM.Modules;

import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.SpinnerNumberModel;

import org.bdgp.OpenHiCAMM.Dao;
import org.bdgp.OpenHiCAMM.WorkflowRunner;
import org.bdgp.OpenHiCAMM.DB.ModuleConfig;
import org.bdgp.OpenHiCAMM.DB.WorkflowModule;
import org.micromanager.dialogs.AcqControlDlg;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JRadioButton;
import javax.swing.ButtonGroup;

import static org.bdgp.OpenHiCAMM.Util.where;

import javax.swing.JSpinner;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

@SuppressWarnings("serial")
public class SlideImagerDialog extends JPanel {
	JTextField acqSettingsText;
	JTextField posListText;
	JComboBox<String> moduleId;
	private final ButtonGroup takeDummyImages = new ButtonGroup();
	JRadioButton takeDummyImagesYes;
	JRadioButton takeDummyImagesNo;
	DoubleSpinner pixelSizeUm;

	public SlideImagerDialog(final AcqControlDlg acqControlDlg, WorkflowRunner workflowRunner) {
		this.setLayout(new MigLayout("", "[grow]", "[][][][][][][][][]"));
		
		JButton btnShowAcquisitionDialog = new JButton("Show Acquisition Dialog");
		if (acqControlDlg == null) {
            btnShowAcquisitionDialog.setEnabled(false);
        }
		btnShowAcquisitionDialog.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (acqControlDlg != null) {
                    acqControlDlg.setVisible(true);
                    acqControlDlg.toFront();
                    acqControlDlg.repaint();
				}
			}
		});
		this.add(btnShowAcquisitionDialog, "cell 0 0");
		
		JLabel lblSelectMultid = new JLabel("Load Acqusition Settings File");
		this.add(lblSelectMultid, "cell 0 1");
		
		acqSettingsText = new JTextField();
		acqSettingsText.setEditable(false);
		this.add(acqSettingsText, "flowx,cell 0 2,growx");
		acqSettingsText.setColumns(10);
		
		JLabel lblLoad = new JLabel("Load Position List From File");
		this.add(lblLoad, "cell 0 3");
		
        final JFileChooser acqSettingsChooser = new JFileChooser();
        acqSettingsChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		JButton btnLoadAcqSettings = new JButton("Select File");
		btnLoadAcqSettings.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
                if (acqSettingsChooser.showDialog(SlideImagerDialog.this,"Load Acquisition Settings File") == JFileChooser.APPROVE_OPTION) {
                    acqSettingsText.setText(acqSettingsChooser.getSelectedFile().getPath());
                }
			}
		});
		this.add(btnLoadAcqSettings, "cell 0 2");
		
		posListText = new JTextField();
		posListText.setEditable(false);
		this.add(posListText, "flowx,cell 0 4,growx");
		posListText.setColumns(10);
		
		final JFileChooser posListChooser = new JFileChooser();
		posListChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		JButton btnLoadPosList = new JButton("Select File");
		btnLoadPosList.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
                if (posListChooser.showDialog(SlideImagerDialog.this,"Load Position List File") == JFileChooser.APPROVE_OPTION) {
                    posListText.setText(posListChooser.getSelectedFile().getPath());
                }
			}
		});
		this.add(btnLoadPosList, "cell 0 4");
		
		JLabel lblPositionListDb = new JLabel("Or choose the module that will generate the position list:");
		this.add(lblPositionListDb, "cell 0 5");
		
        // Populate the module ID drop-down list        
        moduleId = new JComboBox<String>();
        List<String> moduleIds = new ArrayList<String>();
        moduleIds.add("- Select -");
        Dao<WorkflowModule> modules = workflowRunner.getWorkflowDb().table(WorkflowModule.class);
        Dao<ModuleConfig> moduleConfigDao = workflowRunner.getInstanceDb().table(ModuleConfig.class);
        for (WorkflowModule module : modules.select()) {
            List<ModuleConfig> moduleConfigs = moduleConfigDao.select(
                    where("id", module.getId()).
                    and("key", "canProduceROIs").
                    and("value", "yes"));
            if (moduleConfigs.size() > 0) {
                moduleIds.add(module.getId());
            }
        }
        Collections.sort(moduleIds);
        moduleId.setModel(new DefaultComboBoxModel<String>(moduleIds.toArray(new String[0])));
        moduleId.setEnabled(true);
        this.add(moduleId, "cell 0 6,growx");
        
        JLabel lblTakeSeveralDummy = new JLabel("Take several dummy images to adjust camera?");
        add(lblTakeSeveralDummy, "flowx,cell 0 7");
        
        takeDummyImagesYes = new JRadioButton("Yes");
        takeDummyImages.add(takeDummyImagesYes);
        takeDummyImagesYes.setSelected(true);
        add(takeDummyImagesYes, "cell 0 7");
        
        takeDummyImagesNo = new JRadioButton("No");
        takeDummyImages.add(takeDummyImagesNo);
        add(takeDummyImagesNo, "cell 0 7");
        
        JLabel lblSetPixelSize = new JLabel("Set Pixel Size Per Micrometer:");
        add(lblSetPixelSize, "flowx,cell 0 8");
        
        // calculated px/um: 1.38257875001752
        pixelSizeUm = new DoubleSpinner();
        pixelSizeUm.setValue(new Double(1.383));
        add(pixelSizeUm, "cell 0 8");
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
