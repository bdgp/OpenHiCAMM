package org.bdgp.OpenHiCAMM;

import java.awt.Dimension;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.bdgp.OpenHiCAMM.Modules.BDGPROIFinder;
import org.bdgp.OpenHiCAMM.Modules.CompareImager;
import org.bdgp.OpenHiCAMM.Modules.CustomMacroROIFinder;
import org.bdgp.OpenHiCAMM.Modules.ImageStitcher;
import org.bdgp.OpenHiCAMM.Modules.ManualSlideLoader;
import org.bdgp.OpenHiCAMM.Modules.PosCalibrator;
import org.bdgp.OpenHiCAMM.Modules.SlideImager;
import org.bdgp.OpenHiCAMM.Modules.SlideSurveyor;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Module;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Report;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.pluginmanagement.PluginFinder;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.scijava.plugin.SciJavaPlugin;

public class OpenHiCAMM implements MenuPlugin, SciJavaPlugin {
	public static final String OPENHICAMM_MODULES_DIR = "lib/openhicamm_modules";
	private Studio app;
	private WorkflowDialog dialog;
	private static LinkedHashMap<String,Class<?>> modules = null;
	private static LinkedHashMap<String,Class<?>> reports = null;
    private static OpenHiCAMM instance = null;

    public static final String MENU_NAME = "OpenHiCAMM";
    public static final String TOOL_TIP_DESCRIPTION = "OpenHiCAMM";

	/**
	 *  The menu name is stored in a static string, so Micro-Manager
	 *  can obtain it without instantiating the module
	 */
	public static String menuName = "OpenHiCAMM";	
	public static String tooltipDescription = "Automated microscope imaging workflow tool";
	private static Boolean loadedAutofocus = false;
	
	public OpenHiCAMM() { }
	
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
	 * Open the module window
	 */
	public void show() {
		// initialize the list of modules
		getModules();

        // open the slide workflow dialog
        SwingUtilities.invokeLater(new Runnable() {
			public void run() {
            	if (dialog == null || dialog.isDisposed()) dialog = new WorkflowDialog(OpenHiCAMM.this);
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
		return "V1.0";
	}

	/**
	 * Returns copyright information
	 */
	public String getCopyright() {
		return "Copyright (C) 2015 Lawrence Berkeley National Laboratory";
	}
	
	private static void loadModules() {
		if (modules == null || reports == null) {
            // Add all the builtin modules to the modules list first
            modules = new LinkedHashMap<String,Class<?>>();
            modules.put(ManualSlideLoader.class.getName(), ManualSlideLoader.class);
            modules.put(SlideImager.class.getName(), SlideImager.class);
            modules.put(SlideSurveyor.class.getName(), SlideSurveyor.class);
            modules.put(CompareImager.class.getName(), CompareImager.class);
            modules.put(BDGPROIFinder.class.getName(), BDGPROIFinder.class);
            modules.put(CustomMacroROIFinder.class.getName(), CustomMacroROIFinder.class);
            modules.put(ImageStitcher.class.getName(), ImageStitcher.class);
            modules.put(PosCalibrator.class.getName(), PosCalibrator.class);
            
            reports = new LinkedHashMap<String, Class<?>>();
            reports.put(WorkflowReport.class.getName(), WorkflowReport.class);
            reports.put(ImageFileReport.class.getName(), ImageFileReport.class);
            
            // Look in the openhicamm_modules/ directory for any additional workflow modules.
            String pluginRootDir = System.getProperty("org.bdgp.OpenHiCAMM.modules_dir", OPENHICAMM_MODULES_DIR);
            @SuppressWarnings("rawtypes")
			List<Class> classes = PluginFinder.findPlugins(pluginRootDir);
            for (Class<?> clazz : classes) { 
                if (!Modifier.isAbstract(clazz.getModifiers())) {
                    for (Class<?> iface : clazz.getInterfaces()) {
                        if (iface == Module.class) {
                            modules.put(clazz.getName(), clazz);
                        }
                        if (iface == Report.class) {
                            reports.put(clazz.getName(), clazz);
                        }
                    }
                }
            }
		}
	}
	
	/**
	 * @return The list of registered modules
	 */
	public static Map<String,Class<?>> getModules() {
	    loadModules();
        return modules;
    }
	
	/**
	 * @return The list of registered reports
	 */
	public static Map<String,Class<?>> getReports() {
	    loadModules();
	    return reports;
	}

	@Override
	public void setContext(Studio studio) {
		this.app = studio;
	}
	
	public Studio getApp() {
		return this.app;
	}

	@Override
	public String getName() {
		return MENU_NAME;
	}

	@Override
	public String getHelpText() {
		return TOOL_TIP_DESCRIPTION;
	}

	@Override
	public String getSubMenu() {
		return "";
	}

	@Override
	public void onPluginSelected() {
        if (OpenHiCAMM.instance == null) OpenHiCAMM.instance = this;
        if (!loadedAutofocus) {
             MMStudio.getInstance().getAutofocusManager().refresh();
            loadedAutofocus = true;
        }
	    loadModules();
	    show();
	}
}
