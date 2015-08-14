package org.bdgp.OpenHiCAMM.DB;

import org.bdgp.OpenHiCAMM.MMAcquisitionCache;
import org.bdgp.OpenHiCAMM.Util;
import org.micromanager.acquisition.MMAcquisition;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Acquisition {
    @DatabaseField(generatedId=true, canBeNull=false) private int id;
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING) private String name;
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING) private String directory;
    
    public Acquisition() {}
    public Acquisition (String name, String directory) {
        this.name = name;
        this.directory = directory;
    }
    public int getId() {return this.id;}
    public String getName() {return this.name;}
    public String getDirectory() { return this.directory; }

    public MMAcquisition getAcquisition() {
    	return MMAcquisitionCache.getAcquisition(name, directory, false, true, true);
    }
    public MMAcquisition getAcquisition(boolean diskCached) {
    	return MMAcquisitionCache.getAcquisition(name, directory, false, diskCached, true); 
    }
    
    public String toString() {
    	return String.format("%s(id=%d, name=%s, directory=%s)",
    			this.getClass().getSimpleName(), this.id, Util.escape(this.name), Util.escape(this.directory));
    }
}
