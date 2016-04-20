package org.bdgp.OpenHiCAMM.DB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bdgp.OpenHiCAMM.Util;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
 * Class for storing configuration metadata. This is used for both
 * module and task metadata. Configuration is stored as key-value 
 * pairs.
 */
@DatabaseTable
public class Config {
    public Config() {}
    public Config(String id, String key, String value) {
        this.id = id;
        this.key = key;
        this.value = value;
    }
    public Config(String id, String key, String value, Object object) {
        this.id = id;
        this.key = key;
        this.value = value;
        this.object = object;
    }
    @DatabaseField(canBeNull=false,uniqueCombo=true,dataType=DataType.LONG_STRING, index=true)
    private String id;
    
    @DatabaseField(canBeNull=false,uniqueCombo=true,dataType=DataType.LONG_STRING, index=true)
    private String key;
    
    @DatabaseField(canBeNull=true,dataType=DataType.LONG_STRING, index=true)
    private String value;
    
    // The object field can store any object as part of the configuration, but 
    // won't be serialized to the DB. This is useful for passing Java objects from
    // parent tasks to child tasks.
    private Object object;
    
    public String getId() {
        return id;
    }
    public String getKey() {
        return key;
    }
    public String getValue() {
        return value;
    }
    public Object getObject() {
    	return object;
    }

    /**
     * Lookup all the configuration data for id in configs, going from right to left.
     * If the same config is stored in multiple config sources, the config specified
     * first gets represented.
     * 
     * @param id The id to look up configs for
     * @param configs A list of configuration sources to search
     * @return A map of all configurations for the id
     */
    public static Map<String,Config> merge(List<Config> configs) {
        Map<String,Config> map = new HashMap<String,Config>();
        for (Config c : configs) {
            map.put(c.getKey(), c);
        }
        return map;
    }
    
    public String toString() {
    	return String.format("%s(id=%s, %s=%s%s)",
    			this.getClass().getSimpleName(), 
    			Util.escape(this.id), 
    			Util.escape(this.key), 
    			Util.escape(this.value), 
    			this.object != null? String.format(", object=%s", Util.escape(this.object)) : "");
    }
}

