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

public class SlideImager implements Module {
	public SlideImager() { }
		
    @Override
    public String getTitle() {
        return "Slide imaging";
    }

    @Override
    public String getDescription() {
        return "Imaging and/or dealing with all the images from a slide";
    }

    @Override
    public Status run(WorkflowRunner workflow, Task task, Map<String,Config> config, Logger logger) {
        // TODO Auto-generated method stub
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
