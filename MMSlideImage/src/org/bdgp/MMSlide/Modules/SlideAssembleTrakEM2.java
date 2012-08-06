package org.bdgp.MMSlide.Modules;

import java.util.HashMap;
import java.util.Map;

import org.bdgp.MMSlide.Logger;
import org.bdgp.MMSlide.WorkflowRunner;
import org.bdgp.MMSlide.Dao.Config;
import org.bdgp.MMSlide.Dao.Task.Status;
import org.bdgp.MMSlide.Modules.Interfaces.Module;
import org.bdgp.MMSlide.Modules.Interfaces.WorkerImageCamera;

public class SlideAssembleTrakEM2 implements Module<WorkerImageCamera>, WorkerImageCamera {
	public SlideAssembleTrakEM2() {
	}

	public void processImage(int x, int y, boolean focus) {
		// TODO Auto-generated method stub

	}

    @Override
    public Map<String, Config> configure() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Status callSuccessor(WorkerImageCamera successor, int instance_id,
            Map<String, Config> config, Logger logger) 
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Class<WorkerImageCamera> getSuccessorInterface() {
        return WorkerImageCamera.class;
    }

    @Override
    public String getTitle() {
		return "TrackEM2 image assembly";
    }

    @Override
    public String getDescription() {
        return "Assemble images with TrackEM2";
    }

    @Override
    public boolean requiresDataAcquisitionMode() {
        return false;
    }

}
