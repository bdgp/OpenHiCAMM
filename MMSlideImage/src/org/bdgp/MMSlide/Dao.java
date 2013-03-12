package org.bdgp.MMSlide;

import java.sql.SQLException;


/**
 * Simplified Dao interface which doesn't have the ID generic parameter.
 * @param <T>
 */
public class Dao<T> extends DaoID<T,Object> {
    protected Dao(Class<T> class_) throws SQLException {
        super(class_);
    }
}
