package org.bdgp.MMSlide;

import java.awt.Component;
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
import javax.swing.JTabbedPane;

import org.bdgp.MMSlide.DB.Config;
import org.bdgp.MMSlide.DB.ModuleConfig;
import org.bdgp.MMSlide.Modules.Interfaces.Configuration;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;

import static org.bdgp.MMSlide.Util.where;

@SuppressWarnings("serial")
public class WorkflowConfigurationDialog extends JDialog {
	JFrame parent;
	Map<String,Configuration> configurations;
	Dao<ModuleConfig> config;
	
    public WorkflowConfigurationDialog(
            JFrame parent, 
            final Map<String,Configuration> configurations, 
            final Dao<ModuleConfig> config)
    {
	    super(parent, "Module Configuration", Dialog.ModalityType.APPLICATION_MODAL);
	    this.parent = parent;
	    this.configurations = configurations;
	    this.config = config;

	    this.setPreferredSize(new Dimension(800,700));
	    final WorkflowConfigurationDialog thisDialog = this;
        getContentPane().setLayout(new MigLayout("", "[grow][]", "[grow][]"));
        
        final JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        for (Map.Entry<String,Configuration> entry : configurations.entrySet()) {
            List<ModuleConfig> configs = config.select(where("id",entry.getKey()));
            Component panel = entry.getValue().display(configs.toArray(new Config[0]));
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
        
        final JButton btnPrevious = new JButton("< Previous");
        final JButton btnNext = new JButton("Next >");
        
        btnPrevious.setEnabled(tabbedPane.getSelectedIndex() > 0);
        btnNext.setEnabled(tabbedPane.getSelectedIndex() < tabbedPane.getTabCount()-1);
        
        tabbedPane.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                btnPrevious.setEnabled(tabbedPane.getSelectedIndex() > 0);
                btnNext.setEnabled(tabbedPane.getSelectedIndex() < tabbedPane.getTabCount()-1);
            }
        });
        
        btnPrevious.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (tabbedPane.getSelectedIndex() > 0) {
                    tabbedPane.setSelectedIndex(tabbedPane.getSelectedIndex()-1);
                }
                btnPrevious.setEnabled(tabbedPane.getSelectedIndex() > 0);
                btnNext.setEnabled(tabbedPane.getSelectedIndex() < tabbedPane.getTabCount()-1);
            }
        });
        getContentPane().add(btnPrevious, "flowx,cell 1 1");
        
        btnNext.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (tabbedPane.getSelectedIndex() < tabbedPane.getTabCount()-1) {
                    tabbedPane.setSelectedIndex(tabbedPane.getSelectedIndex()+1);
                }
                btnPrevious.setEnabled(tabbedPane.getSelectedIndex() > 0);
                btnNext.setEnabled(tabbedPane.getSelectedIndex() < tabbedPane.getTabCount()-1);
            }
        });
        getContentPane().add(btnNext, "cell 1 1,alignx right");
        
        JButton btnFinish = new JButton("Finish");
        btnFinish.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
            	if (validateConfiguration()) {
            		storeConfiguration();
                    thisDialog.dispose();
            	}
            }
        });
        getContentPane().add(btnFinish, "cell 1 1");
    }
    
    public boolean validateConfiguration() {
    	List<ValidationError> errors = new ArrayList<ValidationError>();
    	for (Map.Entry<String,Configuration> entry : configurations.entrySet()) {
    		ValidationError[] error = entry.getValue().validate();
    		if (error != null) {
    			errors.addAll(Arrays.asList(error));
    		}
    	}
    	if (errors.size() > 0) {
    		StringBuilder errorMessage = new StringBuilder("Please fix the following configuration errors:\n\n");
    		for (ValidationError error : errors) {
    			errorMessage.append(error.getMessage());
    			errorMessage.append("\n\n");
    		}
    		JOptionPane.showMessageDialog(parent, errorMessage.toString(), "Configuration Errors", JOptionPane.ERROR_MESSAGE);
    		return false;
    	}
        return true;
    }
    
    public void storeConfiguration() {
        for (Map.Entry<String,Configuration> entry : configurations.entrySet()) {
            Config[] configs = entry.getValue().retrieve();
            if (configs != null) {
                for (Config c : configs) {
                    ModuleConfig setId = new ModuleConfig(entry.getKey(), c.getKey(), c.getValue());
                    config.insertOrUpdate(setId,"id","key");
                }
            }
        }
    }
}
