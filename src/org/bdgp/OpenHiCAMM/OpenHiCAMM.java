package org.bdgp.OpenHiCAMM;

import java.awt.Dimension;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
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
import org.micromanager.internal.MMStudio;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.micromanager.internal.utils.JavaUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class OpenHiCAMM implements MenuPlugin, SciJavaPlugin {
	public static final String MMSLIDEMODULESDIR = "lib/openhicamm_modules";
	private Studio app;
	private WorkflowDialog dialog;
	private static List<String> moduleNames = null;
	private static List<String> reportNames = null;
    private static Logger log = Logger.getLogger("org.bdgp.OpenHiCAMM");
    private static Boolean clientServerMode = false;
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
                 MMStudio.getInstance().getAutofocusManager().refresh();
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
	public void setApp(Studio app) {
		this.app = app;
	}

	public Studio getApp() {
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
		return "V1.0";
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
            List<Class<?>> classes = findAndLoadClasses(pluginRootDir, 0);
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
	
	public static List<Class<?>> findAndLoadClasses(File directory, int recursionLevel) {
		List<Class<?>> classes = new ArrayList<Class<?>>();
		if (!directory.exists()) {
			return classes;
		}

		final URL directoryURL;
		try {
			directoryURL = directory.toURI().toURL();
		}
		catch (MalformedURLException e) {
			ReportingUtils.logError(e, "Failed to search for classes");
			return classes;
		}

		try {
			addURL(directoryURL);
		}
		catch (IOException ignore) {
			// Logged by addURL()
		}

		File[] files = directory.listFiles();
		for (File file : files) {
			final String fileName = file.getName();
			if (file.isDirectory() && recursionLevel > 0) {
				classes.addAll(findAndLoadClasses(file, recursionLevel - 1));
			} else if (fileName.endsWith(".class")) {
				final String className = stripFilenameExtension(fileName);
				try {
					classes.add(Class.forName(className));
				}
				catch (ClassNotFoundException e) {
					ReportingUtils.logError(e, "Failed to load class: " +
							className + " (expected in " + fileName + ")");
				}
			} else if (file.getName().endsWith(".jar")) {
				try {
					addURL(new URL("jar:file:" + file.getAbsolutePath() + "!/"));
					try (JarInputStream jarFile = new JarInputStream(new FileInputStream(file))) {
                        for (JarEntry jarEntry = jarFile.getNextJarEntry();
                                jarEntry != null;
                                jarEntry = jarFile.getNextJarEntry()) {
                            final String classFileName = jarEntry.getName();
                            if (classFileName.endsWith(".class")) {
                                final String className = stripFilenameExtension(classFileName).replace("/", ".");
                                try {
                                    classes.add(Class.forName(className));
                                } catch (ClassNotFoundException e) {
                                    ReportingUtils.logError(e, "Failed to load class: " +
                                            className + " (expected in " +
                                            file.getAbsolutePath() + " based on JAR entry");
                                } catch (NoClassDefFoundError ncfe) {
                                    ReportingUtils.logError(ncfe, "Failed to load class: " +
                                            className + " (expected in " +
                                            file.getAbsolutePath() + " based on JAR entry");
                                }
                            }
                        }
					}
				} catch (Exception e) {
					ReportingUtils.logError(e);
				}
			}
		}

		return classes;
	}

   private static final Class<?>[] parameters = new Class[]{URL.class};
   public static void addURL(URL u) throws IOException {
	   URLClassLoader loader = (URLClassLoader) JavaUtils.class.getClassLoader();
	   Class<?> sysclass = URLClassLoader.class;

	   try {
		   Method method = sysclass.getDeclaredMethod("addURL", parameters);
		   method.setAccessible(true);
		   method.invoke(loader, new Object[]{u});
		   ReportingUtils.logMessage("Added URL to system class loader: " + u);
	   } catch (Throwable t) {
		   ReportingUtils.logError(t, "Failed to add URL to system class loader: " + u);
		   throw new IOException("Failed to add URL to system class loader: " + u);
	   }

   }

   private static String stripFilenameExtension(String filename) {
	   int i = filename.lastIndexOf('.');
	   if (i > 0) {
		   return filename.substring(0, i);
	   } else {
		   return filename;
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

	@Override
	public void setContext(Studio studio) {
		this.app = studio;
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
	    loadModules();
	    show();
	}
}
