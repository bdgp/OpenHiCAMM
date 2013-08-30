package org.bdgp.MMSlide.Modules.Interfaces;

import java.util.List;

import javax.swing.JPanel;

import org.bdgp.MMSlide.DB.Config;

public interface Configuration {
    /**
     * Return the list of configuration key-value pairs.
     */
    public List<Config> retrieve();
    /**
     * Return a JPanel configuration UI with the given configurations set.
     */
    public JPanel display(List<Config> configs);
    /**
     * Validate the configuration settings.
     * @return A list of validation errors, if any.
     */
    public String[] validate();
}
