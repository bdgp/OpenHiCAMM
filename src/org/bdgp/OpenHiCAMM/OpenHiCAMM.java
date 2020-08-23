package org.bdgp.OpenHiCAMM;

import java.awt.Dimension;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.bdgp.OpenHiCAMM.Modules.PosCalibrator;
import org.bdgp.OpenHiCAMM.Modules.BDGPROIFinder;
import org.bdgp.OpenHiCAMM.Modules.CompareImager;
import org.bdgp.OpenHiCAMM.Modules.CustomMacroROIFinder;
import org.bdgp.OpenHiCAMM.Modules.ImageStitcher;
import org.bdgp.OpenHiCAMM.Modules.ManualSlideLoader;
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

public class OpenHiCAMM implements MMPlugin {
	public static final String MMSLIDEMODULESDIR = "lib/openhicamm_modules";
	private ScriptInterface app;
	private WorkflowDialog dialog;
	private static List<String> moduleNames = null;
	private static List<String> reportNames = null;
    private static Logger log = Logger.getLogger("org.bdgp.OpenHiCAMM");
    private static Boolean clientServerMode = false;
    private static OpenHiCAMM instance = null;

	/**
	 *  The menu name is stored in a static string, so Micro-Manager
	 *  can obtain it without instantiating the module
	 */
	public static String menuName = "OpenHiCAMM";	
	public static String tooltipDescription = "Automated microscope imaging workflow tool";
	private static Boolean loadedAutofocus = false;
	
	public static boolean isClentServerMode() {
		return clientServerMode;
	}
	
    // Add the AutoFocus plugin
	public OpenHiCAMM() {
		synchronized(OpenHiCAMM.instance) {
            if (OpenHiCAMM.instance == null) OpenHiCAMM.instance = this;
		}
		synchronized(OpenHiCAMM.loadedAutofocus) {
            if (!loadedAutofocus) {
                MMStudio.getInstance().getAutofocusManager().setAFPluginClassName(FastFFTAutoFocus.class.getName());
                try { MMStudio.getInstance().getAutofocusManager().refresh(); } 
                catch (MMException e) { ReportingUtils.logError(e.getMessage()); }
                loadedAutofocus = true;
            }
		}
	}
	
	public static class Command { }
	public static class SampleCommand extends Command {
		public SampleCommand() {}
	}
	public static class Result { }
	public static class Success extends Result {
		public Success() {}
	}
	public static class Fail extends Result {
		public String message;
		public Fail(String message) {
			this.message = message;
		}
	}
	
	public static void main(String[] args) {
		synchronized(OpenHiCAMM.clientServerMode) {
            clientServerMode = true;
		}

		if (args.length > 0 && args[0].equals("client")) {
			log.info(String.format("Starting OpenHiCAMM in client mode"));

			try {
                ObjectInputStream ois = new ObjectInputStream(System.in);
                ObjectOutputStream oos = new ObjectOutputStream(System.out);
                while (true) {
                    try {
                        Object o = ois.readObject();
                        if (o instanceof SampleCommand) {
                        	SampleCommand sc = (SampleCommand) o;
                        	try {
                        		// Run some command here
                        	}
                        	catch (Throwable e) {
                                oos.writeObject(new Fail(e.getMessage()));
                                continue;
                        	}
                        	oos.writeObject(new Success());
                        }
                        else {
                        	throw new RuntimeException(String.format("Unrecognized deserialized class %s", 
                        			o.getClass().getCanonicalName()));
                        }
                    }
                    catch (ClassNotFoundException e) {throw new RuntimeException(e);}
                    catch (EOFException e) { break; }
                }
			}
            catch (IOException e) {throw new RuntimeException(e);}
			System.exit(0);
		}
		else {
			log.info(String.format("Starting OpenHiCAMM in server mode"));
			new OpenHiCAMM().show();
			log.info(String.format("Starting client process"));
		}
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
            moduleNames.add(ManualSlideLoader.class.getName());
            moduleNames.add(SlideImager.class.getName());
            moduleNames.add(SlideSurveyor.class.getName());
            moduleNames.add(CompareImager.class.getName());
            moduleNames.add(BDGPROIFinder.class.getName());
            moduleNames.add(CustomMacroROIFinder.class.getName());
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
