package org.bdgp.MMSlide.DB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
 * Class for storing configuration metadata. This is used for both
 * module and task metadata. Configuration is stored as key-value 
 * pairs.
 */
@DatabaseTable
public class Config {
    @DatabaseField(canBeNull=false,uniqueCombo=true)
    private int id;
    
    @DatabaseField(canBeNull=false,uniqueCombo=true)
    private String key;
    
    @DatabaseField
    private String value;
    
    @DatabaseField(canBeNull=false)
    private boolean required;

    public int getId() {
        return id;
    }
    public String getKey() {
        return key;
    }
    public String getValue() {
        return value;
    }
    public boolean isRequired() {
        return required;
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
}

