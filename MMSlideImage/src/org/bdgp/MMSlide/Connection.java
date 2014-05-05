package org.bdgp.MMSlide;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.security.SecureRandom;
import java.math.BigInteger;

import javax.swing.JOptionPane;

import org.hsqldb.Server;
import org.hsqldb.persist.HsqlProperties;
import org.hsqldb.server.ServerAcl.AclFormatException;

import com.j256.ormlite.jdbc.JdbcPooledConnectionSource;
import com.j256.ormlite.table.DatabaseTableConfig;

import static org.bdgp.MMSlide.Util.map;

public class Connection extends JdbcPooledConnectionSource {
    private static Map<String,String> serverDefaults = map("server.port","9001");
    
    public static class ServerURI {
        public ServerURI(URI serverURI, String user, String password) {
            this.serverURI = serverURI;
            this.user = user;
            this.password = password;
        }
        public URI serverURI;
        public String user;
        public String password;
    }
    private static Map<String,ServerURI> serverURIs = new HashMap<String,ServerURI>();
    private static Map<String,Server> servers = new HashMap<String,Server>();
    private static SecureRandom random = new SecureRandom();

    
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
        	    File loginfile = new File(serverDir, new File(dbPath).getName().replaceFirst("\\.db$","")+".login");
                if (lockfile.createNewFile()) {
                    // set server properties
                    HsqlProperties p = new HsqlProperties();
                    p.setProperty("server.remote_open","true");
                    p.setProperty("server.address", InetAddress.getLocalHost().getHostAddress());
                    p.setProperty("server.port", map(serverDefaults).merge(Main.getConfig()).get("server.port"));
                    p.setProperty("server.no_system_exit","true");
                    p.setProperty("hsqldb.default_table_type","cached");
                    p.setProperty("hsqldb.applog","1");
                    p.setProperty("hsqldb.sqllog","3");
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
                    
                    String user = "mmslide";
                    String pw = new BigInteger(130, random).toString(32);
                    if (loginfile.exists()) {
                        // read the login information from the login file
                        BufferedReader login = new BufferedReader(new FileReader(loginfile.getPath()));
                        user = login.readLine();
                        pw = login.readLine();
                        login.close();
                    }
                    else {
                        // write the login file
                        PrintWriter login = new PrintWriter(loginfile);
                        login.println(user);
                        login.println(pw);
                        login.close();
                    }
                    serverURIs.put(serverPath,new ServerURI(serverURI,user,pw));
                    URI dbURI = new URI(serverURI.getScheme(), serverURI.getUserInfo(), serverURI.getHost(), serverURI.getPort(), 
                            new File("", new File(dbPath).getName().replaceFirst("\\.db$","")).getPath(), serverURI.getQuery(), serverURI.getFragment());
                    // write the lock file
                    PrintWriter lock = new PrintWriter(lockfile);
                    lock.println(serverURI);
                    lock.close();
                    lockfile.deleteOnExit();
                    return new Connection("jdbc:hsqldb:"+dbURI+";file:"+dbPath+";user="+user+";password="+pw, user, pw);
                }
                else {
                    // use the connection URL stored in the existing .lock file
                    BufferedReader lock = new BufferedReader(new FileReader(lockfile.getPath()));
                    String serverString = lock.readLine();
                    lock.close();
                    // read the login information from the login file
                    BufferedReader login = new BufferedReader(new FileReader(loginfile.getPath()));
                    String username = login.readLine();
                    String password = login.readLine();
                    login.close();
                    if (serverString == null || username == null || password == null) {
                        throw new RuntimeException("Could not read server connection information from file "+lockfile.getPath());
                    }
                    URI serverURI = new URI(serverString);
                    
                    serverURIs.put(serverPath,new ServerURI(serverURI, username, password));
                    URI dbURI = new URI(serverURI.getScheme(), serverURI.getUserInfo(), serverURI.getHost(), serverURI.getPort(), 
                            new File("", new File(dbPath).getName().replaceFirst("\\.db$","")).getPath(), serverURI.getQuery(), serverURI.getFragment());
                    try {
                        return new Connection("jdbc:hsqldb:"+dbURI+";file:"+dbPath+";user="+username+";password="+password, username, password);
                    } catch (SQLTransientConnectionException e) {
                    	if (JOptionPane.showConfirmDialog(null, 
                    			"There is a database lock file, but I could not connect to the database. "+
                    			"This could mean that the database is not running. "+
                    			"Do you want me to delete the lock file and re-start the database?", 
                    			"Database Connection Error", 
                    			JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) 
                    	{
                    		serverURIs.remove(serverPath);
                    		lockfile.delete();
                            return new Connection("jdbc:hsqldb:"+dbURI+";file:"+dbPath+";user="+username+";password="+password, username, password);
                    	}
                        throw e;
                    }
                }
    	    }
    	    else {
    	        ServerURI serverURI = serverURIs.get(serverPath);
                URI dbURI = new URI(serverURI.serverURI.getScheme(), serverURI.serverURI.getUserInfo(), 
                        serverURI.serverURI.getHost(), serverURI.serverURI.getPort(), 
                        new File("", new File(dbPath).getName().replaceFirst("\\.db$","")).getPath(), 
                        serverURI.serverURI.getQuery(), serverURI.serverURI.getFragment());
                return new Connection("jdbc:hsqldb:"+dbURI+";file:"+dbPath+";user="+serverURI.user+";password="+serverURI.password, 
                        serverURI.user, serverURI.password);
    	    }
        }
        catch (SQLException e) {throw new RuntimeException(e);} 
        catch (URISyntaxException e) {throw new RuntimeException(e);}
	    catch (IOException e) {throw new RuntimeException(e);}
        catch (AclFormatException e) {throw new RuntimeException(e);}
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
