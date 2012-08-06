package org.bdgp.MMSlide;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main implements Runnable {
    private static boolean commandLineMode = false;
    
    public static void main(String[] args) throws InvocationTargetException, InterruptedException {
        commandLineMode = true;
        
        // try to set look and feel on Linux OS
        if (System.getProperty("os.name").equals("Linux")) {
            try {
//                UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
                UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            } catch (Exception e) {}
        }
        
        // open the slide workflow dialog
        SwingUtilities.invokeAndWait(new Main());
    }
    public static boolean isCommandLineMode() {
       return commandLineMode; 
    }
    
    @Override
    public void run() {
        final WorkflowDialog d = new WorkflowDialog();
        d.setVisible(true);
        d.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        d.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                d.setVisible(false);
                System.exit(0);
            }
        });
    }
}
