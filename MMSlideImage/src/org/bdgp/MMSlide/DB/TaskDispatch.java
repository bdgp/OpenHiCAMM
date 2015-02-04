package org.bdgp.MMSlide.DB;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class TaskDispatch {
    @DatabaseField(canBeNull=false, uniqueCombo=true) private int parentTaskId;
	@DatabaseField(canBeNull=false, uniqueCombo=true) private int taskId;

    public TaskDispatch() {}
    public TaskDispatch(int taskId, int parentTaskId) {
        this.taskId = taskId;
        this.parentTaskId = parentTaskId;
    }
    public int getParentTaskId() { return this.parentTaskId; }
    public int getTaskId() { return this.taskId; }
    public String toString() {
    	return String.format("%s(parentTaskId=%d, taskId=%d)",
    			this.getClass().getSimpleName(),
    			this.parentTaskId, this.taskId);
    }
    @Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + parentTaskId;
		result = prime * result + taskId;
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TaskDispatch other = (TaskDispatch) obj;
		if (parentTaskId != other.parentTaskId)
			return false;
		if (taskId != other.taskId)
			return false;
		return true;
	}
}
