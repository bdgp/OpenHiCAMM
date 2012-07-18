package org.bdgp.MMSlide.Modules;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.bdgp.MMSlide.Config;
import org.bdgp.MMSlide.Logger;
import org.bdgp.MMSlide.Task.Status;
import org.bdgp.MMSlide.Modules.Interfaces.Module;
import org.bdgp.MMSlide.Modules.Interfaces.WorkerImageCamera;
import org.bdgp.MMSlide.Modules.Interfaces.Root;
import org.bdgp.MMSlide.Modules.Interfaces.WorkerSlide;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.PositionListDlg;
import org.micromanager.api.AcquisitionEngine;
import org.micromanager.api.DeviceControlGUI;
import org.micromanager.navigation.PositionList;

public class Slide implements Module<WorkerSlide>, Root, WorkerSlide {

	private Vector<WorkerSlide> child_slide;
	private Vector<WorkerImageCamera> child_camera;
	
	private MMStudioMainFrame gui;
    private PositionList posList_ = null;
    private PositionListDlg posListDlg_;

	public Slide() {
	}
		
	
	public void processCurrentSlide(PositionList posList) {
		// call MM acquisition if mm is set
		
		// request new storage location from slide storage
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
	public boolean test() {
	    return false;
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
    public Status start(Map<String, Config> config) {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public boolean canRunInCommandLineMode() {
        // TODO Auto-generated method stub
        return false;
    }


    @Override
    public Map configure() {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public Status callSuccessor(WorkerSlide successor, Map<String,Config> config, Logger logger) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Class getSuccessorInterface() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getTitle() {
        return "Slide imaging";
    }

    @Override
    public String getDescription() {
        return "Imaging and/or dealing with all the images from a slide";
    }
}
