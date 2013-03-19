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
public class WorkflowInstance {
    public WorkflowInstance() {}
    public WorkflowInstance(String directory) {
       this.directory = directory;
    }
    
    @DatabaseField(generatedId=true,canBeNull=false) private int id;
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING) private String directory;
    
    public int getId() { return id; }
    public String getStorageLocation() { return new File(this.directory, getName()).getPath(); }
    public String getName() { return String.format("WF%05d",this.id); }
};
