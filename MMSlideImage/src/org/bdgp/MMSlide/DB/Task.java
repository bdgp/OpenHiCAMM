package org.bdgp.MMSlide.DB;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
 * Task metadata storage. For each task instance, store the
 * storage location path and the status.
 */
@DatabaseTable
public class Task {
    public Task(String moduleId, String storageLocation, Status status) {
       this.moduleId = moduleId;
       this.storageLocation = storageLocation;
       this.status = status;
    }
    
    @DatabaseField(generatedId=true,canBeNull=false)
    private int id;
    
    @DatabaseField(canBeNull=false)
    private String moduleId;
    
    @DatabaseField(canBeNull=false)
    private String storageLocation;
    
    public static enum Status {ERROR, FAIL, SUCCESS, IN_PROGRESS, DEFER, NEW};
    
    @DatabaseField(canBeNull=false)
    private Status status;
    
    public int getId() {
        return id;
    }
    public Status getStatus() {
        return status;
    }
    public String getStorageLocation() {
        return storageLocation;
    }
    public String getModuleId() {
        return moduleId;
    }
    public void setStatus(Status status) {
        this.status = status;
    }
};
