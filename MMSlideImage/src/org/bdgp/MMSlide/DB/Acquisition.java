package org.bdgp.MMSlide.DB;

import org.bdgp.MMSlide.Util;
import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.utils.MMScriptException;

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
    	try { return new MMAcquisition(name, directory, false, false, true); } 
    	catch (MMScriptException e) {throw new RuntimeException(e);}
    }
    public MMAcquisition getAcquisition(boolean diskCached) {
    	try { return new MMAcquisition(name, directory, false, diskCached, true); } 
    	catch (MMScriptException e) {throw new RuntimeException(e);}
    }
    
    public String toString() {
    	return String.format("%s(id=%d, name=%s, directory=%s)",
    			this.getClass().getSimpleName(), this.id, Util.escape(this.name), Util.escape(this.directory));
    }
}
