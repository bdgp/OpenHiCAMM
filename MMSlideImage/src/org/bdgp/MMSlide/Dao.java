package org.bdgp.MMSlide;

import java.sql.SQLException;

import com.j256.ormlite.table.DatabaseTableConfig;


/**
 * Simplified Dao interface which doesn't have the ID generic parameter.
 * @param <T>
 */
public class Dao<T> extends DaoID<T,Object> {
	protected Dao(Connection connection, Class<T> class_) throws SQLException { 
	    super(connection, class_);
	}
	protected Dao(Connection connection, DatabaseTableConfig<T> tableConfig) throws SQLException { 
	    super(connection, tableConfig);
	}
}
