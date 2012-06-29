package org.bdgp.MMSlide.Modules;

import java.util.HashMap;

import org.bdgp.MMSlide.StorageManager;

public class SlideAssembleTrakEM2 extends ModuleBase implements WorkerImageCamera {

	public SlideAssembleTrakEM2(StorageManager storage) {
		super(storage);
		moduleLabel = "TrackEM2 image assembly";
		moduleText = "Assemble images with TrackEM2";
	}

	public void processImage(int x, int y, boolean focus) {
		// TODO Auto-generated method stub

	}

	@Override
	public
	void addSuccessor(ModuleBase mod) {
		// TODO Auto-generated method stub

	}

	@Override
	public void rmSuccessor(ModuleBase mod) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean compatibleSuccessor(ModuleBase mod) {
		if ( mod instanceof WorkerImageCamera ) {
			return true;
		}
		return false;
	}

	@Override
	public void configure(HashMap<String, String> options) {
		// TODO Auto-generated method stub
	}

	@Override
	public void test() {
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
