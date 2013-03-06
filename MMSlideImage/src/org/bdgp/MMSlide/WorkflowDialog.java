package org.bdgp.MMSlide;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.JComboBox;

import org.bdgp.MMSlide.Dao.Dao;
import org.bdgp.MMSlide.Dao.Task;

import net.miginfocom.swing.MigLayout;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("serial")
public class WorkflowDialog extends JFrame {
    private JTextField workflowDir;
    private JFileChooser directoryChooser;

    public WorkflowDialog() {
        final WorkflowDialog thisDialog = this;
        
        directoryChooser = new JFileChooser();
        directoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        getContentPane().setLayout(new MigLayout("", "[][grow]", "[][][][]"));
        
        JLabel lblChooseWorkflowDirectory = new JLabel("Choose Workflow Directory");
        getContentPane().add(lblChooseWorkflowDirectory, "cell 0 0,alignx trailing");
        workflowDir = new JTextField();
        workflowDir.setEditable(false);
        workflowDir.setColumns(40);
        getContentPane().add(workflowDir, "flowx,cell 1 0,growx");
        JButton chooseWorkflowDirButton = new JButton("Choose");
        getContentPane().add(chooseWorkflowDirButton, "cell 1 0");
        
        JLabel lblChooseWorkflowInstance = new JLabel("Choose Workflow Instance");
        getContentPane().add(lblChooseWorkflowInstance, "cell 0 1,alignx trailing");
        final JComboBox<String> workflowInstance = new JComboBox<String>();
        workflowInstance.setEnabled(false);
        getContentPane().add(workflowInstance, "cell 1 1,alignx right");
        
        JLabel lblChooseStartTask = new JLabel("Choose Start Task");
        getContentPane().add(lblChooseStartTask, "cell 0 2,alignx trailing");
        final JComboBox<String> startTask = new JComboBox<String>();
        startTask.setEnabled(false);
        getContentPane().add(startTask, "cell 1 2,alignx trailing");
        
        JLabel lblChooseAction = new JLabel("Choose Action");
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
        
        chooseWorkflowDirButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (directoryChooser.showOpenDialog(thisDialog) == JFileChooser.APPROVE_OPTION) {
                    workflowDir.setText(directoryChooser.getSelectedFile().getPath());
                    
                    // get the list of workflow instances
                    List<String> workflowInstances = new ArrayList<String>();
                    workflowInstances.add("-Create new Instance-");
                    Dao<Task> workflowStatus = Dao.get(Task.class, 
                            new File(workflowDir.getText(), WorkflowRunner.WORKFLOW_STATUS_FILE).getPath());
                    workflowInstance.setEnabled(true);
                    // get list of starting tasks
                    startTask.setEnabled(true);
                }
            }
        });
    }
}
