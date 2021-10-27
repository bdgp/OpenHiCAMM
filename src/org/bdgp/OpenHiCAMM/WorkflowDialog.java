package org.bdgp.OpenHiCAMM;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

import org.bdgp.OpenHiCAMM.ImageLog.ImageLogRecord;
import org.bdgp.OpenHiCAMM.DB.ModuleConfig;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.WorkflowModule;
import org.bdgp.OpenHiCAMM.DB.Task.Status;
import org.bdgp.OpenHiCAMM.DB.TaskDispatch;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Configuration;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Report;

import ij.IJ;
import ij.Prefs;
import mmcorej.org.json.JSONArray;
import net.miginfocom.swing.MigLayout;

import java.awt.Desktop;
import java.awt.Frame;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import static org.bdgp.OpenHiCAMM.Util.where;
import javax.swing.JSpinner;

import spark.Spark;

/**
 * The main workflow dialog.
 * @author insitu
 */
@SuppressWarnings("serial")
public class WorkflowDialog extends JDialog {
	// Swing widgets
    JTextField workflowDir;
    JFileChooser directoryChooser;
    JComboBox<String> startModule;
    String start_module;
    JButton editWorkflowButton;
    JButton startButton;
    JLabel lblConfigure;
    JButton btnConfigure;
    JButton btnCopyToNewProject;
    JComboBox<String> selectReport;

    // The Micro-Manager plugin module
    OpenHiCAMM openHiCAMM;
    // The workflow runner module
    WorkflowRunner workflowRunner;
    WorkflowRunnerDialog workflowRunnerDialog;
    JButton btnShowImageLog;
    JButton btnShowDatabaseManager;

    ImageLog imageLog;
    JButton btnResume;
    JSpinner numThreads;

    JLabel lblNumberOfThreads;
    boolean active = true;
    boolean isDisposed = false;
    
    public boolean isDisposed() {
        return isDisposed;
    }

    public WorkflowDialog(OpenHiCAMM openHiCAMM) {
    	this(openHiCAMM, (String)null, (String)null, (Integer)null);
	}

	public WorkflowDialog(OpenHiCAMM openHiCAMM, String workflow_dir, String start_module, Integer num_threads) {
        super((Frame)null, "OpenHiCAMM");
        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        this.openHiCAMM = openHiCAMM;
        
        directoryChooser = new JFileChooser();
        directoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        directoryChooser.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
            	if (!active) return;
            	refresh();
            }});
        getContentPane().setLayout(new MigLayout("", "[][787.00,grow]", "[][][][][][][]"));
        
        JLabel lblChooseWorkflowDirectory = new JLabel("Workflow Directory");
        getContentPane().add(lblChooseWorkflowDirectory, "cell 0 0,alignx trailing");
        workflowDir = new JTextField();
        workflowDir.setEditable(false);
        workflowDir.setColumns(40);
        getContentPane().add(workflowDir, "flowx,cell 1 0,growx");
        JButton openWorkflowButton = new JButton("Choose");
        getContentPane().add(openWorkflowButton, "cell 1 0");
        if (workflow_dir != null) workflowDir.setText(workflow_dir);
        
        JFileChooser newProjectChooser = new JFileChooser();
        newProjectChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        newProjectChooser.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!active) return;
                refresh();
            }});

        btnCopyToNewProject = new JButton("Copy to new Project...");
        btnCopyToNewProject.addActionListener(new ActionListener() {
        	public void actionPerformed(ActionEvent arg0) {
                if (!active) return;
                String oldWorkflowDir = workflowDir.getText();
                WorkflowRunner oldWorkflowRunner = workflowRunner;
                if (newProjectChooser.showDialog(WorkflowDialog.this,"Choose New Workflow Directory") == JFileChooser.APPROVE_OPTION) {
                    String newProjectPath = newProjectChooser.getSelectedFile().getPath();
                    // If the workflow selection changed, then load the new workflow runner
                    if (!newProjectPath.equals(oldWorkflowDir)) {
                        // init the new workflow runner
                        workflowDir.setText(newProjectPath);
                        workflowRunner = new WorkflowRunner(new File(workflowDir.getText()), Level.INFO, openHiCAMM);
                        // init the workflow runner dialog
                        workflowRunnerDialog = new WorkflowRunnerDialog(WorkflowDialog.this, workflowRunner);
                        workflowRunnerDialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                        workflowRunnerDialog.pack();
                        // copy from old to new
                        workflowRunner.copyFromProject(oldWorkflowRunner);
                        // shutdown the old workflow runner
                        oldWorkflowRunner.shutdown();
                    }
                    // If a workflow directory was given, enable the edit button and refresh the UI control state
                    if (workflowDir.getText().length() > 0) {
                        editWorkflowButton.setEnabled(true);
                    }
                    else {
                        editWorkflowButton.setEnabled(false);
                    }
                    refresh();
                }
        	}
        });
        getContentPane().add(btnCopyToNewProject, "flowx,cell 1 1,alignx right");
        btnCopyToNewProject.setEnabled(false);
        
        JLabel lblChooseStartTask = new JLabel("Start Task");
        getContentPane().add(lblChooseStartTask, "cell 0 2,alignx trailing");
        startModule = new JComboBox<String>();
        startModule.setEnabled(false);
        startModule.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
            	if (!active) return;
                if (e.getStateChange() == ItemEvent.SELECTED) { }
                refresh();
            }});
        getContentPane().add(startModule, "cell 1 2,alignx trailing");
        this.start_module = start_module;
        
        lblConfigure = new JLabel("Configure Modules");
        getContentPane().add(lblConfigure, "cell 0 3,alignx trailing");
        
        btnConfigure = new JButton("Configure...");
        btnConfigure.setEnabled(false);
        btnConfigure.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
            	if (!active) return;
                initWorkflowRunner(false);
                
                Map<String,Configuration> configurations = workflowRunner.getConfigurations();
                WorkflowConfigurationDialog config = new WorkflowConfigurationDialog(
                    WorkflowDialog.this, 
                    configurations, 
                    workflowRunner.getWorkflow(),
                    workflowRunner.getModuleConfig());
                config.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                config.pack();
                config.setVisible(true);
                refresh();
            }
        });
        getContentPane().add(btnConfigure, "cell 1 3,alignx trailing");
        startButton = new JButton("Start");
        startButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
            	if (!active) return;
                Map<String,Configuration> configurations = workflowRunner.getConfigurations();
                WorkflowConfigurationDialog config = new WorkflowConfigurationDialog(
                    WorkflowDialog.this, 
                    configurations, 
                    workflowRunner.getWorkflow(),
                    workflowRunner.getModuleConfig());
                config.storeConfiguration();
                if (config.validateConfiguration()) {
                	start(false);
                }
                refresh();
            }
        });
        
        lblNumberOfThreads = new JLabel("Number of Threads:");
        getContentPane().add(lblNumberOfThreads, "cell 0 4");
        
        numThreads = new JSpinner();
        SpinnerNumberModel numThreadsModel = new SpinnerNumberModel();
        numThreadsModel.setMinimum(1);
        numThreads.setModel(numThreadsModel);
        numThreads.setValue(num_threads != null? num_threads : Prefs.getThreads());
        getContentPane().add(numThreads, "cell 1 4,alignx right");

        startButton.setEnabled(false);
        getContentPane().add(startButton, "flowx,cell 1 5,alignx trailing");
        
        btnResume = new JButton("Resume");
        btnResume.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
            	if (!active) return;
                Map<String,Configuration> configurations = workflowRunner.getConfigurations();
                WorkflowConfigurationDialog config = new WorkflowConfigurationDialog(
                    WorkflowDialog.this, 
                    configurations, 
                    workflowRunner.getWorkflow(),
                    workflowRunner.getModuleConfig());
                config.storeConfiguration();
                if (config.validateConfiguration()) {
                	start(true);
                }
                refresh();
            }
        });
        btnResume.setEnabled(false);
        getContentPane().add(btnResume, "cell 1 5");

        editWorkflowButton = new JButton("Edit Workflow...");
        editWorkflowButton.setEnabled(false);
        getContentPane().add(editWorkflowButton, "cell 1 0");
        
        btnShowImageLog = new JButton("Show Image Log");
        btnShowImageLog.setEnabled(true);
        btnShowImageLog.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
            	if (!active) return;
                if (WorkflowDialog.this.workflowRunner != null) {
                    List<ImageLogRecord> records = WorkflowDialog.this.workflowRunner.getImageLogRecords();
                    imageLog = new ImageLog(records);
                    imageLog.setVisible(true);
                }
                refresh();
            }
        });
        getContentPane().add(btnShowImageLog, "flowx,cell 1 6,alignx right");

        btnShowDatabaseManager = new JButton("Show Database Manager");
        btnShowDatabaseManager.setEnabled(true);
        btnShowDatabaseManager.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
            	if (!active) return;
                if (WorkflowDialog.this.workflowRunner != null) {
                    WorkflowDialog.this.workflowRunner.getWorkflowDb().startDatabaseManager();
                }
                refresh();
            }
        });
        getContentPane().add(btnShowDatabaseManager, "cell 1 6,alignx right");
        
        selectReport = new JComboBox<String>();
        selectReport.setEnabled(false);
        getContentPane().add(selectReport, "cell 1 6");
        ArrayList<String> reports = new ArrayList<>();
        reports.add("--Select Report--");
        reports.addAll(OpenHiCAMM.getReports().keySet());
        selectReport.setModel(new DefaultComboBoxModel<String>(reports.toArray(new String[0])));
        selectReport.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
            	if (!active) return;
                if (e.getStateChange() == ItemEvent.SELECTED && !e.getItem().equals("--Select Report--")) { 
                	@SuppressWarnings("unchecked")
					Class<Report> reportClass = (Class<Report>)OpenHiCAMM.getReports().get(e.getItem());
                	Report report;
                	try { report = reportClass.newInstance(); } 
                	catch (InstantiationException | IllegalAccessException e1) {
						throw new RuntimeException(e1);
					}

                    // get the time the workflow completed running
                    Long workflowRunTime = null;
                    for (WorkflowModule startModule : workflowRunner.getWorkflow().select(where("parentId", null))) {
                        ModuleConfig startTimeConf = workflowRunner.getModuleConfig().selectOne(
                                where("id",startModule.getId()).
                                and("key", "startTime"));
                        if (startTimeConf != null) {
                            long startTime;
                            try { startTime = WorkflowRunner.dateFormat.parse(startTimeConf.getValue()).getTime(); } 
                            catch (ParseException e1) { throw new RuntimeException(e1); }
                            if (workflowRunTime == null || workflowRunTime < startTime) workflowRunTime = startTime;
                        }
                        ModuleConfig endTimeConf = workflowRunner.getModuleConfig().selectOne(
                                where("id",startModule.getId()).
                                and("key", "endTime"));
                        if (endTimeConf != null) {
                            long endTime;
							try { endTime = WorkflowRunner.dateFormat.parse(endTimeConf.getValue()).getTime(); } 
							catch (ParseException e1) { throw new RuntimeException(e1); }
                            if (workflowRunTime == null || workflowRunTime < endTime) workflowRunTime = endTime;
                        }
                    }
        
                    // get the report file path
                    String reportName = report.getClass().getSimpleName();
                    File reportDir = new File(new File(
                        workflowRunner.getWorkflowDir(),
                        "reports"),
                        reportName);
                    reportDir.mkdirs();
                    String reportIndex = "index.html";
                	report.initialize(workflowRunner, reportDir.toString(), reportIndex);

                    // if the report file's timestamp is older than the workflow run time,
                    // it needs to be regenerated
                	File reportIndexFile = new File(reportDir, reportIndex);
                    if (workflowRunTime == null || !reportIndexFile.exists() || reportIndexFile.lastModified() <= workflowRunTime) {
                        if (JOptionPane.showConfirmDialog(null, 
                                "Regenerate the report? This could take some time.",
                                String.format("Report %s is outdated", reportName), 
                                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) 
                        { 
                            new Thread(()->{
                                IJ.log(String.format("Generating report for %s", reportName));
                                report.runReport();
                                if (reportIndexFile.exists()) {
                                	Spark.stop();
                                	Spark.init();
                                	int port = Spark.port();
                                	Spark.ipAddress("127.0.0.1");
                                	Spark.staticFiles.externalLocation(reportDir.toString());
                                	Spark.get("/report/:name", (request, response)->{
                                		try {
                                            String name = request.params(":name");
                                            String json = request.queryParams("args");
                                            if (json == null) throw new RuntimeException("/report request given with no args query parameter!");
                                            JSONArray argsJson = new JSONArray(json);
                                            ArrayList<Object> args = new ArrayList<>();
                                            for (int i=0; i<argsJson.length(); ++i) {
                                                args.add(argsJson.get(i));
                                            }
                                            Method method = report.getClass().getDeclaredMethod(name);
                                            Object result = method.invoke(report, args.toArray(new Object[0]));
                                            return new JSONArray(Arrays.asList(result));
                                	   }
                                       catch (Exception e1) {
                                           StringWriter sw = new StringWriter();
                                           e1.printStackTrace(new PrintWriter(sw));
                                    	   IJ.log(sw.toString());
                                    	   throw e1;
                                       }
                                	});
                                	IJ.log(String.format("Report is ready. Open browser and navigate to: http://localhost:%s/"+reportIndex, port));
                                	if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                                	    try {
											Desktop.getDesktop().browse(new URI(String.format("http://localhost:%s", port)));
										} 
                                	    catch (IOException | URISyntaxException e1) {throw new RuntimeException(e1);}
                                	}
                                }
                                else {
                                    throw new RuntimeException(String.format("Report index file %s was not created!", reportIndex));
                                }
                            }).start();
                        }
                    }
                }
            }});
        
        editWorkflowButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
            	if (!active) return;
                WorkflowDesignerDialog designer = new WorkflowDesignerDialog(WorkflowDialog.this, new File(workflowDir.getText()));
                designer.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                designer.pack();
                designer.setVisible(true);
                designer.addWindowListener(new WindowAdapter() {
                    @Override public void windowClosed(WindowEvent e) { 
                    	initWorkflowRunner(true);
                        refresh();
                    }
                });
                refresh();
            }});
        
        openWorkflowButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
            	if (!active) return;
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
                }
                else {
                    editWorkflowButton.setEnabled(false);
                }
                refresh();
            }
        });
        
        refresh();
    }
    
	@Override public void dispose() {
        SwingUtilities.invokeLater(()->{
            if (workflowRunner != null) {
                JDialog dialog = new JDialog();
                dialog.setModal(true);
                dialog.setContentPane(new JOptionPane(
                    "Shutting down the database, please wait...",
                    JOptionPane.INFORMATION_MESSAGE,
                    JOptionPane.DEFAULT_OPTION,
                    null, new Object[]{}, null));
                dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
                dialog.pack();

                new Thread(()->{
                    workflowRunner.shutdown();

                    SwingUtilities.invokeLater(()->{
                        WorkflowDialog.this.workflowRunner = null;
                        WorkflowDialog.this.isDisposed = true;
                        WorkflowDialog.super.dispose();
                        dialog.dispose();
                    });
                }).start();

                dialog.setVisible(true);
            }
        });
    }
    
    /**
     * Catch-all function to refresh the UI controls given the state of the dialog. This should be run whenever
     * a model state change is made that can change the UI state.
     */
    public void refresh() {
    	try {
    		active = false;
            List<String> startModules = new ArrayList<String>();
            if (workflowDir.getText().length() > 0) {
                Connection workflowDb = Connection.get(
                        new File(workflowDir.getText(), WorkflowRunner.WORKFLOW_DB).getPath());
                // get list of starting modules
                Dao<WorkflowModule> modules = workflowDb.file(WorkflowModule.class, new File(WorkflowRunner.WORKFLOW_FILE).getPath());
                List<WorkflowModule> ms = modules.select();
                Collections.sort(ms, (a,b)->a.getPriority().compareTo(b.getPriority()));
                for (WorkflowModule module : ms) {
                    if (module.getParentId() == null) {
                        startModules.add(module.getName());
                    }
                }

                String startModuleName = null;
                if (startModule.getModel() != null) {
                    startModuleName = (String)startModule.getItemAt(startModule.getSelectedIndex());
                }
                startModule.setModel(new DefaultComboBoxModel<String>(startModules.toArray(new String[0])));
                if (startModuleName == null && startModules.size() > 0) {
                	startModuleName = start_module != null? start_module : startModules.get(0);
                }
                if (startModuleName != null) {
                    startModule.setSelectedItem(startModuleName);
                }
                startModule.setEnabled(true);
                
                initWorkflowRunner(false);
                selectReport.setEnabled(this.workflowRunner != null);
                    
                if (startModules.size() > 0) {
                    btnConfigure.setEnabled(true);
                    startButton.setEnabled(true);
                    btnCopyToNewProject.setEnabled(true);

                    if (startModuleName != null && workflowRunner != null) {
                        // If there are any tasks with status not equal to SUCCESS or FAIL, then enable
                        // the resume button.
                        btnResume.setEnabled(false);
                        WorkflowModule startModule = workflowRunner.getWorkflow().selectOneOrDie(where("name", startModuleName));
                        List<Task> tasks = workflowRunner.getTaskStatus().select(where("moduleId", startModule.getId()));
                        CHECK_TASK_STATUSES:
                        while (tasks.size() > 0) {
                            List<TaskDispatch> tds = new ArrayList<TaskDispatch>();
                            for (Task t : tasks) {
                                if (t.getStatus() != Status.SUCCESS && t.getStatus() != Status.FAIL) {
                                    btnResume.setEnabled(true);
                                    break CHECK_TASK_STATUSES;
                                }
                                tds.addAll(workflowRunner.getTaskDispatch().select(where("parentTaskId",t.getId())));
                            }
                            tasks.clear();
                            for (TaskDispatch td : tds) {
                                tasks.addAll(workflowRunner.getTaskStatus().select(where("id", td.getTaskId())));
                            }
                        }
                    }
                    else {
                        btnResume.setEnabled(false);
                    }
                }
                else {
                    btnResume.setEnabled(false);
                }
            }
    	}
    	finally {
    		active = true;
    	}
    }

    /**
     * Create a new workflowRunner instance.
     * @param force
     */
    public void initWorkflowRunner(boolean force) {
        if (workflowRunner == null || force) {
            if (workflowRunner != null) {
                workflowRunner.shutdown();
            }
            workflowRunner = new WorkflowRunner(new File(workflowDir.getText()), Level.INFO, openHiCAMM);

            workflowRunnerDialog = new WorkflowRunnerDialog(WorkflowDialog.this, workflowRunner);
            workflowRunnerDialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            workflowRunnerDialog.pack();

            String logFile = new File(workflowRunner.getWorkflowDir(), WorkflowRunner.LOG_FILE).getPath();
            try {
				workflowRunnerDialog.text.append(new String(Files.readAllBytes(Paths.get(logFile))));
			} 
            catch (IOException e) {throw new RuntimeException(e);}
            workflowRunnerDialog.text.setCaretPosition(workflowRunnerDialog.text.getDocument().getLength());
        }
    }

    /**
     * Start the workflow runner and open the Workflow Runner dialog.
     * Resume mode does not delete and re-create the Task/TaskConfig records before starting.
     */
    public void start(final boolean resume) {
        // Make sure the workflow runner is initialized
        initWorkflowRunner(false);
        this.workflowRunner.setMaxThreads((Integer)numThreads.getValue());
        // Get the selected start module
        final String startModuleName = (String)startModule.getItemAt(startModule.getSelectedIndex());
        
        if (!resume) {
            WorkflowModule startModule = workflowRunner.getWorkflow().selectOneOrDie(where("name", startModuleName));
            List<Task> tasks = workflowRunner.getTaskStatus().select(where("moduleId", startModule.getId()));
            if (tasks.size() > 0) {
                if (JOptionPane.showConfirmDialog(null, 
                        "There is existing run status data in the database. \n"+
                                "Are you sure you want to overwrite the existing data?", 
                                "Confirm Overwrite Existing Run", 
                                JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION) 
                { return; }
            }
        }
        
        // re-init the logger. This ensures each workflow run gets logged to a separate file.
        workflowRunner.initLogger();

        // Refresh the UI controls
        refresh();

        // Make the workflow runner dialog visible
        // init the workflow runner dialog
        if (workflowRunnerDialog == null) {
            workflowRunnerDialog = new WorkflowRunnerDialog(WorkflowDialog.this, workflowRunner);
            workflowRunnerDialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            workflowRunnerDialog.pack();
        } 
        else {
            workflowRunnerDialog.reset();
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                workflowRunnerDialog.setVisible(true);
            }});

        // Start the workflow runner
        workflowRunner.run(startModuleName, null, resume);
    }
}
