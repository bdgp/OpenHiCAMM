package org.bdgp.MMSlide.Modules;

import java.util.Map;

import org.bdgp.MMSlide.Logger;
import org.bdgp.MMSlide.WorkflowRunner;
import org.bdgp.MMSlide.Dao.Config;
import org.bdgp.MMSlide.Dao.Task.Status;
import org.bdgp.MMSlide.Modules.Interfaces.Module;
import org.bdgp.MMSlide.Modules.Interfaces.WorkerImageCamera;

/**
 * Return x/y/len/width of bounding box surrounding the ROI
 */
public class ProcessImageROI implements Module<Void>, WorkerImageCamera {
	public ProcessImageROI() { }
	
	public void processImage(int x, int y, boolean focus) {
		// TODO Auto-generated method stub
	}

	@Override
	public Map<String,Config> configure() {
        return null;
	}

    @Override
    public Status callSuccessor(Void successor, int instance_id,
            Map<String,Config> config, Logger logger) 
    {
        return null;
    }

    @Override
    public Class<Void> getSuccessorInterface() {
        return null;
    }

    @Override
    public String getTitle() {
        return "Detect ROI";
    }

    @Override
    public String getDescription() {
        return "Finding the ROI from an image";
    }

    @Override
    public boolean requiresDataAcquisitionMode() {
        return false;
    }
}
