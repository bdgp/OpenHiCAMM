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
    
    @DatabaseField(generatedId=true,canBeNull=false)
    private int id;
    
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
    public Status getStatus() {
        return status;
    }
    public String getStorageLocation() {
        return storageLocation;
    }
    public String createStorageLocation(String parent) {
        // create a new directory for the task instance
        File dir = new File(parent, this.getName());
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("Could not create directory "+dir.toString());
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
