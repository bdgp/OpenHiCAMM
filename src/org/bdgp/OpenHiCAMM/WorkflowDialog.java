package org.bdgp.OpenHiCAMM;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

import org.bdgp.OpenHiCAMM.ImageLog.ImageLogRecord;
import org.bdgp.OpenHiCAMM.DB.ModuleConfig;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.WorkflowInstance;
import org.bdgp.OpenHiCAMM.DB.WorkflowModule;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Configuration;

import net.miginfocom.swing.MigLayout;

import java.awt.Frame;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import static org.bdgp.OpenHiCAMM.Util.where;

/**
 * The main workflow dialog.
 * @author insitu
 */
@SuppressWarnings("serial")
public class WorkflowDialog extends JDialog {
	// Swing widgets
    JTextField workflowDir;
    JFileChooser directoryChooser;
    JComboBox<String> workflowInstance;
    JComboBox<String> startModule;
    JButton editWorkflowButton;
    JButton startButton;
    JLabel lblConfigure;
    JButton btnConfigure;
    JButton btnCreateNewInstance;

    // The Micro-Manager plugin module
    OpenHiCAMM mmslide;
    // The workflow runner module
    WorkflowRunner workflowRunner;
    private JButton btnShowImageLog;
    private JButton btnShowDatabaseManager;

    ImageLog imageLog;
    private JButton btnResume;

    public WorkflowDialog(Frame parentFrame, OpenHiCAMM mmslide) {
        super(parentFrame, "OpenHiCAMM");
        this.mmslide = mmslide;
        
        directoryChooser = new JFileChooser();
        directoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        directoryChooser.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
            }});
        getContentPane().setLayout(new MigLayout("", "[][835.00,grow]", "[][][][][][]"));
        
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
                    initWorkflowRunner(false);
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
                wf.createStorageLocation(workflowDir.getText());
                wfi.update(wf,"id");

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
                initWorkflowRunner(false);
                
                Map<String,Configuration> configurations = workflowRunner.getConfigurations();
                WorkflowConfigurationDialog config = new WorkflowConfigurationDialog(
                    WorkflowDialog.this, 
                    configurations, 
                    workflowRunner.getInstanceDb().table(ModuleConfig.class),
                    workflowRunner.getWorkflowDb().table(ModuleConfig.class));
                config.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                config.pack();
                config.setVisible(true);
            }
        });
        getContentPane().add(btnConfigure, "cell 1 3,alignx trailing");
        startButton = new JButton("Start");
        startButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Map<String,Configuration> configurations = workflowRunner.getConfigurations();
                WorkflowConfigurationDialog config = new WorkflowConfigurationDialog(
                    WorkflowDialog.this, 
                    configurations, 
                    workflowRunner.getInstanceDb().table(ModuleConfig.class),
                    workflowRunner.getWorkflowDb().table(ModuleConfig.class));
                if (config.validateConfiguration()) {
                	start(false);
                }
            }
        });
        startButton.setEnabled(false);
        getContentPane().add(startButton, "flowx,cell 1 4,alignx trailing");
        
        btnResume = new JButton("Resume");
        btnResume.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Map<String,Configuration> configurations = workflowRunner.getConfigurations();
                WorkflowConfigurationDialog config = new WorkflowConfigurationDialog(
                    WorkflowDialog.this, 
                    configurations, 
                    workflowRunner.getInstanceDb().table(ModuleConfig.class),
                    workflowRunner.getWorkflowDb().table(ModuleConfig.class));
                if (config.validateConfiguration()) {
                	start(true);
                }
            }
        });
        getContentPane().add(btnResume, "cell 1 4");

        editWorkflowButton = new JButton("Edit Workflow...");
        editWorkflowButton.setEnabled(false);
        getContentPane().add(editWorkflowButton, "cell 1 0");
        
        btnShowImageLog = new JButton("Show Image Log");
        btnShowImageLog.setEnabled(true);
        btnShowImageLog.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (WorkflowDialog.this.workflowRunner != null) {
                    List<ImageLogRecord> records = WorkflowDialog.this.workflowRunner.getImageLogRecords();
                    imageLog = new ImageLog(records);
                    imageLog.setVisible(true);
                }
            }
        });
        getContentPane().add(btnShowImageLog, "cell 1 5,alignx right");

        btnShowDatabaseManager = new JButton("Show Database Manager");
        btnShowDatabaseManager.setEnabled(true);
        btnShowDatabaseManager.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (WorkflowDialog.this.workflowRunner != null) {
                    WorkflowDialog.this.workflowRunner.getWorkflowDb().startDatabaseManager();
                    WorkflowDialog.this.workflowRunner.getInstanceDb().startDatabaseManager();
                }
            }
        });
        getContentPane().add(btnShowDatabaseManager, "cell 1 5,alignx right");
        
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
                    	initWorkflowRunner(true);
                    	WorkflowDialog.this.workflowRunner.deleteTaskRecords();
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
            	String oldWorkflowDir = workflowDir.getText();
                if (directoryChooser.showDialog(WorkflowDialog.this,"Choose Workflow Directory") == JFileChooser.APPROVE_OPTION) {
                    workflowDir.setText(directoryChooser.getSelectedFile().getPath());
                }
                // If the workflow selection changed, then load the new workflow runner
                if (!oldWorkflowDir.isEmpty() && !workflowDir.getText().equals(oldWorkflowDir)) {
                    WorkflowDialog.this.initWorkflowRunner(true);
                }
                // If a workflow directory was given, enable the edit button and refresh the UI control state
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
    
    /**
     * Catch-all function to refresh the UI controls given the state of the dialog. This should be run whenever
     * a model state change is made that can change the UI state.
     */
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

            Collections.sort(startModules);
            String startModuleId = null;
            if (startModule.getModel() != null) {
                startModuleId = (String)startModule.getItemAt(startModule.getSelectedIndex());
            }
            startModule.setModel(new DefaultComboBoxModel<String>(startModules.toArray(new String[0])));
            if (startModuleId != null) {
                startModule.setSelectedItem(startModuleId);
            }
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
            if (workflowInstances.size()>0) {
                initWorkflowRunner(false);
            }
                
            if (startModules.size() > 0) {
                btnConfigure.setEnabled(true);
                startButton.setEnabled(true);
                btnCreateNewInstance.setEnabled(true);
            }
    	}
    }

    /**
     * Create a new workflowRunner instance.
     * @param force
     */
    public void initWorkflowRunner(boolean force) {
        Integer instanceId = workflowInstance.getSelectedIndex() < 0 ? null :
            Integer.parseInt(((String)workflowInstance.getItemAt(workflowInstance.getSelectedIndex())).replaceAll("^WF",""));
        if (workflowRunner == null || instanceId == null || !instanceId.equals(workflowRunner.getInstance().getId()) || force) {
            workflowRunner = new WorkflowRunner(new File(workflowDir.getText()), instanceId, Level.INFO, mmslide);

        }
    }

    /**
     * Start the workflow runner and open the Workflow Runner dialog.
     * Resume mode does not delete and re-create the Task/TaskConfig records before starting.
     */
    public void start(boolean resume) {
        // Make sure the workflow runner is initialized
        initWorkflowRunner(false);
        // Get the selected start module
        String startModuleId = (String)startModule.getItemAt(startModule.getSelectedIndex());
        
        List<Task> tasks = workflowRunner.getTaskStatus().select(where("moduleId", startModuleId));
        if (tasks.size() > 0) {
            if (JOptionPane.showConfirmDialog(null, 
                    "There is existing run status data in the database. \n"+
                            "Are you sure you want to overwrite the existing data?", 
                            "Confirm Overwrite Existing Run", 
                            JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION) 
            { return; }
        }
        
        // re-init the logger. This ensures each workflow run gets logged to a separate file.
        workflowRunner.initLogger();

        final WorkflowRunnerDialog wrd = new WorkflowRunnerDialog(this, workflowRunner, startModuleId);
        wrd.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        wrd.pack();

        // Refresh the UI controls
        refresh();
        // Start the workflow runner
        workflowRunner.run(startModuleId, null, resume);

        // Make the workflow runner dialog visible
        SwingUtilities.invokeLater(new Runnable() {
           @Override public void run() {
                wrd.setVisible(true);
            }
        });
    }

}
