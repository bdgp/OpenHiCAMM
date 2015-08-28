package org.bdgp.OpenHiCAMM.Modules.Interfaces;

import org.bdgp.OpenHiCAMM.DB.Task;

public interface TaskListener {
    public void notifyTask(Task task);
    public void taskCount(int taskCount);
    public void startTime(long startTime);
    public void stopped();
    public void debug(String message);
}
