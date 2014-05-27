package org.bdgp.MMSlide;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;
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
import org.bdgp.MMSlide.DB.ModuleConfig;
import org.bdgp.MMSlide.Modules.Interfaces.Configuration;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

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
	    super(parent, "Module Configuration", Dialog.ModalityType.MODELESS);
	    this.parent = parent;
	    this.configurations = configurations;
	    this.config = config;

	    this.setPreferredSize(new Dimension(800,825));
	    final WorkflowConfigurationDialog thisDialog = this;
        getContentPane().setLayout(new MigLayout("", "[grow][]", "[grow][]"));
        
        final JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        for (Map.Entry<String,Configuration> entry : configurations.entrySet()) {
            List<ModuleConfig> configs = config.select(where("id",entry.getKey()));
            Component panel = entry.getValue().display(configs.toArray(new Config[0]));
            if (panel != null) {
            	if (Window.class.isAssignableFrom(panel.getClass())) {
            		tabbedPane.add(entry.getKey(), new WindowedConfigPane((Window)panel));
            	}
            	else {
                    tabbedPane.add(entry.getKey(), panel);
            	}
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
                for (int i=0; i<tabbedPane.getTabCount(); ++i) {
                    if (WindowedConfigPane.class.isAssignableFrom(tabbedPane.getComponentAt(i).getClass())) {
                        final Window window = ((WindowedConfigPane)tabbedPane.getComponentAt(i)).getWindow();
                        if (tabbedPane.getSelectedIndex() == i) {
                        	window.setLocation(tabbedPane.getComponentAt(i).getLocationOnScreen());
                        	window.setSize(tabbedPane.getComponentAt(i).getSize());
                        	window.setVisible(true);
                            java.awt.EventQueue.invokeLater(new Runnable() {
                                @Override public void run() {
                                    window.toFront();
                                    window.repaint();
                                    window.requestFocus();
                                }
                            });
                        }
                        else {
                        	window.setVisible(false);
                        }
                    }
                }
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


        this.addWindowListener(new WindowListener() {
			@Override public void windowActivated(WindowEvent arg0) {
                if (WindowedConfigPane.class.isAssignableFrom(tabbedPane.getSelectedComponent().getClass())) {
                    final Window window = ((WindowedConfigPane)tabbedPane.getSelectedComponent()).getWindow();
                    if (window.isVisible()) {
                        window.setLocation(tabbedPane.getSelectedComponent().getLocationOnScreen());
                        window.setSize(tabbedPane.getSelectedComponent().getSize());
                        java.awt.EventQueue.invokeLater(new Runnable() {
                            @Override public void run() {
                                window.toFront();
                                window.repaint();
                            }
                        });
                    }
                }
			}
			@Override public void windowClosed(WindowEvent arg0) { }
			@Override public void windowClosing(WindowEvent arg0) { }
			@Override public void windowDeactivated(WindowEvent arg0) { }
			@Override public void windowDeiconified(WindowEvent arg0) { }
			@Override public void windowIconified(WindowEvent arg0) { }
			@Override public void windowOpened(WindowEvent arg0) {
			}});
        this.addComponentListener(new ComponentListener() {
			@Override public void componentHidden(ComponentEvent arg0) { }
			@Override public void componentMoved(ComponentEvent arg0) { 
                if (WindowedConfigPane.class.isAssignableFrom(tabbedPane.getSelectedComponent().getClass())) {
                    final Window window = ((WindowedConfigPane)tabbedPane.getSelectedComponent()).getWindow();
                    window.setLocation(tabbedPane.getSelectedComponent().getLocationOnScreen());
                    window.setSize(tabbedPane.getSelectedComponent().getSize());
                    java.awt.EventQueue.invokeLater(new Runnable() {
                        @Override public void run() {
                            window.toFront();
                            window.repaint();
                        }
                    });
                }
			}
			@Override public void componentResized(ComponentEvent arg0) { 
                if (WindowedConfigPane.class.isAssignableFrom(tabbedPane.getSelectedComponent().getClass())) {
                    final Window window = ((WindowedConfigPane)tabbedPane.getSelectedComponent()).getWindow();
                    window.setLocation(tabbedPane.getSelectedComponent().getLocationOnScreen());
                    window.setSize(tabbedPane.getSelectedComponent().getSize());
                    java.awt.EventQueue.invokeLater(new Runnable() {
                        @Override public void run() {
                            window.toFront();
                            window.repaint();
                        }
                    });
                }
			}
			@Override public void componentShown(ComponentEvent arg0) { }});

        this.addWindowListener(new WindowAdapter() {
        	@Override public void windowClosing(WindowEvent we) {
                for (int i=0; i<tabbedPane.getTabCount(); ++i) {
                    if (WindowedConfigPane.class.isAssignableFrom(tabbedPane.getComponentAt(i).getClass())) {
                        Window window = ((WindowedConfigPane)tabbedPane.getComponentAt(i)).getWindow();
                        window.setVisible(false);
                    }
                }
        	}
        });
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
    
    public static class WindowedConfigPane extends JPanel {
    	Window window;
    	public WindowedConfigPane(Window window) {
    		super();
    		this.window = window;
    	}
    	public Window getWindow() {
    		return this.window;
    	}
    }
}
