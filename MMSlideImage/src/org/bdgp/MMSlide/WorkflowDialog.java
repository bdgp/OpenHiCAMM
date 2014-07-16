package org.bdgp.MMSlide;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.JComboBox;

import org.bdgp.MMSlide.DB.ModuleConfig;
import org.bdgp.MMSlide.DB.Task;
import org.bdgp.MMSlide.DB.WorkflowInstance;
import org.bdgp.MMSlide.DB.WorkflowModule;
import org.bdgp.MMSlide.Modules.Interfaces.Configuration;
import org.bdgp.MMSlide.Modules.Interfaces.Module;

import net.miginfocom.swing.MigLayout;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import static org.bdgp.MMSlide.Util.where;

@SuppressWarnings("serial")
public class WorkflowDialog extends JFrame {
    JTextField workflowDir;
    JFileChooser directoryChooser;
    JComboBox<String> workflowInstance;
    JComboBox<String> startModule;
    JButton editWorkflowButton;
    JButton startButton;
    JButton resumeButton;
    JLabel lblConfigure;
    JButton btnConfigure;
    MMSlide mmslide;
    WorkflowRunner workflowRunner;
    private JButton btnCreateNewInstance;

    public WorkflowDialog(MMSlide mmslide) {
        super("MMSlide");
        this.mmslide = mmslide;
        
        directoryChooser = new JFileChooser();
        directoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        directoryChooser.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
            }});
        getContentPane().setLayout(new MigLayout("", "[][grow]", "[][][][][]"));
        
        JLabel lblChooseWorkflowDirectory = new JLabel("Workflow Directory");
        getContentPane().add(lblChooseWorkflowDirectory, "cell 0 0,alignx trailing");
        workflowDir = new JTextField();
        workflowDir.setEditable(false);
        workflowDir.setColumns(40);
        getContentPane().add(workflowDir, "flowx,cell 1 0,growx");
        JButton openWorkflowButton = new JButton("Choose");
        getContentPane().add(openWorkflowButton, "cell 1 0");
        
        JLabel lblChooseWorkflowInstance = new JLabel("Workflow Instance");
        getContentPane().add(lblChooseWorkflowInstance, "cell 0 1,alignx trailing");
        workflowInstance = new JComboBox<String>();
        workflowInstance.setEnabled(false);
        workflowInstance.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    initWorkflowRunner();

                    // should the resume button be enabled?
                    resumeButton.setEnabled(false);
                    if (workflowRunner != null) {
                        List<Task> tasks = workflowRunner.getTaskStatus().select();
                        if (tasks.size() > 0) {
                            resumeButton.setEnabled(true);
                        }
                    }
                }
            }});
        
        btnCreateNewInstance = new JButton("Create New Instance");
        btnCreateNewInstance.addActionListener(new ActionListener() {
        	public void actionPerformed(ActionEvent arg0) {
                Connection workflowDb = Connection.get(
                    new File(workflowDir.getText(), WorkflowRunner.WORKFLOW_DB).getPath());
                Dao<WorkflowInstance> wfi = workflowDb.table(WorkflowInstance.class);
                WorkflowInstance wf = new WorkflowInstance();
                wfi.insert(wf);

                List<String> workflowInstances = new ArrayList<String>();
                for (WorkflowInstance instance : wfi.select()) {
                    workflowInstances.add(instance.getName());
                }
                Collections.sort(workflowInstances, Collections.reverseOrder());
                workflowInstance.setModel(new DefaultComboBoxModel<String>(workflowInstances.toArray(new String[0])));
                workflowInstance.setEnabled(true);
                workflowInstance.getModel().setSelectedItem(wf.getName());
        	}
        });
        getContentPane().add(btnCreateNewInstance, "flowx,cell 1 1,alignx right");
        getContentPane().add(workflowInstance, "cell 1 1,alignx right");
        btnCreateNewInstance.setEnabled(false);
        
        JLabel lblChooseStartTask = new JLabel("Start Task");
        getContentPane().add(lblChooseStartTask, "cell 0 2,alignx trailing");
        startModule = new JComboBox<String>();
        startModule.setEnabled(false);
        startModule.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) { }
            }});
        getContentPane().add(startModule, "cell 1 2,alignx trailing");
        
        lblConfigure = new JLabel("Configure Modules");
        getContentPane().add(lblConfigure, "cell 0 3,alignx trailing");
        
        btnConfigure = new JButton("Configure...");
        btnConfigure.setEnabled(false);
        btnConfigure.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                initWorkflowRunner();
                
                Map<String,Configuration> configurations = getConfigurations();
                WorkflowConfigurationDialog config = new WorkflowConfigurationDialog(
                    WorkflowDialog.this, configurations, workflowRunner.getInstanceDb().table(ModuleConfig.class));
                config.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                config.pack();
                config.setVisible(true);
            }
        });
        getContentPane().add(btnConfigure, "cell 1 3,alignx trailing");
        startButton = new JButton("Start");
        startButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Map<String,Configuration> configurations = getConfigurations();
                WorkflowConfigurationDialog config = new WorkflowConfigurationDialog(
                    WorkflowDialog.this, configurations, workflowRunner.getInstanceDb().table(ModuleConfig.class));
                if (config.validateConfiguration()) {
                	start(false);
                }
            }
        });
        startButton.setEnabled(false);
        getContentPane().add(startButton, "flowx,cell 1 4,alignx trailing");
        
        resumeButton = new JButton("Resume");
        resumeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Map<String,Configuration> configurations = getConfigurations();
                WorkflowConfigurationDialog config = new WorkflowConfigurationDialog(
                    WorkflowDialog.this, configurations, workflowRunner.getInstanceDb().table(ModuleConfig.class));
                if (config.validateConfiguration()) {
                	start(true);
                }
            }
        });
        resumeButton.setEnabled(false);
        getContentPane().add(resumeButton, "flowx,cell 1 4, alignx trailing");
        
        editWorkflowButton = new JButton("Edit Workflow...");
        editWorkflowButton.setEnabled(false);
        getContentPane().add(editWorkflowButton, "cell 1 0");
        editWorkflowButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                WorkflowDesignerDialog designer = new WorkflowDesignerDialog(WorkflowDialog.this, new File(workflowDir.getText()));
                designer.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                designer.pack();
                designer.setVisible(true);
                designer.addWindowListener(new WindowListener() {
                    @Override public void windowOpened(WindowEvent e) { }
                    @Override public void windowClosing(WindowEvent e) { }
                    @Override public void windowClosed(WindowEvent e) { 
                        refresh();
                    }
                    @Override public void windowIconified(WindowEvent e) { }
                    @Override public void windowDeiconified(WindowEvent e) { }
                    @Override public void windowActivated(WindowEvent e) { }
                    @Override public void windowDeactivated(WindowEvent e) { }
                });
            }});
        
        openWorkflowButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (directoryChooser.showDialog(WorkflowDialog.this,"Choose Workflow Directory") == JFileChooser.APPROVE_OPTION) {
                    workflowDir.setText(directoryChooser.getSelectedFile().getPath());
                }
                if (workflowDir.getText().length() > 0) {
                    editWorkflowButton.setEnabled(true);
                    refresh();
                }
                else {
                    editWorkflowButton.setEnabled(false);
                }
            }
        });
    }
    public void refresh() {
        List<String> startModules = new ArrayList<String>();
    	if (workflowDir.getText().length() > 0) {
            Connection workflowDb = Connection.get(
                    new File(workflowDir.getText(), WorkflowRunner.WORKFLOW_DB).getPath());
            // get list of starting modules
            Dao<WorkflowModule> modules = workflowDb.table(WorkflowModule.class);
            for (WorkflowModule module : modules.select()) {
                if (module.getParentId() == null) {
                    startModules.add(module.getId());
                }
            }
            if (startModules.size() > 0) {
                Collections.sort(startModules);
                startModule.setModel(new DefaultComboBoxModel<String>(startModules.toArray(new String[0])));
                startModule.setEnabled(true);
                
                // get the list of workflow instances
                List<String> workflowInstances = new ArrayList<String>();
                Dao<WorkflowInstance> wfi = workflowDb.table(WorkflowInstance.class);
                for (WorkflowInstance instance : wfi.select()) {
                    workflowInstances.add(instance.getName());
                }
                Collections.sort(workflowInstances, Collections.reverseOrder());
                workflowInstance.setModel(new DefaultComboBoxModel<String>(workflowInstances.toArray(new String[0])));
                workflowInstance.setEnabled(true);
                
                btnConfigure.setEnabled(true);
                startButton.setEnabled(true);
                btnCreateNewInstance.setEnabled(true);

                // should the resume button be enabled?
                resumeButton.setEnabled(false);
                if (workflowRunner != null) {
                	List<Task> tasks = workflowRunner.getTaskStatus().select();
                	if (tasks.size() > 0) {
                		resumeButton.setEnabled(true);
                	}
                }
            }
    	}
    }

    public void initWorkflowRunner() {
        Integer instanceId = workflowInstance.getSelectedIndex() < 0 ? null :
            Integer.parseInt(((String)workflowInstance.getItemAt(workflowInstance.getSelectedIndex())).replaceAll("^WF",""));
        if (workflowRunner == null || instanceId == null || !instanceId.equals(workflowRunner.getInstance().getId())) {
            workflowRunner = new WorkflowRunner(new File(workflowDir.getText()), instanceId, Level.INFO, mmslide);
        }
    }

    public void start(boolean resume) {
        initWorkflowRunner();
        String startModuleId = (String)startModule.getItemAt(startModule.getSelectedIndex());
        WorkflowRunnerDialog wrd = new WorkflowRunnerDialog(this, workflowRunner, startModuleId, resume);
        wrd.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        wrd.pack();
        wrd.setVisible(true);
    }
    public Map<String,Configuration> getConfigurations() {
    	// get list of JPanels and load them with the configuration interfaces
    	initWorkflowRunner();
    	Map<String,Configuration> configurations = new LinkedHashMap<String,Configuration>();
    	Dao<WorkflowModule> modules = workflowRunner.getWorkflowDb().table(WorkflowModule.class);
    	List<WorkflowModule> ms = modules.select(where("id",startModule.getItemAt(startModule.getSelectedIndex())));

    	while (ms.size() > 0) {
    		List<WorkflowModule> newms = new ArrayList<WorkflowModule>();
    		for (WorkflowModule m : ms) {
    			try {
    				Module module = m.getModule().newInstance();
    				module.initialize(workflowRunner, m.getId());
    				configurations.put(m.getId(), module.configure());
    			}
    			catch (InstantiationException e1) {throw new RuntimeException(e1);} 
    			catch (IllegalAccessException e1) {throw new RuntimeException(e1);}

    			newms.addAll(modules.select(where("parentId",m.getId())));
    		}
    		ms = newms;
    	}
    	return configurations;
    }
}
