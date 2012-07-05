package org.bdgp.MMSlide;

import mmcorej.CMMCore;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

public class AcquisitionPlugin implements MMPlugin {

	public static String menuName = "Slide Acquisiton";
	private CMMCore core_;
	private MMStudioMainFrame app_;
//	private AcquisitionDialog dialog_;
	private SlideWorkflow dialog_;


	public void configurationChanged() {
		// TODO Auto-generated method stub

	}

	public void dispose() {
	      if (dialog_ != null) {
	          dialog_.setVisible(false);
	          dialog_.dispose();
	          dialog_ = null;
	       }
	}

	public String getCopyright() {
		// TODO Auto-generated method stub
		return null;
	}

	public String getDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	public String getInfo() {
		// TODO Auto-generated method stub
		return null;
	}

	public String getVersion() {
		// TODO Auto-generated method stub
		return null;
	}

	public void setApp(ScriptInterface app) {
		app_ = (MMStudioMainFrame) app;
		core_ = app.getMMCore();
	}

	public void show() {
		// TODO Auto-generated method stub
//		dialog_ = new AcquisitionDialog(app_.getAcquisitionEngine(), null, app_);
//		dialog_.setVisible(true);
		dialog_ = new SlideWorkflow();
		dialog_.setVisible(true);
	}

}
