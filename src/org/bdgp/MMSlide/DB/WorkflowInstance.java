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
    public String createStorageLocation(String toplevel) {
        // create a new directory for the task instance
        File path = new File(toplevel, this.getName());
        if (!path.exists() && !path.mkdirs()) {
            throw new RuntimeException("Could not create directory "+path.toString());
        }
        this.storageLocation = this.getName();
        return this.storageLocation;
    }
    public String getName() { 
    	return String.format("WF%05d",this.id); 
    }
    public String toString() {
    	return this.getName();
    }
};
