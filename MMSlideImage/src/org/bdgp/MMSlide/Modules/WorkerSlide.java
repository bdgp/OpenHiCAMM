package org.bdgp.MMSlide.Modules;

import org.micromanager.navigation.PositionList;

public interface WorkerSlide extends WorkerBase {
	public void processCurrentSlide(PositionList posList);
}
