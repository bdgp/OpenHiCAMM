package org.bdgp.OpenHiCAMM;

import java.io.File;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.j256.ormlite.field.DatabaseFieldConfig;
import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.stmt.UpdateBuilder;
import com.j256.ormlite.stmt.Where;
import com.j256.ormlite.table.DatabaseTableConfig;
import com.j256.ormlite.table.TableInfo;
import com.j256.ormlite.table.TableUtils;
import com.j256.ormlite.dao.BaseDaoImpl;

/**
 * Extend the ORMlite Dao to use improved update methods, and add a
 * static initializer that handles creating CSV files.
 * @param <T>
 * @param <ID>
 */
public class Dao<T> extends BaseDaoImpl<T,Object> {
	protected static final String options = 
	        "fs=\\t;textdb.allow_full_path=true;encoding=UTF-8;quoted=true;"+
            "cache_rows=10000;cache_size=2000;ignore_first=true";
	private Connection connection;
	
	protected Dao(Connection connection, Class<T> class_) throws SQLException { 
	    super(connection, class_);
	    this.connection = connection;
	}
	protected Dao(Connection connection, DatabaseTableConfig<T> tableConfig) throws SQLException { 
	    super(connection, tableConfig);
	    this.connection = connection;
	}
	
	public Connection getConnection() {
	    return connection;
	}
	
	/**
	 * Return a DAO instance for a database table.
	 * 
	 * @param class_ The class to wrap
	 * @param connection The database connection
	 * @param tablename The name of the table to access
	 * @return A DAO instance
	 * @throws SQLException
	 */
	public static synchronized <T> Dao<T> getTable(Class<T> class_, Connection connection, String tablename) 
	{
	    try {
    		// get the table configuration
    		DatabaseTableConfig<T> tableConfig = DatabaseTableConfig.fromClass(
    		        connection, class_);
    		
    		if (tableConfig == null) {
        		// get the field configurations
        		List<DatabaseFieldConfig> fieldConfigs = new ArrayList<DatabaseFieldConfig>();
        		for (Field field : class_.getFields()) {
        		    DatabaseFieldConfig dfield = DatabaseFieldConfig.fromField(
        		            connection.getDatabaseType(), class_.getName(), field);
        		    if (dfield == null) {
        		        dfield = new DatabaseFieldConfig(field.getName());
        		    }
        		    fieldConfigs.add(dfield);
        		}
        		tableConfig = new DatabaseTableConfig<T>(class_, fieldConfigs);
    		}
    		
    		tableConfig.setTableName(tablename);
    		Dao<T> dao = new Dao<T>(connection, tableConfig);
    		
    		// create the table if it doesn't already exist
    		if (!dao.isTableExists()) {
    		    // call CREATE TABLE
        		List<String> create = TableUtils.getCreateTableStatements(connection, tableConfig);
        		for (String c: create) {
        		    dao.executeRaw(c);
        		}
    		}
    		return dao;
	    }
	    catch (SQLException e) { throw new RuntimeException(e); }
    }
	
	/**
	 * Return a DAO instance for a database table.
	 * 
	 * @param class_ The class to wrap
	 * @param connection The database connection
	 * @param tablename The name of the table to access
	 * @return A DAO instance
	 * @throws SQLException
	 */
	public static synchronized <T> Dao<T> getTable(Class<T> class_, String dbPath, String tablename) 
	{
        return getTable(class_, Connection.get(dbPath), tablename);
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
	public static synchronized <T> Dao<T> getFile(Class<T> class_, Connection connection, String filename) 
	{
	    try {
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
        		    if (dfield == null) {
        		        dfield = new DatabaseFieldConfig(field.getName());
        		    }
        		    fieldConfigs.add(dfield);
        		}
        		tableConfig = new DatabaseTableConfig<T>(class_, fieldConfigs);
    		}
    		
    		// set the table name to the file's path. 
    		// Hopefully the ORM supports quoted identifiers...
    		tableConfig.setTableName(file.getAbsolutePath());
    		Dao<T> dao = new Dao<T>(connection, tableConfig);
    		
    		// create the table if it doesn't already exist
    		if (!dao.isTableExists()) {
    		    // make sure the parent directories exist
    		    File dir = file.getParentFile();
    		    if (dir != null && !(dir.exists() && dir.isDirectory())) {
    		        dir.mkdirs();
            		if (!(dir.exists() && dir.isDirectory())) {
            		    throw new SQLException("Could not create directory "+dir.getPath());
            		}
    		    }
    		    // call CREATE TEXT TABLE
        		List<String> create = TableUtils.getCreateTableStatements(connection, tableConfig);
        		for (String c: create) {
        		    dao.executeRaw(c.replaceFirst("^CREATE TABLE ","CREATE TEXT TABLE "));
        		}
        		
        		// call SET TABLE
        		if (file.getPath().matches("[;\\]")) {
        		    throw new SQLException("Invalid characters in filename: "+file.getPath());
        		}
        		String tablename = file.getPath().replaceAll("\"","\"\"");
        		String source = tablename+';'+options.replaceAll("\"","\"\"");
        		dao.executeRaw("SET TABLE \""+tablename+"\" SOURCE \""+source+"\"");
        		
        		// build the header string
        		StringBuilder headerBuilder = new StringBuilder();
        		List<DatabaseFieldConfig> fields = tableConfig.getFieldConfigs();
        		for (int i=0; i<fields.size(); ++i) {
        		    headerBuilder.append(fields.get(i).getColumnName());
        		    if (i != fields.size()-1) headerBuilder.append("\t");
        		}
        		String header = headerBuilder.toString().replaceAll("'","''");
        		dao.executeRaw("SET TABLE \""+tablename+"\" SOURCE HEADER '"+header+"'");
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
    	    UpdateBuilder<T,Object> update = this.updateBuilder();
    	    
    	    // Update all columns, and store the fields in a map
    	    for (Map.Entry<String,Object> entry : set.entrySet()) {
        	    update.updateColumnValue(entry.getKey(), entry.getValue());
    	    }
    	    // Add the where clause
    	    if (where.size()>0) {
                Where<T,Object> whereClause = update.where();
                for (Map.Entry<String,Object> entry : where.entrySet()) {
                    if (entry.getValue() == null) {
                        whereClause.isNull(entry.getKey());
                    }
                    else {
                        whereClause.eq(entry.getKey(), new SelectArg(entry.getValue()));
                    }
                }
                whereClause.and(where.size());
    	    }
    	    return update.update();
	    } catch (SQLException e) {throw new RuntimeException(e);}
	}
    
    public int update(Map<String,Object> set) {
    	return update(set, new HashMap<String,Object>());
    }
    
    @Override
    public int update(T value) {
        try { return super.update(value); } 
        catch (SQLException e) {throw new RuntimeException(e);}
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
    	    TableInfo<T,Object> table = new TableInfo<T,Object>(connection, this, class_);
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
	    insertOrUpdate(T value, String ... where)
	{
	    try {
	        Class<T> class_ = this.getDataClass();
	        TableInfo<T,Object> table = new TableInfo<T,Object>(connection, this, class_);
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
	
	/**
	 * Override queryForFieldValues and queryForFieldValuesArgs to handle nulls
	 */
	public List<T> queryForFieldValues(Map<String, Object> fieldValues) throws SQLException {
		return queryForFieldValues(fieldValues, false);
	}
	public List<T> queryForFieldValuesArgs(Map<String, Object> fieldValues) throws SQLException {
		return queryForFieldValues(fieldValues, true);
	}
	private List<T> queryForFieldValues(Map<String, Object> fieldValues, boolean useArgs) throws SQLException {
		if (fieldValues.size() == 0) {
			return Collections.emptyList();
		}
		checkForInitialized();
		QueryBuilder<T, Object> qb = queryBuilder();
		Where<T, Object> where = qb.where();
		for (Map.Entry<String, Object> entry : fieldValues.entrySet()) {
			Object fieldValue = entry.getValue();
			if (useArgs) {
				fieldValue = new SelectArg(fieldValue);
			}
			if (entry.getValue() == null) {
			    where.isNull(entry.getKey());
			}
			else {
    			where.eq(entry.getKey(), fieldValue);
			}
		}
        where.and(fieldValues.size());
        return qb.query();
	}
	
	/**
	 * Insert a value into the database table.
	 * @param value Object to insert
	 * @return The number of rows updated in the database. This should be 1.
	 */
	public int insert(T value) {
	    try {
            return create(value);
        } 
	    catch (SQLException e) {throw new RuntimeException(e);}
	}
	
	/**
	 * Given an object and an optional list of keys, returns an updated
	 * copy of the object from the database.
	 * @param value The object to refresh
	 * @param where Optional list of keys to use when refreshing
	 * @return A refreshed copy of the object
	 */
	public T reload(T value, String ... where) {
		Class<T> class_ = this.getDataClass();
		Map<String,Object> values = new HashMap<String,Object>();
		for (String w : where) {
			try {
				Field f = class_.getDeclaredField(w);
				f.setAccessible(true);
				values.put(w, f.get(value));
			} 
			catch (IllegalArgumentException e) {throw new RuntimeException(e);} 
			catch (IllegalAccessException e) {throw new RuntimeException(e);} 
			catch (NoSuchFieldException e) {throw new RuntimeException(e);} 
			catch (SecurityException e) {throw new RuntimeException(e);}
		}
		T updated = where.length == 0?
				this.selectOneOrDie(value) :
				this.selectOneOrDie(values);
		return updated;
	}
	
	/**
	 * Delete a value from the database table.
	 * @return The number of records deleted
	 */
	public int delete(T data) {
	    try {
    	    return super.delete(data);
	    }
        catch (SQLException e) {throw new RuntimeException(e);}
	}
	
	/**
	 * Delete all records in a table.
	 * @return The number of records deleted
	 */
	public int delete() {
	    try {
            return super.delete(deleteBuilder().prepare());
        } 
	    catch (SQLException e) {throw new RuntimeException(e);}
	}
	
	/**
	 * Delete any records in the table whose values match the where clause
	 * map.
	 * @param where The where clause map.
	 * @return The number of rows deleted.
	 */
	public int delete(Map<String,Object> where) {
	    try {
    	    DeleteBuilder<T,Object> delete = this.deleteBuilder();
    	    
    	    // Add the where clause
    	    if (where.size()>0) {
                Where<T,Object> whereClause = delete.where();
                for (Map.Entry<String,Object> entry : where.entrySet()) {
                    if (entry.getValue() == null) {
                        whereClause.isNull(entry.getKey());
                    }
                    else {
                        whereClause.eq(entry.getKey(), new SelectArg(entry.getValue()));
                    }
                }
                whereClause.and(where.size());
    	    }
    	    return delete.delete();
	    } catch (SQLException e) {throw new RuntimeException(e);}
	}
	
	/**
	 * Select values from the database table.
	 * @param fieldValues
	 * @return
	 */
	public List<T> select(Map<String,Object> fieldValues) {
	    try { return queryForFieldValuesArgs(fieldValues); }
        catch (SQLException e) {throw new RuntimeException(e);}
	}
	public List<T> select(T matchObj) {
	    try { return queryForMatchingArgs(matchObj); }
        catch (SQLException e) {throw new RuntimeException(e);}
	}
	public List<T> select() {
	    try { return queryForAll(); }
	    catch (SQLException e) {throw new RuntimeException(e);}
	}
	
	public T selectOne(Map<String,Object> fieldValues) {
	    return one(select(fieldValues));
	}
	public T selectOne(T matchObj) {
	    return one(select(matchObj));
	}
	public T selectOneOrDie(Map<String,Object> fieldValues) {
	    return oneOrDie(select(fieldValues));
	}
	public T selectOneOrDie(T matchObj) {
	    return oneOrDie(select(matchObj));
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
        if (list.size() == 0) {
        	if (noneErrorMessage != null) {
                throw new RuntimeException(noneErrorMessage);
        	}
        	return null;
        }
        if (list.size() > 1 && multipleErrorMessage != null) {
            throw new RuntimeException(multipleErrorMessage);
        }
        return list.get(0);
    }
    public static <T> T oneOrDie(List<T> list) {
        return one(list, "Query returned no rows!", "Query returned multiple rows!");
    }
    public static <T> T one(List<T> list) {
        return one(list, null, null);
    }
}

