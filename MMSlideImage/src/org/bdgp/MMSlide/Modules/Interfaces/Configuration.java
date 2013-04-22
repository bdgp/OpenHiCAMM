package org.bdgp.MMSlide.Modules.Interfaces;

import java.util.List;

import javax.swing.JPanel;

import org.bdgp.MMSlide.DB.Config;

public interface Configuration {
    public List<Config> retrieve();
    public JPanel display();
}
