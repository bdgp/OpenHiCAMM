package org.bdgp.OpenHiCAMM.Modules;

import javax.swing.JPanel;

import org.bdgp.OpenHiCAMM.WorkflowRunner;
import org.bdgp.OpenHiCAMM.DB.ModuleConfig;
import org.bdgp.OpenHiCAMM.DB.WorkflowModule;

import net.miginfocom.swing.MigLayout;
import javax.swing.JLabel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;

import static org.bdgp.OpenHiCAMM.Util.where;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("serial")
public class PosCalibratorDialog extends JPanel {
    WorkflowRunner workflowRunner;
    JComboBox<String> refSlideImagerModule;
    JComboBox<String> roiFinderModule;
    JComboBox<String> compareSlideImagerModule;

    public PosCalibratorDialog(WorkflowRunner workflowRunner) {
        this.workflowRunner = workflowRunner;

        setLayout(new MigLayout("", "[479.00,grow][]", "[][][][][][]"));
        
        JLabel lblChooseReferenceSlide = new JLabel("Choose Reference Slide Imager module:");
        add(lblChooseReferenceSlide, "cell 0 0");
        
        refSlideImagerModule = new JComboBox<String>();
        add(refSlideImagerModule, "cell 0 1,growx");
        
        // Populate the module ID drop-down list        
        List<String> canSlideImages = new ArrayList<String>();
        canSlideImages.add("- Select -");
        for (ModuleConfig mc : workflowRunner.getModuleConfig().select(where("key","canImageSlides"))) {
            canSlideImages.add(mc.getId());
        }
        Collections.sort(canSlideImages);
        refSlideImagerModule.setModel(new DefaultComboBoxModel<String>(canSlideImages.toArray(new String[]{})));
        refSlideImagerModule.setEnabled(true);
        
        JLabel lblChooseComparisonSlideimager = new JLabel("Choose Comparison SlideImager module:");
        add(lblChooseComparisonSlideimager, "cell 0 2");
        
        compareSlideImagerModule = new JComboBox<String>();
        add(compareSlideImagerModule, "cell 0 3,growx");

        compareSlideImagerModule.setModel(new DefaultComboBoxModel<String>(canSlideImages.toArray(new String[]{})));
        compareSlideImagerModule.setEnabled(true);
        
        JLabel lblChooseRoiFinder = new JLabel("Choose ROI Finder module:");
        add(lblChooseRoiFinder, "cell 0 4");
        
        roiFinderModule = new JComboBox<String>();
        add(roiFinderModule, "cell 0 5,growx");

        // Populate the module ID drop-down list        
        List<String> canProduceROIs = new ArrayList<String>();
        canProduceROIs.add("- Select -");
        for (ModuleConfig mc : workflowRunner.getModuleConfig().select(where("key","canProduceROIs"))) {
            WorkflowModule wm = workflowRunner.getWorkflow().selectOneOrDie(where("id", mc.getId()));
            if (!wm.getModuleName().equals(PosCalibrator.class.getName())) {
                canProduceROIs.add(mc.getId());
            }
        }
        Collections.sort(canProduceROIs);
        refSlideImagerModule.setModel(new DefaultComboBoxModel<String>(canProduceROIs.toArray(new String[]{})));
        refSlideImagerModule.setEnabled(true);
    }

}
