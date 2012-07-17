package org.bdgp.MMSlide;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private String id;
    
    @DatabaseField(canBeNull=false,uniqueCombo=true)
    private String key;
    
    @DatabaseField
    private String value;
    
    @DatabaseField(canBeNull=false)
    private boolean required;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
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
    public static Map<String,Config> getMap(List<Config> ... configs) {
        Map<String,Config> map = new HashMap<String,Config>();
        for (int i=configs.length; i>=0; --i) {
            for (Config c : configs[i]) {
                map.put(c.getKey(), c);
            }
        }
        return map;
    }
}

