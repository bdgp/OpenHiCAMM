package org.bdgp.MMSlide;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;


import com.j256.ormlite.jdbc.JdbcPooledConnectionSource;
import com.j256.ormlite.table.DatabaseTableConfig;

public class Connection extends JdbcPooledConnectionSource {
    public Connection(String string) throws SQLException {
        super(string);
    }
    public Connection(String string, String user, String pw) throws SQLException {
        super(string, user, pw);
    }
    
    public static Connection get(String dbPath) {
        return get(dbPath,"sa","",false);
    }
    public static Connection get(String dbPath, boolean create) {
        return get(dbPath,"sa","",create);
    }
    public static Connection get(String dbPath, String user, String pw, boolean create) {
        try {
    	    File dir = new File(dbPath).getParentFile();
    	    if (!(dir.exists() && dir.isDirectory())) {
    	        dir.mkdirs();
        		if (!(dir.exists() && dir.isDirectory())) {
        		    throw new RuntimeException("Could not create directory "+dir.getPath());
        		}
    	    }
            Connection connection = new Connection(
                        "jdbc:hsqldb:file:"+new URI(new File(dbPath).getAbsolutePath()).getPath()+";create="+create, user, pw);
            return connection;
        }
        catch (SQLException e) {throw new RuntimeException(e);} 
        catch (URISyntaxException e) {throw new RuntimeException(e);}
    }
    
    public <T> Dao<T> table(Class<T> class_, String tablename) {
        return (Dao<T>) DaoID.getTable(class_, this, tablename);
    }
    public <T> Dao<T> table(Class<T> class_) {
        try {
    		DatabaseTableConfig<T> tableConfig = DatabaseTableConfig.fromClass(this, class_);
    		if (tableConfig == null) {
    		    throw new RuntimeException("Could not get table config for class "+class_.getName());
    		}
            return (Dao<T>) DaoID.getTable(class_, this, tableConfig.getTableName());
        } 
        catch (SQLException e) {throw new RuntimeException(e);}
    }
    
    public static <T> Dao<T> file(Class<T> class_, String filename) {
        return (Dao<T>) DaoID.getFile(class_, filename);
    }
    
}
