package org.bdgp.OpenHiCAMM.DB;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bdgp.OpenHiCAMM.Util;
import org.micromanager.internal.MMStudio;
import org.micromanager.acquisition.internal.MMAcquisition;
import org.micromanager.data.Datastore;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Acquisition {
    @DatabaseField(generatedId=true, canBeNull=false,allowGeneratedIdInsert=true) private int id;
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING) private String name;
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING) private String prefix;
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING) private String directory;
    
    private static Map<String,MMAcquisition> acquisitionCache = new ConcurrentHashMap<>();
    
    public static void clearCache() {
        acquisitionCache.clear();
    }
    
    public Acquisition() {}
    public Acquisition (String name, String prefix, String directory) {
        this.name = name;
        this.prefix = prefix;
        this.directory = directory;
    }
    public int getId() {return this.id;}
    public String getName() {return this.name;}
    public String getPrefix() {return this.prefix;}
    public String getDirectory() { return this.directory; }

    public Datastore getDatastore() {
    	return this.getDatastore(true);
    }

    public Datastore getDatastore(boolean isVirtual) {
    	try {
            return MMStudio.getInstance().getDataManager().loadData(Paths.get(this.directory, this.prefix).toString(), isVirtual); 
        } 
    	catch (IOException e) { throw new RuntimeException(e); }
    }
    
    public String toString() {
    	return String.format("%s(id=%d, name=%s, prefix=%s, directory=%s)",
    			this.getClass().getSimpleName(), 
    			this.id, 
    			Util.escape(this.name), 
    			Util.escape(this.prefix), 
    			Util.escape(this.directory));
    }
}
