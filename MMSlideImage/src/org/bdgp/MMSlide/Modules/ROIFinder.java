package org.bdgp.MMSlide.Modules;

import java.util.Map;

import javax.swing.JPanel;

import org.bdgp.MMSlide.Configuration;
import org.bdgp.MMSlide.Logger;
import org.bdgp.MMSlide.WorkflowRunner;
import org.bdgp.MMSlide.DB.Config;
import org.bdgp.MMSlide.DB.Task;
import org.bdgp.MMSlide.DB.Task.Status;
import org.bdgp.MMSlide.Modules.Interfaces.Module;

/**
 * Return x/y/len/width of bounding box surrounding the ROI
 */
public class ROIFinder implements Module {
	public ROIFinder() { }
	
	public void processImage(int x, int y, boolean focus) {
		// TODO Auto-generated method stub
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
    public Status run(WorkflowRunner workflow, Task task, Map<String,Config> config, Logger logger) {
        return null;
    }

    @Override
    public JPanel configure(Configuration config) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void createTaskRecords(WorkflowRunner workflow, String moduleId) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public Map<String, Integer> getResources() {
        // TODO Auto-generated method stub
        return null;
    }
}
