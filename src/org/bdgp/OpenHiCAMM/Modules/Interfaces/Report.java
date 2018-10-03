package org.bdgp.OpenHiCAMM.Modules.Interfaces;

import org.bdgp.OpenHiCAMM.WorkflowRunner;

import javafx.scene.web.WebEngine;

/**
 * Interface for building HTML reports.
 *
 */
public interface Report {
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
