package org.bdgp.OpenHiCAMM.Modules;

import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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
import javax.swing.JRadioButton;
import javax.swing.ButtonGroup;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;

@SuppressWarnings("serial")
public class SlideImagerDialog extends JPanel {
	private static final int DEFAULT_DUMMY_IMAGE_COUNT = 3;
    JTextField acqSettingsText;
	JTextField posListText;
	JComboBox<String> moduleName;
    DoubleSpinner pixelSize;
    DoubleSpinner initialZPos;
    JSpinner dummyImageCount;
    
    JRadioButton invertXAxisYes;
    JRadioButton invertXAxisNo;
    JRadioButton invertYAxisYes;
    JRadioButton invertYAxisNo;
    JRadioButton setInitZPosNo;
    JRadioButton setInitZPosYes;

	public static final double DEFAULT_PIXEL_SIZE_UM = 0.48;
	private final ButtonGroup invertXAxisGroup = new ButtonGroup();
	private final ButtonGroup invertYAxisGroup = new ButtonGroup();
	private final ButtonGroup setInitZPosGrp = new ButtonGroup();
	
	public SlideImagerDialog(final AcqControlDlg acqControlDlg, final WorkflowRunner workflowRunner) {
		this.setLayout(new MigLayout("", "[grow]", "[][][][][][][][][][][][]"));
		
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
        moduleName = new JComboBox<String>();
        List<String> moduleNames = new ArrayList<String>();
        moduleNames.add("- Select -");
        Dao<WorkflowModule> modules = workflowRunner.getWorkflow();
        Dao<ModuleConfig> moduleConfigDao = workflowRunner.getModuleConfig();
        for (WorkflowModule module : modules.select()) {
            List<ModuleConfig> moduleConfigs = moduleConfigDao.select(
                    where("id", module.getId()).
                    and("key", "canProduceROIs").
                    and("value", "yes"));
            if (moduleConfigs.size() > 0) {
                moduleNames.add(module.getName());
            }
        }
        Collections.sort(moduleNames);
        moduleName.setModel(new DefaultComboBoxModel<String>(moduleNames.toArray(new String[0])));
        moduleName.setEnabled(true);
        this.add(moduleName, "cell 0 6,growx");
        
        JLabel lblTakeSeveralDummy = new JLabel("How many dummy adjustment images to take?");
        add(lblTakeSeveralDummy, "flowx,cell 0 7");
        
        dummyImageCount = new JSpinner();
        add(dummyImageCount, "cell 0 7");
        dummyImageCount.setValue(new Integer(DEFAULT_DUMMY_IMAGE_COUNT));
        
        JLabel lblPixelSize = new JLabel("Pixel Size: ");
        add(lblPixelSize, "flowx,cell 0 8");
        
        pixelSize = new DoubleSpinner();
        pixelSize.setValue(DEFAULT_PIXEL_SIZE_UM);
        add(pixelSize, "cell 0 8");
        
        JLabel lblInvertXAxis = new JLabel("Invert X axis? ");
        add(lblInvertXAxis, "flowx,cell 0 9");
        
        invertXAxisNo = new JRadioButton("No");
        invertXAxisGroup.add(invertXAxisNo);
        add(invertXAxisNo, "cell 0 9");
        
        JLabel lblInvertYAxis = new JLabel("Invert Y axis? ");
        add(lblInvertYAxis, "flowx,cell 0 10");
        
        invertYAxisNo = new JRadioButton("No");
        invertYAxisGroup.add(invertYAxisNo);
        add(invertYAxisNo, "cell 0 10");
        
        JLabel lblSetInitialZ = new JLabel("Set Initial Z Axis Position:");
        add(lblSetInitialZ, "flowx,cell 0 11");
        
        setInitZPosNo = new JRadioButton("No");
        setInitZPosNo.setSelected(true);
        setInitZPosGrp.add(setInitZPosNo);
        add(setInitZPosNo, "cell 0 11");
        
        setInitZPosYes = new JRadioButton("Yes");
        setInitZPosGrp.add(setInitZPosYes);
        add(setInitZPosYes, "cell 0 11");
        
        initialZPos = new DoubleSpinner();
        initialZPos.setEnabled(false);
        add(initialZPos, "cell 0 11");
        CMMCore mmcore = workflowRunner.getOpenHiCAMM().getApp().getMMCore();
        String focusDevice = mmcore.getFocusDevice();
        try { initialZPos.setValue(new Double(mmcore.getPosition(focusDevice))); } 
        catch (Exception e1) {throw new RuntimeException(e1);}
        
        JButton btnSetPosition = new JButton("Read From Device");
        btnSetPosition.setEnabled(false);
        btnSetPosition.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try { initialZPos.setValue(new Double(mmcore.getPosition(focusDevice))); } 
                catch (Exception e1) {throw new RuntimeException(e1);}
            }
        });
        add(btnSetPosition, "cell 0 11");
        
        JButton btnGoToPosition = new JButton("Go To Position");
        btnGoToPosition.setEnabled(false);
        btnGoToPosition.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (JOptionPane.showConfirmDialog(null, 
                        "Warning: Moving the Z Axis is a potentially dangerous operation!\n"+
                        "Setting the Z Axis incorrectly could damage the objective!\n"+
                        "Are you sure you want to proceed?", 
                        "WARNING", 
                        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) 
                {
                    try { mmcore.setPosition(mmcore.getFocusDevice(), (Double)initialZPos.getValue()); } 
                    catch (Exception e1) {throw new RuntimeException(e1);}
                }
            }
        });
        add(btnGoToPosition, "cell 0 11");
        
        setInitZPosNo.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    initialZPos.setEnabled(false);
                    btnGoToPosition.setEnabled(false);
                    btnSetPosition.setEnabled(false);
                }
            }
        });
        setInitZPosYes.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    initialZPos.setEnabled(true);
                    btnGoToPosition.setEnabled(true);
                    btnSetPosition.setEnabled(true);
                }
            }
        });

        invertXAxisYes = new JRadioButton("Yes");
        invertXAxisGroup.add(invertXAxisYes);
        invertXAxisYes.setSelected(true);
        add(invertXAxisYes, "cell 0 9");
        
        invertYAxisYes = new JRadioButton("Yes");
        invertYAxisGroup.add(invertYAxisYes);
        invertYAxisYes.setSelected(true);
        add(invertYAxisYes, "cell 0 10");
	}

}
