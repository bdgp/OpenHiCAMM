package org.bdgp.ColorCalibration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import javax.swing.JOptionPane;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;

import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;

/**
 * Manages different instances of ColorCalibration devices, both Java plugin and MMCore based.
 * The class is designed to be instantiated in the top level gui and used to obtain
 * the list of available focusing devices, as well as for selecting a default one.
 */

public class CalibrationManager {

	   private CMMCore core_;
	   private Vector<ColorCalibration> ccs_;
	   private Vector<String> ccPluginClassNames_;
	   private ColorCalibration currentCCDevice_;
//	   private AutofocusPropertyEditor afDlg_;
	   
	   public CalibrationManager(CMMCore core) {
	      core_ = core;
	      ccs_ = new Vector<ColorCalibration>();
	      ccPluginClassNames_ = new Vector<String>();
	      currentCCDevice_ = null;
	   }
	   
	   /**
	    * Selects a default autofocus device.
	    * @param name - device name
	    * @throws MMException
	    */
	   public void selectDevice(String name) throws MMException {
	      for (ColorCalibration cc : ccs_) {
	         if (cc.getDeviceName().equals(name)) {
	            currentCCDevice_ = cc;
	            return;
	         }
	      }
	      
	      throw new MMException(name + " not loaded.");
	   }
	   
	   /**
	    * Sets a class name for a Java af plugin.

	    * TODO: add multiple plugin devices 
	    * @param className - plugin class name
	    */
	   public void setCCPluginClassName(String className) {
		   if (! ccPluginClassNames_.contains(className))
			   ccPluginClassNames_.add(className);
	   }

	   /**
	    * Returns the current af device or null if none loaded.
	    * Callers should always use this method to obtain the current af device instead
	    * of storing the af device reference directly.
	    * @return
	    */
	   public ColorCalibration getDevice() {
	      return currentCCDevice_;
	   }
	   
	   /**
	    * Scans the system for available af devices, both plugin and core based
	    * If it has a current AFDevice, try to keep the same device as the current one
	    * Update the Autofcosu property dialog
	    * @throws MMException 
	    */
	   public void refresh() throws MMException {
	      ccs_.clear();

	      // first check core - not sure if we'll implement that for Calibration
	      /*
	      StrVector afDevs = core_.getLoadedDevicesOfType(DeviceType.AutoFocusDevice);
	      for (int i=0; i<afDevs.size(); i++) {
	         CoreAutofocus caf = new CoreAutofocus();
	         try {
	            core_.setAutoFocusDevice(afDevs.get(i));
	            caf.setMMCore(core_);
	            if (caf.getDeviceName().length() != 0) {
	               afs_.add(caf);
	               if (currentAfDevice_ == null)
	                  currentAfDevice_ = caf;
	            }
	         } catch (Exception e) {
	            ReportingUtils.showError(e);
	         }
	      }
	*/
	      // then check Java
	      try {

	    	  for (int i=0; i<ccPluginClassNames_.size(); i++) {
	    		  String name = ccPluginClassNames_.get(i);
	    		  if (name.length() != 0) {

	    			  ColorCalibration jcc = null;
	    			  try {
	    				  jcc = loadColorCalibrationPlugin(name);
	    			  } catch (Exception e) {
	                      ReportingUtils.logError(e);
	    				  ccPluginClassNames_.remove(name);
	    				  i--;
	    			  }

	    			  if (jcc != null) {
	    				  ccs_.add(jcc);
	    				  if (currentCCDevice_ == null)
	    					  currentCCDevice_ = jcc;
	    				  jcc.setMMCore(core_);
	    			  }
	    		  }
	    	  }
	      } catch (Exception e) {
	    	  ReportingUtils.logError(e);
	      }

	      // make sure the current autofocus is still in the list, otherwise set it to something...
	      boolean found = false;
	      for (ColorCalibration af : ccs_) {
	         if (af.getDeviceName().equals(currentCCDevice_.getDeviceName())) {
	            found = true;
	            currentCCDevice_ = af;
	         }
	      }
	      if (!found && ccs_.size() > 0)
	         currentCCDevice_ = ccs_.get(0);
	  
	      // Show new list in Options Dialog
	      /* not yet
	      if (afDlg_ != null) 
	         afDlg_.rebuild();
	      */

	   }
	   
	      /* not yet
	   public void showOptionsDialog() {
	      if (afDlg_ == null)
	         afDlg_ = new AutofocusPropertyEditor(this);
	      afDlg_.setVisible(true);
	      if (currentAfDevice_ != null) {
	         currentAfDevice_.applySettings();
	         currentAfDevice_.saveSettings();
	      }
	   }

	   public void closeOptionsDialog() {
	      if (afDlg_ != null)
	         afDlg_.cleanup();
	   }
	   */

	   /**
	    * Returns a list of available af device names
	    * @return - array of af names
	    */
	   public String[] getCCDevices() {
	      String ccDevs[] = new String[ccs_.size()];
	      int count = 0;
	      for (ColorCalibration cc : ccs_) {
	         ccDevs[count++] = cc.getDeviceName();
	      }
	      return ccDevs;
	   }

	   @SuppressWarnings("unchecked")
	   private ColorCalibration loadColorCalibrationPlugin(String className) throws MMException {
	      String msg = new String(className + " module.");
	      // instantiate auto-focusing module
	      ColorCalibration cc = null;
	      try {
	         Class cl = Class.forName(className);
	         cc = (ColorCalibration) cl.newInstance();
	         return cc;
	      } catch (ClassNotFoundException e) {
	         ReportingUtils.logError(e);
	         msg = className + " autofocus plugin not found.";
	      } catch (InstantiationException e) {
	          ReportingUtils.logError(e);
	          msg = className + " instantiation to Autofocus interface failed.";
	      } catch (IllegalAccessException e) {
	          ReportingUtils.logError(e);
	          msg = "Illegal access exception!";
	      } catch (NoClassDefFoundError e) {
	          ReportingUtils.logError(e);
	          msg = className + " class definition nor found.";
	      }
	      
	      // not found
	      ReportingUtils.logMessage(msg);
	      throw new MMException(msg);
	   }
	   
	   public boolean hasDevice(String dev) {
	      for (ColorCalibration cc : ccs_) {
	         if (cc.getDeviceName().equals(dev))
	            return true;
	      }
	      return false;
	   }

	   // Essentially taken as is from Autofocus code from MMStudioMainFrame
	   // IMHO, should be contained within the manager anyway
	   
	   public void loadPlugins(String plugin_dir) {

		   ArrayList<Class<?>> ccClasses = new ArrayList<Class<?>>();
		   List<Class<?>> classes;

		   try {
			   classes = JavaUtils.findClasses(new File(plugin_dir), 2);
			   for (Class<?> clazz : classes) {
				   for (Class<?> iface : clazz.getInterfaces()) {
					   //core_.logMessage("interface found: " + iface.getName());
					   if (iface == ColorCalibration.class) {
						   ccClasses.add(clazz);
					   }
				   }
			   }

		   } catch (ClassNotFoundException e1) {
			   ReportingUtils.logError(e1);
		   }

		   for (Class<?> plugin : ccClasses) {
			   try {
				   installCCPlugin(plugin);
			   } catch (Exception e) {
				   ReportingUtils.logError(e, "Attempted to install the \"" + plugin.getName() + "\" plugin .");
			   }
		   }

	   }
   
	   public String installCCPlugin(String className) {
		   try {
			   return installCCPlugin(Class.forName(className));
		   } catch (ClassNotFoundException e) {
			   String msg = "Internal error: Loading ColorCalibration plugins.";
			   ReportingUtils.logError(e, msg);
			   return msg;
		   }
	   }

	   public String installCCPlugin(Class<?> cc) {
		   String msg = new String(cc.getSimpleName() + " module loaded.");

		   try {
			   refresh();
			   setCCPluginClassName(cc.getSimpleName());
		   } catch (MMException e) {
			   msg = e.getMessage();
			   ReportingUtils.logError(e);
		   }

		   return msg;
	   }


}
