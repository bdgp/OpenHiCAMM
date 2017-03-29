package org.bdgp.OpenHiCAMM.Modules;

import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.JButton;

import org.bdgp.OpenHiCAMM.DoubleSpinner;
import org.bdgp.OpenHiCAMM.WorkflowRunner;
import org.micromanager.MMStudio;

import mmcorej.CMMCore;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.JRadioButton;
import javax.swing.ButtonGroup;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;

@SuppressWarnings("serial")
public class SlideSurveyorDialog extends JPanel {
	JTextField posListText;
    DoubleSpinner pixelSize;
    DoubleSpinner initialZPos;
    DoubleSpinner imageScaleFactor;
    
    JRadioButton invertXAxisYes;
    JRadioButton invertXAxisNo;
    JRadioButton invertYAxisYes;
    JRadioButton invertYAxisNo;
    JRadioButton setInitZPosNo;
    JRadioButton setInitZPosYes;

    // HiRes pixel size
	public static final double DEFAULT_PIXEL_SIZE_UM = 0.1253;
	// feasible default image scaling factor
	// 1.0 = 4.6 GB image size, 0.1 = 460 MB image size
	public static final double DEFAULT_IMAGE_SCALE_FACTOR = 0.3;

	// Default FFT options
	public static final String POSTPROCESSING_MACRO = 
	        "run(\"Bandpass Filter...\", \"filter_large=25 filter_small=15 suppress=Vertical tolerance=5 autoscale saturate\");\n"+
	        "run(\"Bandpass Filter...\", \"filter_large=25 filter_small=15 suppress=Horizontal tolerance=5 autoscale saturate\");\n";
	
	private final ButtonGroup invertXAxisGroup = new ButtonGroup();
	private final ButtonGroup invertYAxisGroup = new ButtonGroup();
	private final ButtonGroup setInitZPosGrp = new ButtonGroup();
	private JButton btnShowAcquisitionDialog;
	private JLabel lblFftFilterOptions;
	private JScrollPane scrollPane;
	JTextArea postprocessingMacro;
	
	public SlideSurveyorDialog(WorkflowRunner workflowRunner) {
		this.setLayout(new MigLayout("", "[672.00,grow][]", "[][][][][][][][][][][grow]"));
		
		btnShowAcquisitionDialog = new JButton("Show XY Position Dialog");
		btnShowAcquisitionDialog.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		        MMStudio.getInstance().showXYPositionList();
		    }
		});
		add(btnShowAcquisitionDialog, "cell 0 0");
		
		JLabel lblLoad = new JLabel("Load Position List From File");
		this.add(lblLoad, "cell 0 1");
		
        final JFileChooser acqSettingsChooser = new JFileChooser();
        acqSettingsChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		posListText = new JTextField();
		posListText.setEditable(false);
		this.add(posListText, "flowx,cell 0 2 2 1,growx");
		posListText.setColumns(10);
		
		final JFileChooser posListChooser = new JFileChooser();
		posListChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		JButton btnLoadPosList = new JButton("Select File");
		btnLoadPosList.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
                if (posListChooser.showDialog(SlideSurveyorDialog.this,"Load Position List File") == JFileChooser.APPROVE_OPTION) {
                    posListText.setText(posListChooser.getSelectedFile().getPath());
                }
			}
		});
		this.add(btnLoadPosList, "cell 0 2 2 1");
		
        JLabel lblPixelSize = new JLabel("HiRes Pixel Size: ");
        add(lblPixelSize, "flowx,cell 0 3");
        
        pixelSize = new DoubleSpinner();
        pixelSize.setValue(DEFAULT_PIXEL_SIZE_UM);
        add(pixelSize, "cell 1 3");
        
        JLabel lblInvertXAxis = new JLabel("Invert X axis? ");
        add(lblInvertXAxis, "flowx,cell 0 4");
                
                invertXAxisNo = new JRadioButton("No");
                invertXAxisGroup.add(invertXAxisNo);
                add(invertXAxisNo, "flowx,cell 1 4");
        
                invertXAxisYes = new JRadioButton("Yes");
                invertXAxisGroup.add(invertXAxisYes);
                invertXAxisYes.setSelected(true);
                add(invertXAxisYes, "cell 1 4");
        
        JLabel lblInvertYAxis = new JLabel("Invert Y axis? ");
        add(lblInvertYAxis, "flowx,cell 0 5");
        
        invertYAxisNo = new JRadioButton("No");
        invertYAxisGroup.add(invertYAxisNo);
        add(invertYAxisNo, "flowx,cell 1 5");
        
        invertYAxisYes = new JRadioButton("Yes");
        invertYAxisGroup.add(invertYAxisYes);
        invertYAxisYes.setSelected(true);
        add(invertYAxisYes, "cell 1 5");
        
        JLabel lblSetInitialZ = new JLabel("Set Initial Z Axis Position:");
        add(lblSetInitialZ, "flowx,cell 0 6");
        CMMCore mmcore = workflowRunner.getOpenHiCAMM().getApp().getMMCore();
        String focusDevice = mmcore.getFocusDevice();
        try {} 
        catch (Exception e1) {throw new RuntimeException(e1);}
        
        setInitZPosNo = new JRadioButton("No");
        setInitZPosNo.setSelected(true);
        setInitZPosGrp.add(setInitZPosNo);
        add(setInitZPosNo, "flowx,cell 1 6");
        
        JLabel lblSetImageScale = new JLabel("Set Image Scale Factor (0.0 - 1.0):");
        add(lblSetImageScale, "flowx,cell 0 7");
        
        setInitZPosYes = new JRadioButton("Yes");
        setInitZPosGrp.add(setInitZPosYes);
        add(setInitZPosYes, "cell 1 6");
        
        initialZPos = new DoubleSpinner();
        initialZPos.setEnabled(false);
        add(initialZPos, "cell 1 6");
        try { initialZPos.setValue(new Double(mmcore.getPosition(focusDevice))); } 
        catch (Exception e2) { } 
        
        JButton btnSetPosition = new JButton("Read From Device");
        btnSetPosition.setEnabled(false);
        btnSetPosition.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try { initialZPos.setValue(new Double(mmcore.getPosition(focusDevice))); } 
                catch (Exception e1) {throw new RuntimeException(e1);}
            }
        });
        add(btnSetPosition, "cell 1 6");
        
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
        add(btnGoToPosition, "cell 1 6");
        
        JButton btnGoToOrigin = new JButton("Go To Origin");
        btnGoToOrigin.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (JOptionPane.showConfirmDialog(null, 
                        "Warning: Moving the Z Axis is a potentially dangerous operation!\n"+
                        "Setting the Z Axis incorrectly could damage the objective!\n"+
                        "Are you sure you want to proceed?", 
                        "WARNING", 
                        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) 
                {
                    try { mmcore.setPosition(mmcore.getFocusDevice(), 0.0); } 
                    catch (Exception e1) {throw new RuntimeException(e1);}
                }
            }
        });
        btnGoToOrigin.setEnabled(false);
        add(btnGoToOrigin, "cell 1 6");
        
        setInitZPosNo.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    initialZPos.setEnabled(false);
                    btnGoToPosition.setEnabled(false);
                    btnSetPosition.setEnabled(false);
                    btnGoToOrigin.setEnabled(false);
                }
            }
        });

        imageScaleFactor = new DoubleSpinner();
        imageScaleFactor.setValue(new Double(DEFAULT_IMAGE_SCALE_FACTOR));
        add(imageScaleFactor, "cell 1 7");
        
        lblFftFilterOptions = new JLabel("Postprocessing Macro Script:");
        add(lblFftFilterOptions, "cell 0 9");
        
        scrollPane = new JScrollPane();
        add(scrollPane, "cell 0 10 2 1,grow");
        
        postprocessingMacro = new JTextArea();
        postprocessingMacro.setText(POSTPROCESSING_MACRO);
        scrollPane.setViewportView(postprocessingMacro);
        setInitZPosYes.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    initialZPos.setEnabled(true);
                    btnGoToPosition.setEnabled(true);
                    btnSetPosition.setEnabled(true);
                    btnGoToOrigin.setEnabled(true);
                }
            }
        });
	}
}
