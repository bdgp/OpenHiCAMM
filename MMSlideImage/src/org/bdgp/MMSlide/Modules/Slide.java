package org.bdgp.MMSlide.Modules;

import java.util.HashMap;
import java.util.Vector;

import org.bdgp.MMSlide.StorageManager;
import org.bdgp.MMSlide.Modules.Interfaces.MMModule;
import org.bdgp.MMSlide.Modules.Interfaces.WorkerImageCamera;
import org.bdgp.MMSlide.Modules.Interfaces.Root;
import org.bdgp.MMSlide.Modules.Interfaces.WorkerSlide;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.PositionListDlg;
import org.micromanager.api.AcquisitionEngine;
import org.micromanager.api.DeviceControlGUI;
import org.micromanager.navigation.PositionList;

public class Slide extends ModuleBase implements Root, WorkerSlide, MMModule {

	private Vector<WorkerSlide> child_slide;
	private Vector<WorkerImageCamera> child_camera;
	
	private MMStudioMainFrame gui;
    private PositionList posList_ = null;
    private PositionListDlg posListDlg_;

	public Slide(StorageManager storage) {
		super(storage);
		moduleLabel = "Slide imaging";
		moduleText = "Imaging and/or dealing with all the images from a slide";
	}
		
	
	public void processCurrentSlide(PositionList posList) {
		// call MM acquisition if mm is set
		
		// request new storage location from slide storage
//		long id = storage.create(this);
		String stor_dir = storage.get(this);
		
		// process children
		for ( WorkerSlide c_slide : child_slide ) {
			c_slide.processCurrentSlide(posList);
		}
		// should be in engine
		// eventually class should be instantiated and call function given to engine
		for ( WorkerImageCamera c_cam : child_camera ) {
			// c_cam.processImage(x, y, focus);
		}
	}
	
	
	@Override
	public
	void rmSuccessor(ModuleBase mod) {
		// TODO Auto-generated method stub

	}

	@Override
	public
	boolean compatibleSuccessor(ModuleBase mod) {
		
		if ( mod instanceof WorkerSlide ) {
			return true;
		}
		if ( mod instanceof WorkerImageCamera ) {
			return true;
		}
		
		return false;
		
	}

	@Override
	public void test() {
		// TODO Auto-generated method stub

	}

	public void setAcquisitionNew() {
		// TODO Auto-generated method stub
		
	}

	public void setAcquisitionRedo() {
		// TODO Auto-generated method stub
		
	}

	public void setNoAcquisition() {
		// TODO Auto-generated method stub
		
	}

	public void setMM(AcquisitionEngine acqEng, DeviceControlGUI gui) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void configure(HashMap<String, String> options) {
		// TODO Auto-generated method stub
		
	}


	public void start() {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void confSave() {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void confLoad() {
		// TODO Auto-generated method stub
		
	}

}
