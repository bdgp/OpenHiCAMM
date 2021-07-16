package org.bdgp.OpenHiCAMM.mmplugin;

import org.bdgp.OpenHiCAMM.OpenHiCAMM;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class MMPlugin implements MenuPlugin, SciJavaPlugin {
	OpenHiCAMM openhicamm;
	
	public MMPlugin() {
		if (this.openhicamm == null) {
			this.openhicamm = new OpenHiCAMM();
		}
	}

	@Override
	public void setContext(Studio studio) {
		this.openhicamm.setContext(studio);
	}

	@Override
	public String getName() {
		return this.openhicamm.getName();
	}

	@Override
	public String getHelpText() {
		return this.openhicamm.getHelpText();
	}

	@Override
	public String getVersion() {
		return this.openhicamm.getVersion();
	}

	@Override
	public String getCopyright() {
		return this.openhicamm.getCopyright();
	}

	@Override
	public String getSubMenu() {
		return this.openhicamm.getSubMenu();
	}

	@Override
	public void onPluginSelected() {
		this.openhicamm.onPluginSelected();
	}

}
