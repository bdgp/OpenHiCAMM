package org.bdgp.MMSlide;

import java.awt.Dialog;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.swing.JDialog;

import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import org.bdgp.MMSlide.DB.Config;
import org.bdgp.MMSlide.Modules.Interfaces.Configuration;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;

import static org.bdgp.MMSlide.Util.where;

@SuppressWarnings("serial")
public class WorkflowConfigurationDialog extends JDialog {
    public WorkflowConfigurationDialog(
            JFrame parentFrame, 
            final Map<String,Configuration> configurations, 
            final Dao<Config> config)
    {
	    super(parentFrame, "Module Configuration", Dialog.ModalityType.APPLICATION_MODAL);
	    final WorkflowConfigurationDialog self = this;
	    this.setPreferredSize(new Dimension(800,600));
	    final WorkflowConfigurationDialog thisDialog = this;
        getContentPane().setLayout(new MigLayout("", "[grow][]", "[grow][]"));
        
        final JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        for (Map.Entry<String,Configuration> entry : configurations.entrySet()) {
            List<Config> configs = config.select(where("id",entry.getKey()));
            JPanel panel = entry.getValue().display(configs);
            if (panel != null) {
                tabbedPane.add(entry.getKey(), panel);
            }
        }
        getContentPane().add(tabbedPane, "cell 0 0 2 1,grow");
        
        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                thisDialog.dispose();
            }
        });
        getContentPane().add(btnCancel, "cell 0 1");
        
        final JButton button = new JButton("< Previous");
        final JButton btnNext = new JButton("Next >");
        
        button.setEnabled(tabbedPane.getSelectedIndex() > 0);
        btnNext.setEnabled(tabbedPane.getSelectedIndex() < tabbedPane.getTabCount()-1);
        
        tabbedPane.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if (tabbedPane.getSelectedIndex() > 0) {
                    tabbedPane.setSelectedIndex(tabbedPane.getSelectedIndex()-1);
                }
                if (tabbedPane.getSelectedIndex() < tabbedPane.getTabCount()-1) {
                    tabbedPane.setSelectedIndex(tabbedPane.getSelectedIndex()+1);
                }
                button.setEnabled(tabbedPane.getSelectedIndex() > 0);
                btnNext.setEnabled(tabbedPane.getSelectedIndex() < tabbedPane.getTabCount()-1);
            }
        });
        
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (tabbedPane.getSelectedIndex() > 0) {
                    tabbedPane.setSelectedIndex(tabbedPane.getSelectedIndex()-1);
                }
                button.setEnabled(tabbedPane.getSelectedIndex() > 0);
                btnNext.setEnabled(tabbedPane.getSelectedIndex() < tabbedPane.getTabCount()-1);
            }
        });
        getContentPane().add(button, "flowx,cell 1 1");
        
        btnNext.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (tabbedPane.getSelectedIndex() < tabbedPane.getTabCount()-1) {
                    tabbedPane.setSelectedIndex(tabbedPane.getSelectedIndex()+1);
                }
                button.setEnabled(tabbedPane.getSelectedIndex() > 0);
                btnNext.setEnabled(tabbedPane.getSelectedIndex() < tabbedPane.getTabCount()-1);
            }
        });
        getContentPane().add(btnNext, "cell 1 1,alignx right");
        
        JButton btnFinish = new JButton("Finish");
        btnFinish.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                List<String> errors = new ArrayList<String>();
                for (Map.Entry<String,Configuration> entry : configurations.entrySet()) {
                    String[] error = entry.getValue().validate();
                    if (error != null) {
                        errors.addAll(Arrays.asList(error));
                    }
                }
                if (errors.size() > 0) {
                    StringBuilder errorMessage = new StringBuilder("Please fix the following configuration errors:\n\n");
                    for (String error : errors) {
                        errorMessage.append(error);
                        errorMessage.append("\n\n");
                    }
                    JOptionPane.showMessageDialog(self, errorMessage.toString(), "Configuration Errors", JOptionPane.ERROR_MESSAGE);
                }
                else {
                    for (Map.Entry<String,Configuration> entry : configurations.entrySet()) {
                        List<Config> configs = entry.getValue().retrieve();
                        if (configs != null) {
                            for (Config c : configs) {
                                Config setId = new Config(entry.getKey(), c.getKey(), c.getValue());
                                config.insertOrUpdate(setId,"id","key");
                            }
                        }
                    }
                    thisDialog.dispose();
                }
            }
        });
        getContentPane().add(btnFinish, "cell 1 1");
    }

}
