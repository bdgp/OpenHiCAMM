package org.bdgp.MMSlide;

import java.lang.reflect.InvocationTargetException;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    public static void main(String[] args) throws InvocationTargetException, InterruptedException {
        // try to set look and feel on Linux OS
        if (System.getProperty("os.name").equals("Linux")) {
            try {
//                UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
                UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            } catch (Exception e) {}
        }
        
        // open the slide workflow dialog
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                final WorkflowDialog dialog = new WorkflowDialog();
                dialog.pack();
                dialog.setVisible(true);
                
                Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                    public void uncaughtException(Thread t, Throwable e) {
                        System.err.println(e.toString());
                        JOptionPane.showMessageDialog(dialog, e.toString());
                    }
                });
            }
        });
    }
}
