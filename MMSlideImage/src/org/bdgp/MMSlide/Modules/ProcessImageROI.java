package org.bdgp.MMSlide.Modules;

import java.util.HashMap;

import org.bdgp.MMSlide.StorageManager;
import org.bdgp.MMSlide.Modules.Interfaces.WorkerImageCamera;

public class ProcessImageROI extends ModuleBase implements WorkerImageCamera {
	// Return x/y/len/width of bounding box surrounding the ROI

	public ProcessImageROI(StorageManager storage) {
		super(storage);
		moduleLabel = "Detect ROI";
		moduleText = "Finding the ROI from an image";
	}
	
	public void processImage(int x, int y, boolean focus) {
		// TODO Auto-generated method stub

	}

	@Override
	public
	void rmSuccessor(ModuleBase mod) {
		// TODO Auto-generated method stub

	}

	@Override
	public
	boolean compatibleSuccessor(ModuleBase mod) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void test() {
		// TODO Auto-generated method stub

	}

	@Override
	public void configure(HashMap<String, String> options) {
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
