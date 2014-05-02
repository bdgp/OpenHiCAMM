package org.bdgp.MMSlide.DB;

import java.io.File;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class WorkflowInstance {
    public WorkflowInstance() {}
    public WorkflowInstance(String storageLocation) {
       this.storageLocation = storageLocation;
    }
    
    @DatabaseField(generatedId=true,canBeNull=false) 
    private int id;
    @DatabaseField(canBeNull=true,dataType=DataType.LONG_STRING) 
    private String storageLocation;
    
    public int getId() { return id; }
    public String getStorageLocation() { return this.storageLocation; }
    public String createStorageLocation(String parent) {
        // create a new directory for the task instance
        File dir = new File(parent, this.getName());
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("Could not create directory "+dir.toString());
        }
        this.storageLocation = dir.toString();
        return this.storageLocation;
    }
    public String getName() { return String.format("WF%05d",this.id); }
};
