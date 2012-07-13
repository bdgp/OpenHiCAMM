package org.bdgp.MMSlide;

import java.io.File;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.j256.ormlite.field.DatabaseFieldConfig;
import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.stmt.UpdateBuilder;
import com.j256.ormlite.stmt.Where;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.DatabaseTableConfig;
import com.j256.ormlite.table.TableInfo;
import com.j256.ormlite.table.TableUtils;
import com.j256.ormlite.dao.DaoManager;


/**
 * Class for handling all database-related and logging-related issues.
 * Contains mostly static utility functions and classes for making
 * ORMlite more pleasant to use.
 */
public class Session {
	protected static final String options = "fs=\\t";
	private static ConnectionSource connection;
	
	private Session() { }
	
	private static void init() throws SQLException {
	    if (connection == null) {
            connection = new JdbcConnectionSource("jdbc:hsqldb:mem:memdb","SA","");
	    }
	}
	
	/**
	 * Return a DAO instance for a CSV file.
	 * 
	 * @param class_ The class to wrap
	 * @param dir The directory to store the CSV file
	 * @param filename The file name of the CSV file
	 * @return A DAO instance
	 * @throws SQLException
	 */
	public static <L> Dao<L> dao(Class<L> class_, String filename) {
	    try {
    	    init();
    		File file = new File(filename);
    		
    		// get the table configuration
    		DatabaseTableConfig<L> tableConfig = DatabaseTableConfig.fromClass(
    		        connection, class_);
    		
    		if (tableConfig == null) {
        		// get the field configurations
        		List<DatabaseFieldConfig> fieldConfigs = new ArrayList<DatabaseFieldConfig>();
        		for (Field field : class_.getFields()) {
        		    DatabaseFieldConfig dfield = DatabaseFieldConfig.fromField(
        		            connection.getDatabaseType(), class_.getName(), field);
        		    if (dfield == null)
        		        dfield = new DatabaseFieldConfig(field.getName());
        		    fieldConfigs.add(dfield);
        		}
        		tableConfig = new DatabaseTableConfig<L>(class_, fieldConfigs);
    		}
    		
    		// set the table name to the file's path. 
    		// Hopefully the ORM supports quoted identifiers...
    		tableConfig.setTableName(file.getPath());
    		Dao<L> dao = DaoManager.createDao(connection, tableConfig);
    		
    		// create the table if it doesn't already exist
    		if (!dao.isTableExists()) {
    		    // call CREATE TEXT TABLE
        		List<String> create = TableUtils.getCreateTableStatements(connection, tableConfig);
        		for (String c: create) {
        		    dao.executeRaw(c.replaceFirst("^CREATE TABLE ","CREATE TEXT TABLE "));
        		}
        		// call SET TABLE
        		if (file.getPath().matches("[;\\]")) 
        		    throw new SQLException("Invalid characters in filename: "+file.getPath());
        		
        		// escape any double quotes
        		String tablename = file.getPath().replaceAll("\"","\"\"");
        		String source = tablename+';'+options.replaceAll("\"","\"\"");
        		dao.executeRaw("SET TABLE \""+tablename+"\" SOURCE \""+source+"\"");
    		}
    		return dao;
	    }
	    catch (SQLException e) { throw new RuntimeException(e); }
    }
	
	/**
	 * Function to ensure that a query returned only one result.
	 * @param list The list to check.
	 * @return The single value from the list to return.
	 */
	public static <T> T one(List<T> list) {
	    if (list.size() == 0) {
	        throw new RuntimeException("Query returned no rows!");
	    }
	    if (list.size() > 1) {
	        throw new RuntimeException("Query returned multiple rows!");
	    }
	    return list.get(0);
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
        public ChainHashMap<K,V> map(K k, V v) { this.put(k, v); return this; }
        public ChainHashMap<K,V> o(K k, V v) { this.put(k, v); return this; }
	}
    public static <K,V> ChainHashMap<K,V> map(K k, V v) {
	    return new ChainHashMap<K,V>(k, v);
	}
    public static ChainHashMap<String,Object> where(String k, Object v) {
        return new ChainHashMap<String,Object>(k, v);
    }
	
	/**
	 * Simplified Dao interface which doesn't have the ID generic parameter.
	 * @param <T>
	 */
	public static interface Dao<T> extends com.j256.ormlite.dao.Dao<T,Object> {}
	
	/**
	 * Update function which takes a dao, a map of columns to set,
	 * and a map of columns to query.
	 * @param dao The dao object to use
	 * @param set Which columns to set
	 * @param where Which columns to query
	 * @return the number of rows updated
	 */
    public static <T,ID> int update(
	        com.j256.ormlite.dao.Dao<T,ID> dao, 
	        Map<String,Object> set,
	        Map<String,Object> where) 
	{
	    try {
    	    UpdateBuilder<T,ID> update = dao.updateBuilder();
    	    
    	    // Update all columns, and store the fields in a map
    	    for (Map.Entry<String,Object> entry : set.entrySet()) {
        	    update.updateColumnValue(entry.getKey(), entry.getValue());
    	    }
    	    // Add the where clause
    	    Where<T,ID> whereClause = update.where();
    	    for (Map.Entry<String,Object> entry : where.entrySet()) {
    	        whereClause.eq(entry.getKey(), new SelectArg(entry.getValue()));
    	    }
    	    whereClause.and(where.size());
    	    return update.update();
	    } catch (SQLException e) {throw new RuntimeException(e);}
	}
	
    /**
     * Update a value object using a supplied list of lookup columns.
     * @param dao The dao object to use
     * @param value The value object to update
     * @param where ... The list of lookup columns to use
     * @return the number of rows updated
     */
	public static <T,ID> int update(
	        com.j256.ormlite.dao.Dao<T,ID> dao,
	        T value,
	        String ... where) 
	{
	    try {
	        Class<T> class_ = dao.getDataClass();
    	    TableInfo<T,ID> table = new TableInfo<T,ID>(connection, dao, class_);
    	    Map<String,Object> set = new HashMap<String,Object>();
    	    Map<String,Object> whereMap = new HashMap<String,Object>();
    	    Map<String,FieldType> fields = new HashMap<String,FieldType>();
    	    
    	    // fill the set map and fields map
    	    for (FieldType field : table.getFieldTypes()) {
    	        fields.put(field.getFieldName(), field);
    	        Object fieldValue = field.getFieldValueIfNotDefault(value);
                if (fieldValue != null) {
                    set.put(field.getColumnName(), fieldValue);
                }
    	    }
    	    // fill the where map
    	    for (String w : where) {
    	        if (!fields.containsKey(w)) {
    	            throw new SQLException("Class "+class_.getName()
    	                    +" does not contain field "+w);
    	        }
    	        whereMap.put(w, fields.get(w).extractJavaFieldValue(value));
    	    }
    	    // call the more generic update function
            return update(dao, set, whereMap);
	    } catch (SQLException e) {throw new RuntimeException(e);}
	}
	
	/**
	 * Perform a create or an update, depending on whether or not the row
	 * already exists in the database. Not atomic.
	 * @param dao
	 * @param value
	 * @param where
	 * @return
	 */
	public static <T,ID> com.j256.ormlite.dao.Dao.CreateOrUpdateStatus 
	    createOrUpdate(com.j256.ormlite.dao.Dao<T,ID> dao, T value, String ... where)
	{
	    try {
	        Class<T> class_ = dao.getDataClass();
	        TableInfo<T,ID> table = new TableInfo<T,ID>(connection, dao, class_);
    	    Map<String,FieldType> fields = new HashMap<String,FieldType>();
    	    for (FieldType field : table.getFieldTypes()) {
    	        fields.put(field.getFieldName(), field);
    	    }
    	    
    	    // Construct the query
    	    Map<String,Object> query = new HashMap<String,Object>();
    	    for (String w : where) {
    	        if (!fields.containsKey(w)) {
    	            throw new SQLException("Class "+class_.getName()
    	                    +" does not contain field "+w);
    	        }
    	        query.put(w, fields.get(w).extractJavaFieldValue(value));
    	    }
	        
    	    if (dao.queryForFieldValuesArgs(query).size() == 0) {
    	        int rows = dao.create(value);
    	        return new com.j256.ormlite.dao.Dao.CreateOrUpdateStatus(true, false, rows);
    	    }
    	    else {
    	        int rows = update(dao, value, where);
    	        return new com.j256.ormlite.dao.Dao.CreateOrUpdateStatus(false, true, rows);
    	    }
	    } catch (SQLException e) {throw new RuntimeException(e);}
	}
}

