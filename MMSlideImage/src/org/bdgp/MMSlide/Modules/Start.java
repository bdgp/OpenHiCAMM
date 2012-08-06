package org.bdgp.MMSlide.Modules;

import java.util.Map;

import org.bdgp.MMSlide.Logger;
import org.bdgp.MMSlide.WorkflowRunner;
import org.bdgp.MMSlide.Dao.Config;
import org.bdgp.MMSlide.Dao.Task.Status;
import org.bdgp.MMSlide.Modules.Interfaces.Module;
import org.bdgp.MMSlide.Modules.Interfaces.Root;

/**
 * Top-level workflow module for all workflows.
 *
 */
public class Start implements Module<Root> {
    public Status callSuccessor(Root successor, int instance_id, Map<String,Config> config, Logger logger) {
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
    public Map<String, Config> configure() {
        return null;
    }

    @Override
    public boolean requiresDataAcquisitionMode() {
        return false;
    }
}
