package org.bdgp.MMSlide.Modules.Interfaces;

import java.util.Map;

import org.bdgp.MMSlide.WorkflowRunner.Task.Status;
import org.bdgp.MMSlide.WorkflowRunner.Config;

/**
 * Interface for workflow modules.
 * 
 * @param <S> The class of the successor interface. 
 * If no successor is possible, the Void class can be used.
 * @param <T>
 */
public interface Module<S> {
    /**
     * @return True or false depending on whether this module can be run
     * in command-line mode.
     */
    public boolean canRunInCommandLineMode();
    
    /**
     * Call a successor.
     */
    public Status callSuccessor(S successor, Map<String,Config> config);
    
    /**
     * Return the successor interface class object
     */
    public Class<S> getSuccessorInterface();
}
