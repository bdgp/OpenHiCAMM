package org.bdgp.MMSlide.Modules;

import java.util.Map;

import org.bdgp.MMSlide.WorkflowRunner;
import org.bdgp.MMSlide.WorkflowRunner.Config;
import org.bdgp.MMSlide.WorkflowRunner.Task.Status;
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

    public Status callSuccessor(Root successor, Map<String,Config> config) {
        return successor.start(config);
    }

    public Class<Root> getSuccessorInterface() {
        return Root.class;
    }
}
