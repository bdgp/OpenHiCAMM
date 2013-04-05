package org.bdgp.MMSlide;

import java.awt.Dialog;
import java.awt.Dimension;
import java.util.Map;

import javax.swing.JDialog;
import net.miginfocom.swing.MigLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

@SuppressWarnings("serial")
public class WorkflowConfigurationDialog extends JDialog {
    public WorkflowConfigurationDialog(JFrame parentFrame, Map<String,JPanel> jpanels) {
	    super(parentFrame, "Module Configuration", Dialog.ModalityType.APPLICATION_MODAL);
	    this.setPreferredSize(new Dimension(800,600));
	    final WorkflowConfigurationDialog thisDialog = this;
        getContentPane().setLayout(new MigLayout("", "[grow][]", "[grow][]"));
        
        JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        for (Map.Entry<String,JPanel> entry : jpanels.entrySet()) {
            tabbedPane.add(entry.getKey(), entry.getValue());
        }
        getContentPane().add(tabbedPane, "cell 0 0 2 1,grow");
        
        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                thisDialog.dispose();
            }
        });
        getContentPane().add(btnCancel, "cell 0 1");
        
        JButton button = new JButton("< Previous");
        getContentPane().add(button, "flowx,cell 1 1");
        
        JButton btnNext = new JButton("Next >");
        getContentPane().add(btnNext, "cell 1 1,alignx right");
        
        JButton btnFinish = new JButton("Finish");
        getContentPane().add(btnFinish, "cell 1 1");
    }

}
