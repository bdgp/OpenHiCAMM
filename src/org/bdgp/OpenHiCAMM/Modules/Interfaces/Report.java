package org.bdgp.OpenHiCAMM.Modules.Interfaces;

import org.bdgp.OpenHiCAMM.WorkflowRunner;

/**
 * Interface for building HTML reports.
 *
 */
public interface Report {
    /**
     * Initialize the report and set the workflow runner
     * @param workflowRunner
     */
    public void initialize(WorkflowRunner workflowRunner);

    /**
     * Runs a report and returns the report document
     * as a HTML string.
     */
    public void runReport(String reportDir, String reportIndex);
}
