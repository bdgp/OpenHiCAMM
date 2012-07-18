package org.bdgp.MMSlide.Modules;

import java.util.Map;

import org.bdgp.MMSlide.Logger;
import org.bdgp.MMSlide.WorkflowRunner;
import org.bdgp.MMSlide.Config;
import org.bdgp.MMSlide.Task.Status;
import org.bdgp.MMSlide.Modules.Interfaces.Module;
import org.bdgp.MMSlide.Modules.Interfaces.Root;

/**
 * Top-level workflow module for all workflows.
 *
 */
public class Start implements Module<Root> {
    static { WorkflowRunner.addModule(Start.class); }
    
    public boolean canRunInCommandLineMode() {
        return true;
    }

    public Status callSuccessor(Root successor, Map<String,Config> config, Logger logger) {
        return successor.start(config);
    }

    public Class<Root> getSuccessorInterface() {
        return Root.class;
    }

    @Override
    public String getTitle() {
        return "Start of workflow.";
    }

    @Override
    public String getDescription() {
        return "Start of workflow.";
    }

    @Override
    public boolean test() {
        return false;
    }

    @Override
    public Map<String, Config> configure() {
        return null;
    }
}
