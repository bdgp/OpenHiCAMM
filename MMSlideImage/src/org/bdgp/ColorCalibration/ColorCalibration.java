package org.bdgp.ColorCalibration;

import mmcorej.CMMCore;

import org.micromanager.metadata.AcquisitionData;
import org.micromanager.utils.MMException;
import org.micromanager.utils.PropertyItem;

public interface ColorCalibration {
	
	public void automatic();	
	public void dialog();
	
	public void set(ColorSettings setting);
	public ColorSettings get();

	public String getDeviceName();
	public void applySettings();
	public void saveSettings();
	public void setMMCore(CMMCore core);
}
