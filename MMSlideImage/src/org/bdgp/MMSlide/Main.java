package org.bdgp.MMSlide;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import static org.bdgp.MMSlide.Util.map;

public class Main {
    public static Map<String,String> getConfig() {
        return map("server.port","9001");
    }
    public static void main(String[] args) throws InvocationTargetException, InterruptedException {
        // try to set look and feel on Linux OS
        if (System.getProperty("os.name").equals("Linux")) {
            try { UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel"); } catch (Exception e) {}
            try { UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel"); } catch (Exception e) {}
        }
        
        // open the slide workflow dialog
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                final WorkflowDialog dialog = new WorkflowDialog();
                dialog.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                dialog.pack();
                dialog.setVisible(true);
                
                Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                    public void uncaughtException(Thread t, Throwable e) {
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        e.printStackTrace(pw);
                        System.err.println(sw.toString());
                        
                        JTextArea text = new JTextArea(sw.toString());
                        text.setEditable(false);
                        JOptionPane.showMessageDialog(dialog, text);
                        dialog.dispose();
                    }
                });
            }
        });
    }
}
