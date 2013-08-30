package org.bdgp.MMSlide;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bdgp.MMSlide.Modules.Interfaces.Module;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.JavaUtils;


public class MMSlide implements MMPlugin {
   public static String MODULE_LIST = "mmslidemodules.txt";
   public static String MODULE_DIR = "mmslidemodules";
   private ScriptInterface app;
   private WorkflowDialog dialog;

   /**
	*  The menu name is stored in a static string, so Micro-Manager
	*  can obtain it without instantiating the plugin
	*/
   public static String menuName = "MMSlideImage";	
   public static String tooltipDescription = "Automated microscope imaging workflow tool";

   /**
    * The main app calls this method to remove the module window
    */
   public void dispose() {
       if (dialog != null) dialog.dispose();
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
       if (dialog == null) {
           dialog = new WorkflowDialog(this);
           dialog.pack();
       }
       dialog.setVisible(true);
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
        try {
            // Try to get the module names from resource files in the classpath
            Enumeration<URL> configs = ClassLoader.getSystemResources(MODULE_LIST);
            Set<String> modules = new HashSet<String>();
            for (URL url : Collections.list(configs)) {
                BufferedReader r = new BufferedReader(
                        new InputStreamReader(url.openStream(), "utf-8"));
                String line;
                while ((line = r.readLine()) != null) {
                    Matcher m = Pattern.compile("^\\s*([^#\\s]+)").matcher(line);
                    if (m.find() && m.groupCount() > 0) {
                        Class<?> c = Class.forName(m.group(1));
                        if (!Module.class.isAssignableFrom(c)) {
                            throw new RuntimeException(
                                    "Class "+c.getName()+
                                    " does not implement the Module interface");
                        }
                        modules.add(m.group(1));
                    }
                }
            }
            // Now get module names from mmslidemodules folder in the current directory.
            // We're using the micromanager routine JavaUtils.findClasses to do this.
            List<Class<?>> classes = JavaUtils.findClasses(new File(MODULE_DIR), 2);
            for (Class<?> class_ : classes) {
                for (Class<?> iface : class_.getInterfaces()) {
                    if (iface == Module.class) {
                        modules.add(class_.getName());
                    }
                }
            }
            return new ArrayList<String>(modules);
        } 
        catch (IOException e) {throw new RuntimeException(e);}
        catch (ClassNotFoundException e) {throw new RuntimeException(e);}
    }
}
