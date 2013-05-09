package org.bdgp.MMSlide;

import java.awt.Dimension;
import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import javax.swing.JDialog;
import net.miginfocom.swing.MigLayout;
import javax.swing.JTextPane;
import javax.swing.JButton;
import javax.swing.JProgressBar;
import javax.swing.JLabel;

import org.bdgp.MMSlide.DB.Task;
import org.bdgp.MMSlide.Modules.Interfaces.TaskListener;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

@SuppressWarnings("serial")
public class WorkflowRunnerDialog extends JDialog {
    private WorkflowRunner workflowRunner;
    
    public WorkflowRunnerDialog(File workflowDirectory, Integer instanceId, String startModuleId, boolean resume) {
        getContentPane().setLayout(new MigLayout("", "[][grow]", "[grow][][]"));
        setPreferredSize(new Dimension(800,600));
        
        JLabel lblLogOutput = new JLabel("Log Output");
        getContentPane().add(lblLogOutput, "cell 0 0");
        
        final JTextPane textPane = new JTextPane();
        textPane.setEditable(false);
        getContentPane().add(textPane, "cell 1 0,grow");
        
        JLabel lblProgress = new JLabel("Progress");
        getContentPane().add(lblProgress, "cell 0 1");
        
        final JProgressBar progressBar = new JProgressBar();
        getContentPane().add(progressBar, "cell 1 1,growx");
        
        JButton btnNewButton = new JButton("Stop");
        btnNewButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                workflowRunner.stop();
            }
        });
        getContentPane().add(btnNewButton, "flowx,cell 1 2,alignx trailing");
        
        JButton btnKill = new JButton("Kill");
        btnKill.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                workflowRunner.kill();
            }
        });
        getContentPane().add(btnKill, "cell 1 2");
        
        Map<String,Integer> resources = new HashMap<String,Integer>();
        Level loglevel = Level.INFO;
        workflowRunner = new WorkflowRunner(workflowDirectory, instanceId, resources, loglevel);
        
        // logging output
        workflowRunner.getLogger().addHandler(new Handler() {
            @Override public void publish(LogRecord record) {
        	    textPane.setText(String.format("%s%n%s %s %s%n", 
        	            textPane.getText(), new Date(record.getMillis()), record.getLevel(), record.getMessage()));
            }
            @Override public void flush() {}
            @Override public void close() throws SecurityException { }});
        
        // progress bar
        final Integer[] completedTasks = new Integer[1];
        completedTasks[0] = new Integer(0);
        List<Task> tasks = workflowRunner.getTaskStatus().select();
        progressBar.setIndeterminate(false);
        progressBar.setMaximum(tasks.size());
        workflowRunner.addTaskListener(new TaskListener() {
            @Override
            public void notifyTask(Task task) {
                completedTasks[0]++;
                progressBar.setValue(completedTasks[0]);
            }});
        
        workflowRunner.run(startModuleId, resume);
    }

}
