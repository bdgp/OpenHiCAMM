package org.bdgp.OpenHiCAMM.DB;

import org.bdgp.OpenHiCAMM.Util;

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
    public Task(Integer moduleId, Status status) {
       this.moduleId = moduleId;
       this.status = status;
    }
    public String toString() {
    	return String.format("%s(id=%d, dispatchUUID=%s, moduleId=%s, status=%s)", 
    			this.getName(), 
    			this.id, Util.escape(this.dispatchUUID), Util.escape(moduleId), status);
    }
    
    @DatabaseField(generatedId=true,canBeNull=false,index=true)
    private int id;

    // This field is used by the workflow runner task dispatching logic to determine
    // which parent task should dispatch a child task in cases where a child task 
    // has multiple parent tasks.
    @DatabaseField(canBeNull=true,dataType=DataType.LONG_STRING,index=true)
    private String dispatchUUID;
    
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING,index=true)
    private Integer moduleId;
    
    public static enum Status {ERROR, FAIL, SUCCESS, IN_PROGRESS, DEFER, NEW};
    
    @DatabaseField(canBeNull=false)
    private Status status;
    
    public int getId() {
        return id;
    }
    public String getDispatchUUID() { 
    	return this.dispatchUUID; 
    }
    public Status getStatus() {
        return status;
    }
    public Integer getModuleId() {
        return moduleId;
    }
    public void setStatus(Status status) {
        this.status = status;
    }
    public void setDispatchUUID(String dispatchUUID) {
        this.dispatchUUID = dispatchUUID;
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
        result = prime * result + ((status == null) ? 0 : status.hashCode());
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
        if (status != other.status)
            return false;
        return true;
    }
};
