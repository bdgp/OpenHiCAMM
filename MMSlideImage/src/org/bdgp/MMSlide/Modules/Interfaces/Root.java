package org.bdgp.MMSlide.Modules.Interfaces;

import java.util.Map;

import org.bdgp.MMSlide.WorkflowRunner.Config;
import org.bdgp.MMSlide.WorkflowRunner.Task.Status;

/**
 *  Module with no input dependencies - can be run from root if required.
 */
public interface Root {
    /**
     * Starts the root module.
     */
	public Status start(Map<String,Config> config);
}
