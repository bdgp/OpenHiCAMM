package org.bdgp.MMSlide;

import java.awt.Dimension;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.bdgp.MMSlide.Modules.ImageStitcher;
import org.bdgp.MMSlide.Modules.ROIFinder;
import org.bdgp.MMSlide.Modules.SlideImager;
import org.bdgp.MMSlide.Modules.SlideLoader;
import org.bdgp.MMSlide.Modules.TIGenerator;
import org.bdgp.MMSlide.Modules.Interfaces.Module;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.JavaUtils;


public class MMSlide implements MMPlugin {
	public static final String MMSLIDEMODULESDIR = "mmslidemodules";
	private ScriptInterface app;
	private WorkflowDialog dialog;
	private static List<String> moduleNames;

	/**
	 *  The menu name is stored in a static string, so Micro-Manager
	 *  can obtain it without instantiating the module
	 */
	public static String menuName = "MMSlideImage";	
	public static String tooltipDescription = "Automated microscope imaging workflow tool";

	/**
	 * The main app calls this method to remove the module window
	 */
	public void dispose() {
		if (dialog != null) dialog.dispose();
	}
	
	public WorkflowDialog getDialog() {
		return dialog;
	}

	/**
	 * The main app passes its ScriptInterface to the module. This
	 * method is typically called after the module is instantiated.
	 * @param app - ScriptInterface implementation
	 */
	public void setApp(ScriptInterface app) {
		this.app = app;
	}

	public ScriptInterface getApp() {
		return this.app;
	}

	/**
	 * Open the module window
	 */
	public void show() {
		// initialize the list of modules
		getModuleNames();

        // open the slide workflow dialog
        SwingUtilities.invokeLater(new Runnable() {
            @SuppressWarnings("deprecation")
			public void run() {
            	if (dialog == null) dialog = new WorkflowDialog(MMSlide.this.app.getAcqDlg(), MMSlide.this);
                //dialog.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                dialog.pack();
                dialog.setVisible(true);
                
                // Handle uncaught exceptions by print to stderr and displaying a GUI
                // window with the stack trace.
                Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread t, Throwable e) {
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        e.printStackTrace(pw);
                        System.err.println(sw.toString());
                        
                        JTextArea text = new JTextArea(sw.toString());
                        text.setEditable(false);
                        JScrollPane textScrollPane = new JScrollPane(text);
                        textScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
                        textScrollPane.setPreferredSize(new Dimension(800, 600));
                        JOptionPane.showMessageDialog(dialog, textScrollPane);
                        dialog.dispose();
                    }
                });
            }
        });
	}

	/**
	 * The main app calls this method when hardware settings change.
	 * This call signals to the module that it needs to update whatever
	 * information it needs from the MMCore.
	 */
	public void configurationChanged() {
		// TODO: Not sure what to do here...
	}

	/**
	 * Returns a very short (few words) description of the module.
	 */
	public String getDescription() {
		return "Automated microscope imaging workflow tool";
	}

	/**
	 * Returns verbose information about the module.
	 * This may even include a short help instructions.
	 */
	public String getInfo() {
		return "Automated microscope imaging workflow tool";
	}

	/**
	 * Returns version string for the module.
	 * There is no specific required format for the version
	 */
	public String getVersion() {
		return "0.0.1-ALPHA";
	}

	/**
	 * Returns copyright information
	 */
	public String getCopyright() {
		return "Copyright (C) 2013 Lawrence Berkeley National Laboratory";
	}

	/**
	 * @return The list of registered modules from the META-INF/modules.txt files.
	 */
	public static List<String> getModuleNames() {
		if (moduleNames == null) {
            // Add all the builtin modules to the modules list first
            moduleNames = new ArrayList<String>();
            moduleNames.add(SlideLoader.class.getName());
            moduleNames.add(SlideImager.class.getName());
            moduleNames.add(ROIFinder.class.getName());
            moduleNames.add(TIGenerator.class.getName());
            moduleNames.add(ImageStitcher.class.getName());

            // Look in the mmslidemodules/ directory for any additional workflow modules.
            File pluginRootDir = new File(System.getProperty("org.bdgp.mmslide.module.path", MMSLIDEMODULESDIR));
            List<Class<?>> classes = JavaUtils.findAndLoadClasses(pluginRootDir, 0);
            for (Class<?> clazz : classes) { 
                for (Class<?> iface : clazz.getInterfaces()) {
                    if (iface == Module.class) {
                        moduleNames.add(clazz.getName());
                        break;
                    }
                }
            }
		}
        return moduleNames;
    }
}
