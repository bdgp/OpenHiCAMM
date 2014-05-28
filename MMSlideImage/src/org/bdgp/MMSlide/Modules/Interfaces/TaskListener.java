package org.bdgp.MMSlide.Modules.Interfaces;

import org.bdgp.MMSlide.DB.Task;

public interface TaskListener {
    public void notifyTask(Task task);
    public void stopped();
    public void killed();
}
