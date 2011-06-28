package org.bdgp.MMSlide.Modules;

import org.micromanager.api.AcquisitionEngine;
import org.micromanager.api.DeviceControlGUI;

public interface MMModule {
	
	public void setNoAcquisition(); // No Acquisition
	
	public void setMM(AcquisitionEngine acqEng, DeviceControlGUI gui);
	
}
