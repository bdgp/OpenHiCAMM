package org.bdgp.MMSlide.Modules.Interfaces;

import java.awt.Component;

import org.bdgp.MMSlide.ValidationError;
import org.bdgp.MMSlide.DB.Config;

public interface Configuration {
    /**
     * Return the list of configuration key-value pairs.
     */
    public Config[] retrieve();
    /**
     * Return a JPanel configuration UI with the given configurations set.
     */
    public Component display(Config[] configs);
    /**
     * Validate the configuration settings.
     * @return A list of validation errors, if any.
     */
    public ValidationError[] validate();
}
