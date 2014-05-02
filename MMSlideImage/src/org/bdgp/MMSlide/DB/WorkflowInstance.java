package org.bdgp.MMSlide.DB;

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
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING) 
    private String storageLocation;
    
    public int getId() { return id; }
    public String getStorageLocation() { return this.storageLocation; }
    public String getName() { return String.format("WF%05d",this.id); }
};
