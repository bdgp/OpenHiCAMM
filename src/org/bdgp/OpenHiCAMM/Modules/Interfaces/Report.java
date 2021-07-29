package org.bdgp.OpenHiCAMM.Modules.Interfaces;

import org.bdgp.OpenHiCAMM.WorkflowRunner;
import org.micromanager.MMPlugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Interface for building HTML reports.
 *
 */
public interface Report extends MMPlugin, SciJavaPlugin {
    /**
     * Initialize the report and set the workflow runner
     * @param workflowRunner
     */
    public void initialize(WorkflowRunner workflowRunner, String reportDir, String reportIndex);

    /**
     * Runs a report and returns the report document
     * as a HTML string.
     */
    public void runReport();
}
