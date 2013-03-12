package org.bdgp.MMSlide;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.JComboBox;

import org.bdgp.MMSlide.DB.Task;
import org.bdgp.MMSlide.DB.WorkflowModule;
import org.bdgp.MMSlide.Modules.Start;

import net.miginfocom.swing.MigLayout;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JTextPane;
import javax.swing.JProgressBar;

@SuppressWarnings("serial")
public class WorkflowDialog extends JFrame {
    private JTextField workflowDir;
    private JFileChooser directoryChooser;

    public WorkflowDialog() {
        final WorkflowDialog thisDialog = this;
        
        directoryChooser = new JFileChooser();
        directoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        getContentPane().setLayout(new MigLayout("", "[][grow]", "[][][][][grow][]"));
        
        JLabel lblChooseWorkflowDirectory = new JLabel("Workflow Directory");
        getContentPane().add(lblChooseWorkflowDirectory, "cell 0 0,alignx trailing");
        workflowDir = new JTextField();
        workflowDir.setEditable(false);
        workflowDir.setColumns(40);
        getContentPane().add(workflowDir, "flowx,cell 1 0,growx");
        JButton chooseWorkflowDirButton = new JButton("Choose");
        getContentPane().add(chooseWorkflowDirButton, "cell 1 0");
        
        JLabel lblChooseWorkflowInstance = new JLabel("Workflow Instance");
        getContentPane().add(lblChooseWorkflowInstance, "cell 0 1,alignx trailing");
        final JComboBox<String> workflowInstance = new JComboBox<String>();
        workflowInstance.setEnabled(false);
        getContentPane().add(workflowInstance, "cell 1 1,alignx right");
        
        JLabel lblChooseStartTask = new JLabel("Start Task");
        getContentPane().add(lblChooseStartTask, "cell 0 2,alignx trailing");
        final JComboBox<String> startTask = new JComboBox<String>();
        startTask.setEnabled(false);
        getContentPane().add(startTask, "cell 1 2,alignx trailing");
        
        JLabel lblChooseAction = new JLabel("Action");
        getContentPane().add(lblChooseAction, "cell 0 3,alignx trailing");
        final JButton startButton = new JButton("Start");
        startButton.setEnabled(false);
        getContentPane().add(startButton, "flowx,cell 1 3,alignx trailing");
        final JButton resumeButton = new JButton("Resume");
        resumeButton.setEnabled(false);
        getContentPane().add(resumeButton, "cell 1 3");
        final JButton redoButton = new JButton("Redo");
        redoButton.setEnabled(false);
        getContentPane().add(redoButton, "cell 1 3");
        
        JButton btnStop = new JButton("Stop");
        btnStop.setEnabled(false);
        getContentPane().add(btnStop, "cell 1 3");
        
        JButton btnKill = new JButton("Kill");
        btnKill.setEnabled(false);
        getContentPane().add(btnKill, "cell 1 3");
        
        JLabel lblLogOutput = new JLabel("Log Output");
        getContentPane().add(lblLogOutput, "cell 0 4,alignx trailing");
        
        JTextPane textPane = new JTextPane();
        textPane.setEditable(false);
        getContentPane().add(textPane, "cell 1 4,grow");
        
        JLabel lblProgress = new JLabel("Progress");
        getContentPane().add(lblProgress, "cell 0 5,alignx trailing");
        
        JProgressBar progressBar = new JProgressBar();
        getContentPane().add(progressBar, "cell 1 5,growx");
        
        chooseWorkflowDirButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (directoryChooser.showOpenDialog(thisDialog) == JFileChooser.APPROVE_OPTION) {
                    workflowDir.setText(directoryChooser.getSelectedFile().getPath());
                    
                    // get the list of workflow instances
                    List<String> workflowInstances = new ArrayList<String>();
                    workflowInstances.add("-Create new Instance-");
                    Dao<Task> workflowStatus = Connection.file(Task.class, 
                            new File(workflowDir.getText(), WorkflowRunner.WORKFLOW_INSTANCE).getPath());
                    for (Task task : workflowStatus.select()) {
                        workflowInstances.add(task.getStorageLocation());
                    }
                    Collections.sort(workflowInstances);
                    workflowInstance.setModel(new DefaultComboBoxModel<String>(workflowInstances.toArray(new String[0])));
                    workflowInstance.setEnabled(true);
                    
                    // get list of starting modules
                    List<String> startModules = new ArrayList<String>();
                    Dao<WorkflowModule> modules = Connection.file(WorkflowModule.class, 
                            new File(workflowDir.getText(), WorkflowRunner.WORKFLOW).getPath());
                    for (WorkflowModule module : modules) {
                        if (Start.class.isAssignableFrom(module.getModule())) {
                            startModules.add(module.getId());
                        }
                    }
                    Collections.sort(startModules);
                    startTask.setModel(new DefaultComboBoxModel<String>(startModules.toArray(new String[0])));
                    startTask.setEnabled(true);
                    
                    startButton.setEnabled(true);
                    resumeButton.setEnabled(false);
                    redoButton.setEnabled(false);
                }
            }
        });
    }
}
