package org.bdgp.OpenHiCAMM.Modules;

import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JButton;

import org.bdgp.OpenHiCAMM.Dao;
import org.bdgp.OpenHiCAMM.DoubleSpinner;
import org.bdgp.OpenHiCAMM.WorkflowRunner;
import org.bdgp.OpenHiCAMM.DB.ModuleConfig;
import org.bdgp.OpenHiCAMM.DB.WorkflowModule;
import org.micromanager.dialogs.AcqControlDlg;

import mmcorej.CMMCore;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.bdgp.OpenHiCAMM.Util.where;
import javax.swing.JSpinner;

@SuppressWarnings("serial")
public class SlideImagerDialog extends JPanel {
	private static final int DEFAULT_DUMMY_IMAGE_COUNT = 3;
    JTextField acqSettingsText;
	JTextField posListText;
	JComboBox<String> moduleId;
    DoubleSpinner minAutoFocus;
    DoubleSpinner maxAutoFocus;
    JSpinner dummyImageCount;
	
	public SlideImagerDialog(final AcqControlDlg acqControlDlg, final WorkflowRunner workflowRunner) {
		this.setLayout(new MigLayout("", "[grow]", "[][][][][][][][][][]"));
		
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
        
        JLabel lblTakeSeveralDummy = new JLabel("How many dummy adjustment images to take?");
        add(lblTakeSeveralDummy, "flowx,cell 0 7");
        
        JLabel lblSetMinimumAutofocus = new JLabel("Set Minimum AutoFocus Z axis value:");
        add(lblSetMinimumAutofocus, "flowx,cell 0 8");
        
        minAutoFocus = new DoubleSpinner();
        add(minAutoFocus, "cell 0 8");
        
        JLabel lblSetMaximumAutofocus = new JLabel("Set Maximum AutoFocus Z axis value: ");
        add(lblSetMaximumAutofocus, "flowx,cell 0 9");
        
        maxAutoFocus = new DoubleSpinner();
        add(maxAutoFocus, "cell 0 9");
        
        JButton minGetFromStage = new JButton("Get From Stage");
        minGetFromStage.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                CMMCore core = workflowRunner.getOpenHiCAMM().getApp().getMMCore();
                try {
                    double curDist = core.getPosition(core.getFocusDevice());
                    minAutoFocus.setValue(curDist);
                } 
                catch (Exception e1) {throw new RuntimeException(e1);}
            }
        });
        add(minGetFromStage, "cell 0 8");
        
        JButton maxGetFromStage = new JButton("Get From Stage");
        maxGetFromStage.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                CMMCore core = workflowRunner.getOpenHiCAMM().getApp().getMMCore();
                try {
                    double curDist = core.getPosition(core.getFocusDevice());
                    maxAutoFocus.setValue(curDist);
                } 
                catch (Exception e1) {throw new RuntimeException(e1);}
            }
        });
        add(maxGetFromStage, "cell 0 9");
        
        dummyImageCount = new JSpinner();
        add(dummyImageCount, "cell 0 7");
        dummyImageCount.setValue(new Integer(DEFAULT_DUMMY_IMAGE_COUNT));
	}

}
