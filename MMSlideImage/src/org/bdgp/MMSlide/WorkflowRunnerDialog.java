package org.bdgp.MMSlide;

import java.awt.Dialog;
import java.awt.Dimension;
import java.util.Date;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import javax.swing.JDialog;

import net.miginfocom.swing.MigLayout;

import javax.swing.JScrollPane;
import javax.swing.JTextArea;
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
    
    public WorkflowRunnerDialog(WorkflowDialog workflowDialog, 
            WorkflowRunner runner,
            String startModuleId,
            boolean resume) 
    {
        super(workflowDialog, "Workflow Runner", Dialog.ModalityType.APPLICATION_MODAL);
    	final WorkflowRunnerDialog self = this;
        this.workflowRunner = runner;
        getContentPane().setLayout(new MigLayout("", "[][grow]", "[grow][][]"));
        setPreferredSize(new Dimension(800,600));
        
        JLabel lblLogOutput = new JLabel("Log Output");
        getContentPane().add(lblLogOutput, "cell 0 0");
        
        final JTextArea text = new JTextArea();
        text.setEditable(false);
        JScrollPane textScrollPane = new JScrollPane(text);
        textScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        textScrollPane.setPreferredSize(new Dimension(800, 600));

        getContentPane().add(textScrollPane, "cell 1 0,grow");
        
        JLabel lblProgress = new JLabel("Progress");
        getContentPane().add(lblProgress, "cell 0 1");
        
        final JProgressBar progressBar = new JProgressBar();
        getContentPane().add(progressBar, "cell 1 1,growx");
        
        final JButton btnStop = new JButton("Stop");
        btnStop.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                workflowRunner.stop();
            }
        });
        getContentPane().add(btnStop, "flowx,cell 1 2,alignx trailing");
        
        final JButton btnKill = new JButton("Kill");
        btnKill.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                workflowRunner.kill();
            }
        });
        getContentPane().add(btnKill, "cell 1 2");
        
        // logging output
        workflowRunner.addLogHandler(new Handler() {
            @Override public void publish(LogRecord record) {
        	    text.setText(String.format("%s[%s:%s:%s] %s%n", 
        	            text.getText(), 
        	            record.getLoggerName(),
        	            new Date(record.getMillis()), 
        	            record.getLevel(), 
        	            record.getMessage()));
            }
            @Override public void flush() {}
            @Override public void close() throws SecurityException { }});
        
        // progress bar
        final List<Task> tasks = workflowRunner.getTaskStatus().select();
        progressBar.setIndeterminate(false);
        progressBar.setMaximum(tasks.size());
        
        final JButton btnClose = new JButton("Close");
        btnClose.addActionListener(new ActionListener() {
        	public void actionPerformed(ActionEvent e) {
        		self.dispose();
        	}
        });
        btnClose.setEnabled(false);
        getContentPane().add(btnClose, "cell 1 2");
        workflowRunner.addTaskListener(new TaskListener() {
        	int completedTasks = 0;
        	boolean stopped = false;
            @Override public void notifyTask(Task task) {
            	if (stopped == false) {
                    completedTasks++;
                    progressBar.setValue(completedTasks);
                    if (completedTasks == tasks.size()) {
                        btnStop.setEnabled(false);
                        btnKill.setEnabled(false);
                        btnClose.setEnabled(true);
                    }
            	}
            }
			@Override public void stopped() {
                progressBar.setValue(0);
                stopped = true;
                btnStop.setEnabled(false);
                btnKill.setEnabled(false);
                btnClose.setEnabled(true);
			}
			@Override public void killed() {
                progressBar.setValue(0);
                stopped = true;
                btnStop.setEnabled(false);
                btnKill.setEnabled(false);
                btnClose.setEnabled(true);
			}});
        
        if (!resume) {
            workflowRunner.deleteTaskRecords();
            workflowRunner.createTaskRecords();
        }
        workflowRunner.run(startModuleId);
    }

}
