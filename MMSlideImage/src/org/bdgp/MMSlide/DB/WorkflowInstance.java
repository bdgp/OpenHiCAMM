package org.bdgp.MMSlide.DB;

import java.io.File;

import org.bdgp.MMSlide.Dao;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class WorkflowInstance {
    public WorkflowInstance() {}
    public WorkflowInstance(String directory) {
       this.directory = directory;
    }
    
    private String directory;
    
    @DatabaseField(generatedId=true,canBeNull=false) 
    private int id;
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING) 
    private String storageLocation;
    
    public int getId() { return id; }
    public String getStorageLocation() { return this.storageLocation; }
    public String getName() { return String.format("WF%05d",this.id); }
    
    public void update(Dao<WorkflowInstance> wf) {
        if (this.storageLocation == null && this.directory != null && this.id != 0) {
            this.storageLocation = new File(directory, this.getName()).getPath();
            wf.update(this);
        }
    }
};
