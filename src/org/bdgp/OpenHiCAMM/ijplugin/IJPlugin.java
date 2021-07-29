package org.bdgp.OpenHiCAMM.ijplugin;

import java.lang.management.ManagementFactory;
import java.util.Map;

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
        
        for (String s : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            log.info(s);
        }

        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            log.info(String.format("%s: %s", entry.getKey(), entry.getValue()));
        }

        for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
            log.info(String.format("%s: %s", entry.getKey(), entry.getValue()));
        }
        // System.getProperty("fiji.executable")
        // System.getProperty('ij.executable")
    }
}