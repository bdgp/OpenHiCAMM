package org.bdgp.MMSlide;

import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;


public class MMSlide implements MMPlugin {
   private ScriptInterface app;
   private WorkflowDialog dialog;

   /**
	*  The menu name is stored in a static string, so Micro-Manager
	*  can obtain it without instantiating the plugin
	*/
   public static String menuName = "MMSlideImage";	
	
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
       if (dialog == null) dialog = new WorkflowDialog(this);
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
}
