package org.bdgp.OpenHiCAMM.ijplugin;

import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ij.IJ;

@Plugin(type = Command.class, menuPath = "Plugins>OpenHiCAMM")
public class IJPlugin implements Command {
	
    @Parameter
    private LogService log;

    @Override
    public void run() {
        log.info("OpenHiCAMM IJPlugin executed.");
    }
}