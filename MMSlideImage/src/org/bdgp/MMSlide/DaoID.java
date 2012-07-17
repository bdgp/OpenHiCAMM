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
import com.j256.ormlite.dao.BaseDaoImpl;
import com.j256.ormlite.dao.DaoManager;

/**
 * Extend the ORMlite Dao to use improved update methods, and add a
 * static initializer that handles creating CSV files.
 * @param <T>
 * @param <ID>
 */
public class DaoID<T,ID> extends BaseDaoImpl<T,ID> {
	protected static final String options = "fs=\\t";
	private static ConnectionSource connection;
	
	protected DaoID(Class<T> class_) throws SQLException {
	    super(getdb(), class_);
	}
	protected DaoID(ConnectionSource connectionSource, Class<T> class_) throws SQLException { 
	    super(connectionSource, class_);
	}
	
	private static ConnectionSource getdb() throws SQLException {
	    if (connection == null) {
            connection = new JdbcConnectionSource("jdbc:hsqldb:mem:memdb","SA","");
	    }
	    return connection;
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
	public static <T,ID> DaoID<T,ID> getDao(Class<T> class_, String filename) {
	    try {
    	    getdb();
    		File file = new File(filename);
    		
    		// get the table configuration
    		DatabaseTableConfig<T> tableConfig = DatabaseTableConfig.fromClass(
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
        		tableConfig = new DatabaseTableConfig<T>(class_, fieldConfigs);
    		}
    		
    		// set the table name to the file's path. 
    		// Hopefully the ORM supports quoted identifiers...
    		tableConfig.setTableName(file.getAbsolutePath());
    		DaoID<T,ID> dao = DaoManager.createDao(connection, tableConfig);
    		
    		// create the table if it doesn't already exist
    		if (!dao.isTableExists()) {
    		    // make sure the parent directories exist
    		    File dir = file.getParentFile();
    		    if (dir != null && !(dir.exists() && dir.isDirectory())) {
            		if (!dir.mkdirs()) {
            		    throw new SQLException("Could not create directory "+dir.getPath());
            		}
    		    }
    		    // call CREATE TEXT TABLE
        		List<String> create = TableUtils.getCreateTableStatements(connection, tableConfig);
        		for (String c: create) {
        		    dao.executeRaw(c.replaceFirst("^CREATE TABLE ","CREATE CACHED TEXT TABLE "));
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
	 * Update function which takes a dao, a map of columns to set,
	 * and a map of columns to query.
	 * @param dao The dao object to use
	 * @param set Which columns to set
	 * @param where Which columns to query
	 * @return the number of rows updated
	 */
    public int update(
	        Map<String,Object> set,
	        Map<String,Object> where) 
	{
	    try {
    	    UpdateBuilder<T,ID> update = this.updateBuilder();
    	    
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
	public int update(
	        T value,
	        String ... where) 
	{
	    try {
	        Class<T> class_ = this.getDataClass();
    	    TableInfo<T,ID> table = new TableInfo<T,ID>(connection, this, class_);
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
            return this.update(set, whereMap);
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
	public Dao.CreateOrUpdateStatus 
	    createOrUpdate(T value, String ... where)
	{
	    try {
	        Class<T> class_ = this.getDataClass();
	        TableInfo<T,ID> table = new TableInfo<T,ID>(connection, this, class_);
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
	        
    	    if (this.queryForFieldValuesArgs(query).size() == 0) {
    	        int rows = this.create(value);
    	        return new Dao.CreateOrUpdateStatus(true, false, rows);
    	    }
    	    else {
    	        int rows = this.update(value, where);
    	        return new Dao.CreateOrUpdateStatus(false, true, rows);
    	    }
	    } catch (SQLException e) {throw new RuntimeException(e);}
	}
}

