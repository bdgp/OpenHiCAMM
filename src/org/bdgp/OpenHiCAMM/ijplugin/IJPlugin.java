package org.bdgp.OpenHiCAMM.ijplugin;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.bdgp.OpenHiCAMM.OpenHiCAMM;
import org.bdgp.OpenHiCAMM.Processes;
import org.micromanager.internal.MMStudio;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/***
 * The purpose of this ImageJ plugin is to automatically load micro-manager and the start the OpenHiCAMM workflow
 * in the event that one of the tasks times out due to a driver error. This should allow OpenHiCAMM to re-start the 
 * workflow in cases where buggy camera/loader/scope/stage drivers cause the workflow to stall out.
 * @author insitu
 *
 */

@Plugin(type = Command.class, menuPath = "Plugins>OpenHiCAMM")
public class IJPlugin implements Command {
	
    @Parameter
    private LogService log;

    @Parameter(label="processID", visibility=ItemVisibility.INVISIBLE, required=true, persist=false)
    private Integer processID;

    @Parameter(label="userProfile", visibility=ItemVisibility.INVISIBLE, required=true, persist=false)
    private String userProfile;

    @Parameter(label="configs", visibility=ItemVisibility.INVISIBLE, required=true, persist=false)
    private String configs;

    @Parameter(label="workflowDir", visibility=ItemVisibility.INVISIBLE, required=true, persist=false)
    private String workflowDir;
    
    @Parameter(label="startModule", visibility=ItemVisibility.INVISIBLE, required=true, persist=false)
    private String startModule;
    
    @Parameter(label="numThreads", visibility=ItemVisibility.INVISIBLE, required=true, persist=false)
    private Integer numThreads;
    
    
    @Override
    public void run() {
        log.info("OpenHiCAMM IJPlugin executed.");
        
        if (processID == null) {
        	SwingUtilities.invokeLater(()->{
                JOptionPane.showMessageDialog(null, "Please execute the OpenHiCAMM Micro-Manager plugin instead of this one.");
        	});
        	return;
        }
        
        log.info(String.format("Sending command to stop process %s", processID));
        Processes.stopProcess(processID);

        // Wait for parent process to terminate
        log.info("Wait for parent process to terminate");
        int retries = 0;
        while (Processes.isRunning(processID)) { 
        	try { Thread.sleep(1000); } catch (InterruptedException e) {throw new RuntimeException(e);} 
        	++retries;
        	if (retries % 60 == 0) {
                log.info(String.format("Sending command to kill process %s", processID));
        		Processes.killProcess(processID);
        	}
        }
        log.info("Parent process has terminated, continuing");
        
        // Start micro-manager with the selected user profile
        log.info(String.format("Initializing mmstudio using user profile %s", userProfile));
        MMStudio studio = new MMStudio(true, userProfile);
        
        // set group configuration programmatically
        for (String c : configs.split(",")) {
        	String[] kv = c.split(":", 2);
        	String group = kv[0];
        	String config = kv[1];
            log.info(String.format("Initializing config group %s with config %s", group, config));
        	try {
				studio.getCMMCore().setConfig(group, config);
				studio.getCMMCore().waitForConfig(group, config);
			} 
        	catch (Exception e) {throw new RuntimeException(e);}
        }
        studio.uiManager().frame().getConfigPad().refreshStructure(false);

        // Start the workflow dialog
        OpenHiCAMM openhicamm = new OpenHiCAMM(workflowDir, startModule, numThreads);
        openhicamm.onPluginSelected(true);
    }
}