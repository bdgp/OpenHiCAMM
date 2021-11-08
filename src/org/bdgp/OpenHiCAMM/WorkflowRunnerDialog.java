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
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.TaskListener;

import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.awt.event.ActionEvent;
import java.awt.Font;

@SuppressWarnings("serial")
public class WorkflowRunnerDialog extends JDialog {
    WorkflowRunner workflowRunner;
    JProgressBar progressBar;
    Integer maxTasks;
    Long startTime;
    JTextArea text;
    Set<Task> seen;
    JButton btnStop;
    JButton btnClose;
    String lastMessage;
    
    public WorkflowRunnerDialog(final WorkflowDialog workflowDialog, final WorkflowRunner workflowRunner) 
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
        
        btnStop = new JButton("Stop");
        btnStop.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                workflowRunner.stop();
            }
        });
        
        btnRestartProcess = new JButton("Restart Process");
        btnRestartProcess.addActionListener(new ActionListener() {
        	public void actionPerformed(ActionEvent arg0) {
        		if (JOptionPane.showConfirmDialog(null, 
        				"Are you sure you want to re-start the process?",
        				"Re-starrt Process Confirmation",
        				JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) 
                {
        			workflowRunner.restartProcess();
                }
        	}
        });
        getContentPane().add(btnRestartProcess, "flowx,cell 1 2");
        getContentPane().add(btnStop, "cell 1 2,alignx trailing");
        
        seen = new HashSet<Task>();
        progressBar.setIndeterminate(false);

        // logging output
        final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd.HH:mm:ss.SSSZ");
        lastMessage = "";
        workflowRunner.addLogHandler(new Handler() {
            @Override public void publish(final LogRecord record) {
                SwingUtilities.invokeLater(new Runnable() {
                   @Override public void run() {
                        text.append(String.format("[%s|%s|%s] %s%n", 
                            record.getLoggerName(),
                            dateFormat.format(new Date(record.getMillis())), 
                            String.format("%-10s", record.getLevel()), 
                            record.getMessage()));
                        text.setCaretPosition(text.getDocument().getLength());
                        setProgressBarText(record.getMessage());
                        lastMessage = record.getMessage();
                    }
                });
            }
            @Override public void flush() {}
            @Override public void close() throws SecurityException { }});
        
        btnClose = new JButton("Close");
        btnClose.addActionListener(new ActionListener() {
        	public void actionPerformed(ActionEvent e) {
        		workflowDialog.refresh();
        		self.dispose();
        	}
        });
        this.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
        		workflowDialog.refresh();
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
                            setProgressBarText(lastMessage);
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
            public void addToTaskCount(int taskCount) {
                WorkflowRunnerDialog.this.maxTasks += taskCount;
                SwingUtilities.invokeLater(new Runnable() {
                   @Override public void run() {
                        progressBar.setMaximum(WorkflowRunnerDialog.this.maxTasks);
                   }});
            }
            @Override
            public void setTaskCount(int taskCount) {
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
            @Override
            public void startTime(long startTime) {
                WorkflowRunnerDialog.this.startTime = startTime;
            }
        });
    }
    
    public String getETA() {
        if (this.seen.size() > 0) {
            long elapsedTime = System.currentTimeMillis() - this.startTime;
            int tasksLeft = this.maxTasks - this.seen.size();
            long eta = (long)Math.floor((double)tasksLeft * ((double)elapsedTime / (double)this.seen.size()));
            long hours = (long)Math.floor(eta / (1000 * 60 * 60));
            long minutes = (long)Math.floor(eta / (1000 * 60)) - (hours * 60);
            double seconds = (eta / 1000.0) - (hours * 60 * 60) - (minutes * 60);
            String timeElapsed = Util.join(", ", 
                    hours > 0? String.format("%d hours", hours) : null,
                    minutes > 0? String.format("%d minutes", minutes) : null,
                    String.format("%.1f seconds", seconds));
            return timeElapsed;
        }
        return null;
    }

    public void reset() {
        seen.clear();
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                text.setText("");
                progressBar.setValue(0);
                progressBar.setString("");
                btnStop.setEnabled(true);
                btnClose.setEnabled(false);
            }});
    }
    
    final int MAX_LENGTH = 100;
    private JButton btnRestartProcess;
    public void setProgressBarText(String message) {
        progressBar.setString(String.format("%s%s%s",
                message.length() < MAX_LENGTH? 
                        message : 
                        message.substring(0, MAX_LENGTH)+"...",
                WorkflowRunnerDialog.this.maxTasks != null? 
                        String.format(" (%.2f%%)", 
                                ((double)seen.size() / (double)WorkflowRunnerDialog.this.maxTasks) * 100.0) : 
                        "",
                WorkflowRunnerDialog.this.startTime != null && 
                WorkflowRunnerDialog.this.seen.size() > 0 && 
                WorkflowRunnerDialog.this.seen.size() < WorkflowRunnerDialog.this.maxTasks?
                        String.format(" (ETA: %s)", WorkflowRunnerDialog.this.getETA()) : ""));
    }
}
