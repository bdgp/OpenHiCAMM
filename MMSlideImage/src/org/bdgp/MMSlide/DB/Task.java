package org.bdgp.MMSlide.DB;

import java.io.File;

import org.bdgp.MMSlide.Util;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
 * Task metadata storage. For each task instance, store the
 * storage location path and the status.
 */
@DatabaseTable
public class Task {
    public Task() {}
    public Task(String moduleId, Status status) {
       this.moduleId = moduleId;
       this.status = status;
    }
    public Task(String moduleId, String storageLocation, Status status) {
       this.moduleId = moduleId;
       this.storageLocation = storageLocation;
       this.status = status;
    }
    public String toString() {
    	return String.format("%s(id=%d, parentTaskId=%d, moduleId=%s, storageLocation=%s, status=%s)", 
    			this.getName(), 
    			this.id, this.parentTaskId, Util.escape(moduleId), Util.escape(storageLocation), status);
    }
    
    @DatabaseField(generatedId=true,canBeNull=false)
    private int id;

    // This field is used by the workflow runner task dispatching logic to determine
    // which parent task should dispatch a child task in cases where a child task 
    // has multiple parent tasks. It is not the same as the TaskDispatch.parentTaskId, 
    // which describes the task DAG.
    @DatabaseField(canBeNull=true)
    private Integer parentTaskId;
    
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING)
    private String moduleId;
    
    @DatabaseField(canBeNull=true,dataType=DataType.LONG_STRING)
    private String storageLocation;
    
    public static enum Status {ERROR, FAIL, SUCCESS, IN_PROGRESS, DEFER, NEW};
    
    @DatabaseField(canBeNull=false)
    private Status status;
    
    public int getId() {
        return id;
    }
    public Integer getParentTaskId() { 
    	return this.parentTaskId; 
    }
    public Status getStatus() {
        return status;
    }
    public String getStorageLocation() {
        return storageLocation;
    }
    public String createStorageLocation(String parent, String toplevel) {
        // create a new directory for the task instance
    	File dir = parent != null? new File(parent, this.getName()) : new File(this.getName());
        File path = new File(toplevel, dir.getPath());
        if (!path.exists() && !path.mkdirs()) {
            throw new RuntimeException("Could not create directory "+path.toString());
        }
        this.storageLocation = dir.toString();
        return this.storageLocation;
    }
    public String getModuleId() {
        return moduleId;
    }
    public void setStatus(Status status) {
        this.status = status;
    }
    public String getName() { 
        return String.format("%s.T%05d",this.moduleId,this.id); 
    }
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id;
		result = prime * result
				+ ((moduleId == null) ? 0 : moduleId.hashCode());
		result = prime * result
				+ ((parentTaskId == null) ? 0 : parentTaskId.hashCode());
		result = prime * result + ((status == null) ? 0 : status.hashCode());
		result = prime * result
				+ ((storageLocation == null) ? 0 : storageLocation.hashCode());
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
		Task other = (Task) obj;
		if (id != other.id)
			return false;
		if (moduleId == null) {
			if (other.moduleId != null)
				return false;
		} else if (!moduleId.equals(other.moduleId))
			return false;
		if (parentTaskId == null) {
			if (other.parentTaskId != null)
				return false;
		} else if (!parentTaskId.equals(other.parentTaskId))
			return false;
		if (status != other.status)
			return false;
		if (storageLocation == null) {
			if (other.storageLocation != null)
				return false;
		} else if (!storageLocation.equals(other.storageLocation))
			return false;
		return true;
	}
};
