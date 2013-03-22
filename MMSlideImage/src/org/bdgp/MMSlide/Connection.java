package org.bdgp.MMSlide;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.Map;

import org.bdgp.MMSlide.Logger.Level;
import org.hsqldb.Server;
import org.hsqldb.persist.HsqlProperties;
import org.hsqldb.server.ServerAcl.AclFormatException;

import com.j256.ormlite.jdbc.JdbcPooledConnectionSource;
import com.j256.ormlite.table.DatabaseTableConfig;

import static org.bdgp.MMSlide.Util.map;

public class Connection extends JdbcPooledConnectionSource {
    private static Map<String,String> serverDefaults = map("server.port","9001");
    private static Server server;
    
    public static Server getServer() {
        return server;
    }
    
    public Connection(String string) throws SQLException {
        super(string);
    }
    public Connection(String string, String user, String pw) throws SQLException {
        super(string, user, pw);
    }
    
    public static Connection get(String dbPath) {
        return get(dbPath,"SA","");
    }
    public static Connection get(String dbPath, String user, String pw) {
        try {
    	    File dir = new File(dbPath).getParentFile();
    	    if (!(dir.exists() && dir.isDirectory())) {
    	        dir.mkdirs();
        		if (!(dir.exists() && dir.isDirectory())) {
        		    throw new RuntimeException("Could not create directory "+dir.getPath());
        		}
    	    }
    	    // start a new HSQLDB server if one hasn't been started yet.
    	    if (server == null) {
        	    File lockfile = new File(dir, ".lock");
                if (lockfile.createNewFile()) {
                    // set server properties
                    HsqlProperties p = new HsqlProperties();
                    p.setProperty("server.remote_open","true");
                    p.setProperty("server.port", map(serverDefaults).merge(Main.getConfig()).get("server.port"));
                    p.setProperty("server.no_system_exit","true");
                    // writer server output to a log file
                    Logger logger = Logger.create(new File(dir, WorkflowRunner.LOG_FILE).getPath(), "HSQLDB", Level.INFO);
                    // instantiate a new database server
                    server = new Server();
                    try { server.setProperties(p); } 
                    catch (AclFormatException e) {throw new RuntimeException(e);}
                    server.setLogWriter(logger);
                    server.setErrWriter(logger);
                    server.start();
                    // write the server connection URL to the lock file
                    String dbURL = "jdbc:hsqldb:"+
                            new URL("hsql", server.getAddress(), server.getPort(), "/"+new File(dbPath).getName())+
                            ";file:"+dbPath;
                    PrintWriter lock = new PrintWriter(lockfile);
                    lock.print(dbURL);
                    lock.close();
                    lockfile.deleteOnExit();
                    return new Connection(dbURL, user, pw);
                }
                else {
                    // use the connection URL stored in the existing .lock file
                    return new Connection(
                            new String(Files.readAllBytes(FileSystems.getDefault().getPath(lockfile.getPath()))),
                            user, pw);
                }
    	    }
    	    else {
    	        // connect to the server running in this process
                String dbURL = "jdbc:hsqldb:"+
                        new URL("hsql", server.getAddress(), server.getPort(), "/"+new File(dbPath).getName())+
                        ";file:"+dbPath;
                return new Connection(dbURL, user, pw);
    	    }
        }
        catch (SQLException e) {throw new RuntimeException(e);} 
        catch (MalformedURLException e) {throw new RuntimeException(e);}
	    catch (IOException e) {throw new RuntimeException(e);}
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
    
    public <T> Dao<T> file(Class<T> class_, String filename) {
        return (Dao<T>) DaoID.getFile(class_, this, filename);
    }
    
}
