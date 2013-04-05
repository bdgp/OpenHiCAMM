package org.bdgp.MMSlide;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.hsqldb.Server;
import org.hsqldb.persist.HsqlProperties;
import org.hsqldb.server.ServerAcl.AclFormatException;

import com.j256.ormlite.jdbc.JdbcPooledConnectionSource;
import com.j256.ormlite.table.DatabaseTableConfig;

import static org.bdgp.MMSlide.Util.map;

public class Connection extends JdbcPooledConnectionSource {
    private static Map<String,String> serverDefaults = map("server.port","9001");
    
    private static Map<String,Server> servers = new HashMap<String,Server>();
    private static Map<String,URI> serverURIs = new HashMap<String,URI>();
    
    public Connection(String string) throws SQLException {
        super(string);
    }
    public Connection(String string, String user, String pw) throws SQLException {
        super(string, user, pw);
    }
    
    public static Connection get(String dbPath) {
        return get(dbPath,dbPath);
    }
    public static Connection get(String serverPath, String dbPath) {
        try {
            String user = "SA";
            String pw = "";
            // create the server and db directories if they do not exist
    	    File serverDir = new File(new File(serverPath).getParent());
    	    if (!(serverDir.exists() && serverDir.isDirectory())) {
    	        serverDir.mkdirs();
        		if (!(serverDir.exists() && serverDir.isDirectory())) {
        		    throw new RuntimeException("Could not create directory "+serverDir.getPath());
        		}
    	    }
    	    File dbDir = new File(new File(dbPath).getParent());
    	    if (!(dbDir.exists() && dbDir.isDirectory())) {
    	        dbDir.mkdirs();
        		if (!(dbDir.exists() && dbDir.isDirectory())) {
        		    throw new RuntimeException("Could not create directory "+dbDir.getPath());
        		}
    	    }
    	    
    	    // start a new HSQLDB server if one hasn't been started yet.
    	    if (serverURIs.get(serverPath) == null) {
        	    File lockfile = new File(serverDir, new File(dbPath).getName().replaceFirst("\\.db$","")+".lock");
                if (lockfile.createNewFile()) {
                    // set server properties
                    HsqlProperties p = new HsqlProperties();
                    p.setProperty("server.remote_open","true");
                    p.setProperty("server.address", InetAddress.getLocalHost().getHostAddress());
                    p.setProperty("server.port", map(serverDefaults).merge(Main.getConfig()).get("server.port"));
                    p.setProperty("server.no_system_exit","true");
                    // writer server output to a log file
                    Logger logger = Logger.create(new File(serverDir, WorkflowRunner.LOG_FILE).getPath(), 
                            "HSQLDB", Level.INFO);
                    // instantiate a new database server
                    Server server = new Server();
                    server.setLogWriter(new PrintWriter(logger.getOutputStream(Level.INFO)));
                    server.setErrWriter(new PrintWriter(logger.getOutputStream(Level.WARNING)));
                    server.setProperties(p);
                    server.start();
                    servers.put(serverPath,server);
                    // write the server connection URL to the lock file
                    URI serverURI = new URI("hsql",null,server.getAddress(),server.getPort(),null,null,null);
                    serverURIs.put(serverPath,serverURI);
                    URI dbURI = new URI(serverURI.getScheme(), serverURI.getUserInfo(), serverURI.getHost(), serverURI.getPort(), 
                            new File("", new File(dbPath).getName().replaceFirst("\\.db$","")).getPath(), serverURI.getQuery(), serverURI.getFragment());
                    PrintWriter lock = new PrintWriter(lockfile);
                    lock.print(serverURI);
                    lock.close();
                    lockfile.deleteOnExit();
                    return new Connection("jdbc:hsqldb:"+dbURI+";file:"+dbPath, user, pw);
                }
                else {
                    // use the connection URL stored in the existing .lock file
                    URI serverURI = new URI(new String(Files.readAllBytes(FileSystems.getDefault().getPath(lockfile.getPath()))));
                    serverURIs.put(serverPath,serverURI);
                    URI dbURI = new URI(serverURI.getScheme(), serverURI.getUserInfo(), serverURI.getHost(), serverURI.getPort(), 
                            new File("", new File(dbPath).getName().replaceFirst("\\.db$","")).getPath(), serverURI.getQuery(), serverURI.getFragment());
                    return new Connection("jdbc:hsqldb:"+dbURI+";file:"+dbPath, user, pw);
                }
    	    }
    	    else {
    	        URI serverURI = serverURIs.get(serverPath);
                URI dbURI = new URI(serverURI.getScheme(), serverURI.getUserInfo(), serverURI.getHost(), serverURI.getPort(), 
                        new File("", new File(dbPath).getName().replaceFirst("\\.db$","")).getPath(), serverURI.getQuery(), serverURI.getFragment());
                return new Connection("jdbc:hsqldb:"+dbURI+";file:"+dbPath, user, pw);
    	    }
        }
        catch (SQLException e) {throw new RuntimeException(e);} 
        catch (URISyntaxException e) {throw new RuntimeException(e);}
	    catch (IOException e) {throw new RuntimeException(e);}
        catch (AclFormatException e) {throw new RuntimeException(e);}
    }
    
    public <T> Dao<T> table(Class<T> class_, String tablename) {
        return Dao.getTable(class_, this, tablename);
    }
    public <T> Dao<T> table(Class<T> class_) {
        try {
    		DatabaseTableConfig<T> tableConfig = DatabaseTableConfig.fromClass(this, class_);
    		if (tableConfig == null) {
    		    throw new RuntimeException("Could not get table config for class "+class_.getName());
    		}
            return Dao.getTable(class_, this, tableConfig.getTableName());
        } 
        catch (SQLException e) {throw new RuntimeException(e);}
    }
    
    public <T> Dao<T> file(Class<T> class_, String filename) {
        return Dao.getFile(class_, this, filename);
    }
    
}
