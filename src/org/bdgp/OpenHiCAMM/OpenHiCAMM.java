package org.bdgp.OpenHiCAMM;

import java.awt.Dimension;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.bdgp.OpenHiCAMM.Modules.PosCalibrator;
import org.bdgp.OpenHiCAMM.Modules.BDGPROIFinder;
import org.bdgp.OpenHiCAMM.Modules.CompareImager;
import org.bdgp.OpenHiCAMM.Modules.ImageStitcher;
import org.bdgp.OpenHiCAMM.Modules.SlideImager;
import org.bdgp.OpenHiCAMM.Modules.SlideSurveyor;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Module;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Report;
import org.micromanager.MMStudio;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;

import ij.IJ;


public class OpenHiCAMM implements MMPlugin {
	public static final String MMSLIDEMODULESDIR = "lib/openhicamm_modules";
	private ScriptInterface app;
	private WorkflowDialog dialog;
	private static List<String> moduleNames = null;
	private static List<String> reportNames = null;

	/**
	 *  The menu name is stored in a static string, so Micro-Manager
	 *  can obtain it without instantiating the module
	 */
	public static String menuName = "OpenHiCAMM";	
	public static String tooltipDescription = "Automated microscope imaging workflow tool";
	
    // Add the AutoFocus plugin
	static {
        MMStudio.getInstance().getAutofocusManager().setAFPluginClassName(FastFFTAutoFocus.class.getName());
        try { MMStudio.getInstance().getAutofocusManager().refresh(); } 
        catch (MMException e) { ReportingUtils.logError(e.getMessage()); }
	}

	/**
	 * The main app calls this method to remove the module window
	 */
	public void dispose() {
		if (dialog != null) {
		    dialog.dispose();
		    dialog = null;
		}
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
			public void run() {
            	if (dialog == null || dialog.isDisposed()) dialog = new WorkflowDialog(IJ.getInstance(), OpenHiCAMM.this);
                //dialog.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                dialog.pack();
                dialog.setVisible(true);
                dialog.toFront();
                //dialog.requestFocus();
                
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
		return "1.0";
	}

	/**
	 * Returns copyright information
	 */
	public String getCopyright() {
		return "Copyright (C) 2015 Lawrence Berkeley National Laboratory";
	}
	
	private static void loadModules() {
		if (moduleNames == null || reportNames == null) {
            // Add all the builtin modules to the modules list first
            moduleNames = new ArrayList<String>();
            moduleNames.add(SlideImager.class.getName());
            moduleNames.add(SlideSurveyor.class.getName());
            moduleNames.add(CompareImager.class.getName());
            moduleNames.add(BDGPROIFinder.class.getName());
            moduleNames.add(ImageStitcher.class.getName());
            moduleNames.add(PosCalibrator.class.getName());
            
            reportNames = new ArrayList<String>();
            reportNames.add(WorkflowReport.class.getName());
            reportNames.add(ImageFileReport.class.getName());
            
            // Look in the mmslidemodules/ directory for any additional workflow modules.
            File pluginRootDir = new File(System.getProperty("org.bdgp.mmslide.module.path", MMSLIDEMODULESDIR));
            List<Class<?>> classes = JavaUtils.findAndLoadClasses(pluginRootDir, 0);
            for (Class<?> clazz : classes) { 
                if (!Modifier.isAbstract(clazz.getModifiers())) {
                    for (Class<?> iface : clazz.getInterfaces()) {
                        if (iface == Module.class) {
                            moduleNames.add(clazz.getName());
                        }
                        if (iface == Report.class) {
                            reportNames.add(clazz.getName());
                        }
                    }
                }
            }
		}
	}
	
	/**
	 * @return The list of registered modules
	 */
	public static List<String> getModuleNames() {
	    loadModules();
        return moduleNames;
    }
	
	/**
	 * @return The list of registered reports
	 */
	public static List<String> getReportNames() {
	    loadModules();
	    return reportNames;
	}
}
