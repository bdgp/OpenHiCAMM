package org.bdgp.MMSlide.Dao;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class TaskDispatch {
    @DatabaseField private int parentTaskId;
    @DatabaseField private int taskId;

    public TaskDispatch(int taskId, int parentTaskId) {
        this.taskId = taskId;
        this.parentTaskId = parentTaskId;
    }
    public int getParentTaskId() {
        return this.parentTaskId;
    }
    public int getTaskId() {
        return this.taskId;
    }
}
