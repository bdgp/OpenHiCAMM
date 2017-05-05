package org.bdgp.OpenHiCAMM;

import java.io.File;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.j256.ormlite.field.DatabaseFieldConfig;
import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.stmt.UpdateBuilder;
import com.j256.ormlite.stmt.Where;
import com.j256.ormlite.support.DatabaseConnection;
import com.j256.ormlite.table.DatabaseTableConfig;
import com.j256.ormlite.table.TableUtils;

import ij.IJ;

import com.j256.ormlite.dao.BaseDaoImpl;
import com.j256.ormlite.db.DatabaseType;

/**
 * Extend the ORMlite Dao to use improved update methods, and add a
 * static initializer that handles creating CSV files.
 * @param <T>
 * @param <ID>
 */
public class Dao<T> extends BaseDaoImpl<T,Object> {
    private static final boolean DEBUG = false;

	private Connection connection;
	private Map<String,FieldType> fields;
	
	protected Dao(Connection connection, Class<T> class_) throws SQLException { 
	    super(connection, class_);
	    this.connection = connection;

        this.fields = new HashMap<>();
        for (FieldType field : this.tableInfo.getFieldTypes()) {
            this.fields.put(field.getFieldName(), field);
        }
	}
	protected Dao(Connection connection, DatabaseTableConfig<T> tableConfig) throws SQLException { 
	    super(connection, tableConfig);
	    this.connection = connection;

        this.fields = new HashMap<>();
        for (FieldType field : this.tableInfo.getFieldTypes()) {
            this.fields.put(field.getFieldName(), field);
        }
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
    		tableConfig.setTableName(tablename);

            // make sure all the column names are uppercased
            if (connection.getDatabaseType().isEntityNamesMustBeUpCase()) {
                FieldType[] fieldTypes = tableConfig.getFieldTypes(connection.getDatabaseType());
                for (int i=0; i<fieldTypes.length; ++i) {
                    FieldType ft = fieldTypes[i];
                    DatabaseFieldConfig fieldConfig = DatabaseFieldConfig.fromField(
                            connection.getDatabaseType(), 
                            ft.getTableName(), 
                            ft.getField());
                    if (fieldConfig != null) {
                        fieldConfig.setColumnName(ft.getColumnName().toUpperCase());
                        fieldTypes[i] = new FieldType(connection, ft.getTableName(), ft.getField(), fieldConfig, class_);
                    }
                }
            }

    		Dao<T> dao = new Dao<T>(connection, tableConfig);
    		
    		// create the table if it doesn't already exist
    		if (!dao.isTableExists()) {
    		    // call CREATE TABLE
        		List<String> create = TableUtils.getCreateTableStatements(connection, tableConfig);
        		for (String c: create) {
        		    // store all tables as cached tables
        		    c = c.replaceFirst("^CREATE TABLE ","CREATE CACHED TABLE ");
        		    dao.executeRaw(c);
        		}
    		}
    		return dao;
	    }
	    catch (SQLException e) { throw new RuntimeException(e); }
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
	public static synchronized <T> Dao<T> getFile(Class<T> class_, Connection connection, String tablename, String filename) 
	{
	    try {
    		File file = new File(filename);
    		
    		// get the table configuration
    		DatabaseTableConfig<T> tableConfig = DatabaseTableConfig.fromClass(
    		        connection, class_);
    		tableConfig.setTableName(tablename);

            // make sure all the column names are uppercased
            if (connection.getDatabaseType().isEntityNamesMustBeUpCase()) {
                FieldType[] fieldTypes = tableConfig.getFieldTypes(connection.getDatabaseType());
                for (int i=0; i<fieldTypes.length; ++i) {
                    FieldType ft = fieldTypes[i];
                    DatabaseFieldConfig fieldConfig = DatabaseFieldConfig.fromField(
                            connection.getDatabaseType(), 
                            ft.getTableName(), 
                            ft.getField());
                    if (fieldConfig != null) {
                        fieldConfig.setColumnName(ft.getColumnName().toUpperCase());
                        fieldTypes[i] = new FieldType(connection, ft.getTableName(), ft.getField(), fieldConfig, class_);
                    }
                }
            }

    		Dao<T> dao = new Dao<T>(connection, tableConfig);
    		
    		// create the table if it doesn't already exist
    		if (!dao.isTableExists()) {
    		    // make sure the parent directories exist
    		    File dir = file.getParentFile();
    		    if (dir != null && !(dir.exists() && dir.isDirectory())) {
            		if (!dir.mkdirs() || !(dir.exists() && dir.isDirectory())) {
            		    throw new SQLException("Could not create directory "+dir.getPath());
            		}
    		    }
    		    // call CREATE TEXT TABLE
        		List<String> create = TableUtils.getCreateTableStatements(connection, tableConfig);
        		for (String c: create) {
        		    dao.executeRaw(c.replaceFirst("^CREATE TABLE ","CREATE TEXT TABLE "));
        		}
        		
        		// call SET TABLE
        		if (file.getPath().matches("[;\\\\]")) {
        		    throw new SQLException("Invalid characters in filename: "+file.getPath());
        		}
        		String sourceName = file.getPath().replaceAll("\"","\"\"");

                final String options = "fs=\\t;encoding=UTF-8;quoted=true;cache_rows=10000;cache_size=2000;ignore_first=true";
        		String source = sourceName+';'+options;
        		dao.executeRaw("SET TABLE \""+tablename+"\" SOURCE \""+source+"\"");
        		
        		// build the header string
        		StringBuilder headerBuilder = new StringBuilder();
                FieldType[] fieldTypes = tableConfig.getFieldTypes(connection.getDatabaseType());
                for (int i=0; i<fieldTypes.length; ++i) {
                    FieldType ft = fieldTypes[i];
                    DatabaseFieldConfig fieldConfig = DatabaseFieldConfig.fromField(
                            connection.getDatabaseType(), 
                            ft.getTableName(), 
                            ft.getField());
                    if (fieldConfig != null) {
                        headerBuilder.append(fieldConfig.getFieldName());
                        headerBuilder.append("\t");
                    }
                }
        		String header = headerBuilder.toString().replaceAll("\t$","").replaceAll("'","''");
        		dao.executeRaw("SET TABLE \""+tablename+"\" SOURCE HEADER '"+header+"'");
        		
        		// update any generated sequence ID values
        		dao.updateSequence();
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
	
	@Override
	public boolean isTableExists() {
	    return this.connection.isTableExists(this.getTableInfo().getTableName());
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
    	        if (!this.fields.containsKey(entry.getKey())) throw new RuntimeException(
    	                String.format("Update set: Class %s has no field named %s!",
    	                        this.getDataClass(), entry.getKey()));
        	    update.updateColumnValue(
        	            this.fields.get(entry.getKey()).getColumnName(), 
        	            entry.getValue());
    	    }
    	    // Add the where clause
    	    if (where.size()>0) {
                Where<T,Object> whereClause = update.where();
                for (Map.Entry<String,Object> entry : where.entrySet()) {
                    if (!this.fields.containsKey(entry.getKey())) throw new RuntimeException(
                            String.format("Update where: Class %s has no field named %s!",
                                    this.getDataClass(), entry.getKey()));
                    String columnName = this.fields.get(entry.getKey()).getColumnName();
                    if (entry.getValue() == null) {
                        whereClause.isNull(columnName);
                    }
                    else {
                        whereClause.eq(columnName, new SelectArg(entry.getValue()));
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
    	    Map<String,Object> set = new HashMap<String,Object>();
    	    Map<String,Object> whereMap = new HashMap<String,Object>();
    	    
    	    // fill the set map and fields map
    	    for (FieldType field : this.tableInfo.getFieldTypes()) {
    	        Object fieldValue = field.getFieldValueIfNotDefault(value);
                if (fieldValue != null) {
                    set.put(field.getFieldName(), fieldValue);
                }
    	    }
    	    // fill the where map
    	    for (String w : where) {
    	        whereMap.put(w, fields.get(w).extractRawJavaFieldValue(value));
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
            // Construct the query
            Map<String,Object> query = new HashMap<String,Object>();
            for (String w : where) {
                if (!this.fields.containsKey(w)) {
                    throw new RuntimeException("Class "+this.getDataClass().getName()
                            +" does not contain field "+w);
                }
                query.put(w, fields.get(w).extractRawJavaFieldValue(value));
            }
            
            T existing = this.selectOne(query);
            if (existing == null) {
                // try an insert, if that fails, try an update
                int rows = this.create(value);
                return new Dao.CreateOrUpdateStatus(true, false, rows);
            }

            int rows = this.update(value, where);
            return new Dao.CreateOrUpdateStatus(false, true, rows);
	    }
	    catch (SQLException e) {throw new RuntimeException(e);}
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
		// get a map of the where values
		Map<String,Object> values = new HashMap<>();
		for (String w : where) {
		    if (!this.fields.containsKey(w)) throw new RuntimeException(
		            String.format("reload: class %s does not have field %s!", 
		                    this.getDataClass(), w));
		    try { values.put(w, this.fields.get(w).extractRawJavaFieldValue(value)); } 
		    catch (SQLException e) {throw new RuntimeException(e);}
		}
		// get the updated object values
		T updated = where.length == 0?
				this.selectOneOrDie(value) :
				this.selectOneOrDie(values);

		// set the updated object values on the original field
        for (Map.Entry<String,FieldType> entry : this.fields.entrySet()) {
            FieldType field = entry.getValue();
            try { field.assignField(value, field.extractRawJavaFieldValue(updated), false, this.getObjectCache()); } 
            catch (IllegalArgumentException | SQLException e) { throw new RuntimeException(e); }
        }
		return value;
	}
	
	/**
	 * Delete values from the database table.
	 * @return The number of records deleted
	 */
	public int delete(T data) {
        try {
            return super.delete(data);
        }
        catch (SQLException e) {throw new RuntimeException(e);}
	}
	
	/**
	 * Delete a value from the database table.
	 * @return The number of records deleted
	 */
	public int delete(T data, String... where) {
        try {
            if (where.length == 0) {
                return super.delete(data);
            }
            // Construct the query
            Map<String,Object> query = new HashMap<String,Object>();
            for (String w : where) {
                if (!this.fields.containsKey(w)) {
                    throw new RuntimeException("Class "+this.getDataClass().getName()
                            +" does not contain field "+w);
                }
                query.put(w, fields.get(w).extractRawJavaFieldValue(data));
            }
            return delete(query);
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
                    if (!this.fields.containsKey(entry.getKey())) throw new RuntimeException(
                            String.format("delete: Class %s has no field named %s!", 
                                    this.getDataClass(), entry.getKey()));
                    String columnName = this.fields.get(entry.getKey()).getColumnName();
                    if (entry.getValue() == null) {
                        whereClause.isNull(columnName);
                    }
                    else {
                        whereClause.eq(columnName, new SelectArg(entry.getValue()));
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
	    try {
            if (fieldValues.size() == 0) {
                return Collections.emptyList();
            }
            checkForInitialized();
            QueryBuilder<T, Object> qb = queryBuilder();
            Where<T, Object> where = qb.where();
            for (Map.Entry<String, Object> entry : fieldValues.entrySet()) {
                if (!this.fields.containsKey(entry.getKey())) throw new RuntimeException(
                        String.format("select: Class %s does not contain field %s!", 
                                this.getDataClass(), entry.getKey()));
                String columnName = this.fields.get(entry.getKey()).getColumnName();
                Object fieldValue = new SelectArg(entry.getValue());
                if (entry.getValue() == null) {
                    where.isNull(columnName);
                }
                else {
                    where.eq(columnName, fieldValue);
                }
            }
            where.and(fieldValues.size());
            if (DEBUG) {
                long startTime = System.currentTimeMillis();
                PreparedQuery<T> prepared = qb.prepare();
                List<T> results = query(prepared);
                long endTime = System.currentTimeMillis();
                IJ.log(String.format("[Dao] query took %.2f seconds: %s", 
                        (double)(endTime-startTime) / 1000.0, 
                        prepared.getStatement()));
                return results;
            }
            return qb.query();
	    }
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
    
    /**
     * Update sequence values for all generated id fields in a table.
     */
    public void updateSequence() {
        DatabaseType databaseType = connection.getDatabaseType();
        for (Map.Entry<String,FieldType> entry : fields.entrySet()) {
            FieldType fieldType = entry.getValue();
            if (fieldType.isGeneratedId()) {
                String sequenceName = databaseType.generateIdSequenceName(this.getTableInfo().getTableName(), fieldType);
                try {
                    long restart = this.queryRawValue(String.format("select max(\"%s\") from \"%s\"", 
                            fieldType.getColumnName(), 
                            this.getTableInfo().getTableName()));
                    connection.getReadWriteConnection().executeStatement(String.format("alter sequence \"%s\" restart with %d", 
                            sequenceName, restart+1), DatabaseConnection.DEFAULT_RESULT_FLAGS);
                }
                catch (SQLException e) {throw new RuntimeException(e);}
            }
        }
    }
}

