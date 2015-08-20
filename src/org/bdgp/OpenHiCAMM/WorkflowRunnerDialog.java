package org.bdgp.OpenHiCAMM;

import java.awt.Dialog;
import java.awt.Dimension;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import javax.swing.JDialog;

import net.miginfocom.swing.MigLayout;

import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JButton;
import javax.swing.JProgressBar;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.TaskListener;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.Font;

@SuppressWarnings("serial")
public class WorkflowRunnerDialog extends JDialog {
    WorkflowRunner workflowRunner;
    JProgressBar progressBar;
    Integer maxTasks;
    JTextArea text;
    Set<Task> seen;
    
    public WorkflowRunnerDialog(WorkflowDialog workflowDialog, final WorkflowRunner workflowRunner) 
    {
        super(workflowDialog, "Workflow Runner", Dialog.ModalityType.DOCUMENT_MODAL);
    	final WorkflowRunnerDialog self = this;

        getContentPane().setLayout(new MigLayout("", "[][grow]", "[grow][][]"));
        setPreferredSize(new Dimension(1600,768));
        
        JLabel lblLogOutput = new JLabel("Log Output");
        getContentPane().add(lblLogOutput, "cell 0 0");
        
        text = new JTextArea();
        text.setFont(new Font("Monospaced", Font.PLAIN, 12));
        text.setEditable(false);
        
        JScrollPane textScrollPane = new JScrollPane(text);
        textScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        textScrollPane.setPreferredSize(new Dimension(1600, 768));
        
        getContentPane().add(textScrollPane, "cell 1 0,grow");
        
        JLabel lblProgress = new JLabel("Progress");
        getContentPane().add(lblProgress, "cell 0 1");
        
        progressBar = new JProgressBar();
        getContentPane().add(progressBar, "cell 1 1,growx");
        progressBar.setStringPainted(true);
        
        final JButton btnStop = new JButton("Stop");
        btnStop.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                workflowRunner.stop();
            }
        });
        getContentPane().add(btnStop, "flowx,cell 1 2,alignx trailing");
        
        seen = new HashSet<Task>();
        progressBar.setIndeterminate(false);

        // logging output
        final int MAX_LENGTH = 100;
        workflowRunner.addLogHandler(new Handler() {
            @Override public void publish(final LogRecord record) {
                SwingUtilities.invokeLater(new Runnable() {
                   @Override public void run() {
                        text.append(String.format("[%s:%s:%s] %s%n", 
                            record.getLoggerName(),
                            new Date(record.getMillis()), 
                            record.getLevel(), 
                            record.getMessage()));
                        text.setCaretPosition(text.getDocument().getLength());
                        progressBar.setString(String.format("%s%s",
                                record.getMessage().length() < MAX_LENGTH? 
                                        record.getMessage() : 
                                        record.getMessage().substring(0, MAX_LENGTH)+"...",
                                WorkflowRunnerDialog.this.maxTasks != null? 
                                        String.format(" (%.2f%%)", 
                                                ((double)seen.size() / (double)WorkflowRunnerDialog.this.maxTasks) * 100.0) : 
                                        ""));
                    }
                });
            }
            @Override public void flush() {}
            @Override public void close() throws SecurityException { }});
        
        final JButton btnClose = new JButton("Close");
        btnClose.addActionListener(new ActionListener() {
        	public void actionPerformed(ActionEvent e) {
        		self.dispose();
        	}
        });
        btnClose.setEnabled(false);
        getContentPane().add(btnClose, "cell 1 2");
        workflowRunner.addTaskListener(new TaskListener() {
            @Override public void notifyTask(final Task task) {
                SwingUtilities.invokeLater(new Runnable() {
                   @Override public void run() {
                        if (!seen.contains(task)) {
                            seen.add(task);
                            progressBar.setValue(seen.size());
                            if (WorkflowRunnerDialog.this.maxTasks != null && 
                                seen.size() == WorkflowRunnerDialog.this.maxTasks) 
                            {
                                btnStop.setEnabled(false);
                                btnClose.setEnabled(true);
                            }
                        }
                   }
                });
            }
			@Override public void stopped() {
                SwingUtilities.invokeLater(new Runnable() {
                   @Override public void run() {
                        progressBar.setValue(0);
                        btnStop.setEnabled(false);
                        btnClose.setEnabled(true);
                   }
                });
			}
            @Override
            public void taskCount(int taskCount) {
                WorkflowRunnerDialog.this.maxTasks = taskCount;
                SwingUtilities.invokeLater(new Runnable() {
                   @Override public void run() {
                        progressBar.setMaximum(WorkflowRunnerDialog.this.maxTasks);
                   }});
            }
            @Override
            public void debug(String message) {
                text.append(String.format("%s%n", message));
            }
        });
    }
    
    public void reset() {
        seen.clear();
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                text.setText("");
                progressBar.setValue(0);
                progressBar.setString("");
            }});
    }
}
