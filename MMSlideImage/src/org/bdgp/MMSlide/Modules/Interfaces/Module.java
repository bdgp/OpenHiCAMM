package org.bdgp.MMSlide.Modules.Interfaces;

import java.util.Map;

import org.bdgp.MMSlide.Logger;
import org.bdgp.MMSlide.Task.Status;
import org.bdgp.MMSlide.Config;

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
     * Perform any relevant tests to ensure the module is properly
     * configured.
     * @return 
     */
    public boolean test();

    /**
     * Run the module configuration dialog and return the configuration.
     * @return
     */
    public Map<String,Config> configure();
    
    /**
     * Call a successor.
     */
    public Status callSuccessor(S successor, Map<String,Config> config, Logger logger);
    
    /**
     * Return the successor interface class object
     */
    public Class<S> getSuccessorInterface();
    
    /**
     * Return the title of this module.
     * @return
     */
    public String getTitle();
    
    /**
     * Return the module's description text.
     * @return
     */
    public String getDescription();
}
