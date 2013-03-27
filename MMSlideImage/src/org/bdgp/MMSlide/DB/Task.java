package org.bdgp.MMSlide.DB;

import java.io.File;

import org.bdgp.MMSlide.Dao;

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
    public Task(String moduleId, String parentStorageLocation, Status status) {
       this.moduleId = moduleId;
       this.parentStorageLocation = parentStorageLocation;
       this.status = status;
    }
    
    private String parentStorageLocation;
    
    @DatabaseField(generatedId=true,canBeNull=false)
    private int id;
    
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING)
    private String moduleId;
    
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING)
    private String storageLocation;
    
    public static enum Status {ERROR, FAIL, SUCCESS, IN_PROGRESS, DEFER, NEW};
    
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING)
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
    public String getName() { 
        return String.format("%s.T%05d",this.moduleId,this.id); 
    }
    public void update(Dao<Task> t) {
        if (this.parentStorageLocation != null && this.storageLocation == null && this.id != 0) {
            this.storageLocation = new File(this.parentStorageLocation, this.getName()).getPath();
            t.update(this);
        }
    }
};
