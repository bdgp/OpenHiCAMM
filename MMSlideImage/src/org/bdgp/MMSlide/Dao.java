package org.bdgp.MMSlide;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Simplified Dao interface which doesn't have the ID generic parameter.
 * 
 * Class for handling all database-related issues.
 * Contains mostly static utility functions and classes for making
 * ORMlite more pleasant to use.
 * @param <T>
 */
public class Dao<T> extends DaoID<T,Object> {
    protected Dao(Class<T> class_) throws SQLException {
        super(class_);
    }
    
    public static final <T> Dao<T> get(Class<T> class_, String filename) {
        return (Dao<T>) DaoID.getDao(class_, filename);
    }
    /**
     * Class and static function for declaring map literals.
     * @param <K> The key type
     * @param <V>
     */
    @SuppressWarnings("serial")
    public static class ChainHashMap<K,V> extends HashMap<K,V> {
        public ChainHashMap() {
            super();
        }
        public ChainHashMap(K k, V v) {
            super();
            this.put(k, v);
        }
        public ChainHashMap<K,V> with(K k, V v) { this.put(k, v); return this; }
        public ChainHashMap<K,V> and(K k, V v) { this.put(k, v); return this; }
    }
    public static <K,V> ChainHashMap<K,V> map(K k, V v) {
        return new ChainHashMap<K,V>(k, v);
    }
    public static ChainHashMap<String,Object> set(String k, Object v) {
        return new ChainHashMap<String,Object>(k, v);
    }
    public static ChainHashMap<String,Object> where(String k, Object v) {
        return new ChainHashMap<String,Object>(k, v);
    } 
    
    /**
     * Simple static function for list literals.
     * @param values The list of things to turn into a list literal.
     * @return
     */
    public static <V> List<V> list(V ... values) {
        return Arrays.asList(values);
    }
    
    /**
     * Function to ensure that a query returned only one result.
     * @param list The list to check.
     * @return The single value from the list to return.
     */
    public static <T> T one(List<T> list, 
            String noneErrorMessage, 
            String multipleErrorMessage) 
    {
        if (noneErrorMessage == null)
            noneErrorMessage = "Query returned no rows!";
        if (multipleErrorMessage == null)
            multipleErrorMessage = "Query returned multiple rows!";
        
        if (list.size() == 0) {
            throw new RuntimeException(noneErrorMessage);
        }
        if (list.size() > 1) {
            throw new RuntimeException(multipleErrorMessage);
        }
        return list.get(0);
    }
    public static <T> T one(List<T> list) {
        return one(list, null, null);
    }
}
