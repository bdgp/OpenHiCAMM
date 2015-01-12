package org.bdgp.MMSlide.DB;

import java.io.File;

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
    	return String.format("Task(id=%s, parentTaskId=%s, moduleId=%s, storageLocation=%s, status=%s)", 
    			id, parentTaskId, moduleId, storageLocation, status);
    }
    
    @DatabaseField(generatedId=true,canBeNull=false)
    private int id;

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
};
