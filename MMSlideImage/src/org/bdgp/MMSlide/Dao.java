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
