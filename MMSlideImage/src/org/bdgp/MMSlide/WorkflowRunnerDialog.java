package org.bdgp.MMSlide;

import javax.swing.JDialog;
import net.miginfocom.swing.MigLayout;
import javax.swing.JTextPane;
import javax.swing.JButton;
import javax.swing.JProgressBar;
import javax.swing.JLabel;

@SuppressWarnings("serial")
public class WorkflowRunnerDialog extends JDialog {
    public WorkflowRunnerDialog() {
        getContentPane().setLayout(new MigLayout("", "[][grow]", "[grow][][]"));
        
        JLabel lblLogOutput = new JLabel("Log Output");
        getContentPane().add(lblLogOutput, "cell 0 0");
        
        JTextPane textPane = new JTextPane();
        textPane.setEditable(false);
        getContentPane().add(textPane, "cell 1 0,grow");
        
        JLabel lblProgress = new JLabel("Progress");
        getContentPane().add(lblProgress, "cell 0 1");
        
        JProgressBar progressBar = new JProgressBar();
        getContentPane().add(progressBar, "cell 1 1,growx");
        
        JButton btnNewButton = new JButton("Stop");
        getContentPane().add(btnNewButton, "flowx,cell 1 2,alignx trailing");
        
        JButton btnKill = new JButton("Kill");
        getContentPane().add(btnKill, "cell 1 2");
    }

}
