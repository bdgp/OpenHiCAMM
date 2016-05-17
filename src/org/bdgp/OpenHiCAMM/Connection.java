package org.bdgp.OpenHiCAMM;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;
import java.security.SecureRandom;
import java.math.BigInteger;

import javax.swing.JOptionPane;

import org.hsqldb.Server;
import org.hsqldb.persist.HsqlProperties;
import org.hsqldb.server.ServerAcl.AclFormatException;

import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.jdbc.JdbcDatabaseConnection;
import com.j256.ormlite.support.DatabaseConnection;
import com.j256.ormlite.table.DatabaseTableConfig;

import static org.bdgp.OpenHiCAMM.Util.map;

public class Connection extends JdbcConnectionSource {
    private static Map<String,String> serverDefaults = map("server.port","9001");
    private static SecureRandom random = new SecureRandom();
    private static Server server;
    
    String url;
    String user;
    String pw;
    String schema;
    
    public Connection(String string, String user, String pw) throws SQLException {
        super(string, user, pw);
        this.url = string;
        this.user = user;
        this.pw = pw;
        this.schema = "PUBLIC";
    }
    
    public static Connection get(String dbPath) {
        return Connection.get(dbPath, null, false);
    }
    public static Connection get(String dbPath, String schema) {
        return Connection.get(dbPath, schema, false);
    }
    public static Connection get(String dbPath, String schema, boolean removeLockFile) {
        try {
        	// create the db dir if it does not exist
    	    File dbDir = new File(new File(dbPath).getParent());
    	    if (!(dbDir.exists() && dbDir.isDirectory())) {
    	        dbDir.mkdirs();
        		if (!(dbDir.exists() && dbDir.isDirectory())) {
        		    throw new RuntimeException("Could not create directory "+dbDir.getPath());
        		}
    	    }

            File loginfile = new File(dbDir, new File(dbPath).getName().replaceFirst("\\.db$","")+".login");
            File lockfile = new File(dbDir, new File(dbPath).getName().replaceFirst("\\.db$","")+".lock");

            // try to create the db lockfile
            if (lockfile.createNewFile()) {
                lockfile.deleteOnExit();
            	// make sure a server is started
            	startServer(dbDir.getPath());

            	// Choose a random string to be the DB alias
            	String dbAlias = String.format("/DB_%s", new BigInteger(130, random).toString(32));
                // write the server connection URL to the lock file
                URI dbURI = new URI("hsql",null,server.getAddress(),server.getPort(), dbAlias, null,null);
                // write the lock file
                PrintWriter lock = new PrintWriter(lockfile);
                lock.println(dbURI);
                lock.close();
                
                // write the user/password to the login file
                String user = "openhicamm";
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

                Connection con = new Connection(
                		String.format("jdbc:hsqldb:%s;file:%s;user=%s;password=%s;hsqldb.default_table_type=cached;hsqldb.script_format=3",dbURI,dbPath,user,pw), 
                		user, pw);
                if (schema != null) con.setSchema(schema);
                return con;
            }
            else {
                // use the connection URL stored in the existing .lock file
            	// There is a potential race condition here; if the one thread gets the lock file, it
            	// doesn't write the server connection string right away, so just in case we need to loop
            	// until the connection string is written to the file. To make sure the connection URL is fully
            	// written, make sure it ends in a newline.
            	String dbString = "";
            	while (dbString.isEmpty()) {
            		Scanner scanner = new Scanner(lockfile);
            		scanner.useDelimiter("\\Z");
            		dbString = scanner.next();
            		scanner.close();
                    if (!dbString.matches("\n$")) {
						try { Thread.sleep(500); } 
						catch (InterruptedException e) { }
                    }
            	}
            	dbString = dbString.replaceFirst("\n$", "");

                // read the login information from the login file
                BufferedReader login = new BufferedReader(new FileReader(loginfile.getPath()));
                String username = login.readLine();
                String password = login.readLine();
                login.close();
                if (dbString == null || username == null || password == null) {
                    throw new RuntimeException("Could not read server connection information from file "+lockfile.getPath());
                }
                URI dbURI = new URI(dbString);
                try {
                    Connection con = new Connection(
                    		String.format("jdbc:hsqldb:%s;file:%s;user=%s;password=%s;hsqldb.default_table_type=cached;hsqldb.script_format=3", dbURI,dbPath,username,password), 
                    		username, password);
                    // test the connection
                    if (schema != null) con.setSchema(schema);
                    DatabaseConnection dc = con.getReadWriteConnection();
                    con.releaseConnection(dc);
                    return con;
                } catch (SQLException e) {
                    if (removeLockFile || JOptionPane.showConfirmDialog(null, 
                            "There is a database lock file, but I could not connect to the database.\n"+
                            "This could mean that the database is not running.\n"+
                            "Do you want me to delete the lock file and re-start the database?", 
                            "Database Connection Error", 
                            JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) 
                    {
                        lockfile.delete();
                        return get(dbPath);
                    }
                    throw e;
                }
            }
        }
        catch (SQLException e) {throw new RuntimeException(e);} 
        catch (URISyntaxException e) {throw new RuntimeException(e);}
	    catch (IOException e) {throw new RuntimeException(e);}
    }
    
    private static synchronized void startServer(String logDir) {
    	if (server == null) {
            // set server properties
            HsqlProperties p = new HsqlProperties();
            p.setProperty("server.remote_open","true");
            try { p.setProperty("server.address", InetAddress.getLocalHost().getHostAddress()); } 
            catch (UnknownHostException e) {throw new RuntimeException(e);}
            p.setProperty("server.port", map(serverDefaults).get("server.port"));
            p.setProperty("server.no_system_exit","true");
            p.setProperty("hsqldb.default_table_type","cached");
            p.setProperty("hsqldb.script_format","3");
            p.setProperty("hsqldb.applog","3");
            p.setProperty("hsqldb.sqllog","3");
            p.setProperty("hsqldb.defrag_limit","10");
            // writer server output to a log file
            Logger logger = Logger.create(new File(logDir, "db.log").getPath(), 
            "HSQLDB", Level.INFO);
            // instantiate a new database server
            server = new Server();
            server.setLogWriter(new PrintWriter(logger.getOutputStream(Level.INFO)));
            server.setErrWriter(new PrintWriter(logger.getOutputStream(Level.WARNING)));
            try { server.setProperties(p); } 
            catch (IOException e) {throw new RuntimeException(e);} 
            catch (AclFormatException e) {throw new RuntimeException(e);}
            server.start();
    	}
	}
    
    public void startDatabaseManager() {
        try {
            File dbMgrJar = new File(org.hsqldb.util.DatabaseManager.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
            Runtime.getRuntime().exec(new String[] {
                    "java","-jar",dbMgrJar.getPath(),
                    "--driver","org.hsqldb.jdbcDriver",
                    "--url",this.url,
                    "--user",this.user,
                    "--password",this.pw
            });
        } 
        catch (URISyntaxException e1) {throw new RuntimeException(e1);}
        catch (IOException e1) {throw new RuntimeException(e1);}
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
        try {
            DatabaseTableConfig<T> tableConfig = DatabaseTableConfig.fromClass(this, class_);
            if (tableConfig == null) {
                throw new RuntimeException("Could not get table config for class "+class_.getName());
            }
            return Dao.getFile(class_, this, tableConfig.getTableName(), filename);
        }
        catch (SQLException e) {throw new RuntimeException(e);}
    }
	
	public void createSchema(String schema) {
	    try {
            DatabaseConnection dc = this.getReadWriteConnection();
            StringBuilder sb = new StringBuilder();
            sb.append("CREATE SCHEMA ");
            this.getDatabaseType().appendEscapedEntityName(sb, schema);
            sb.append(" AUTHORIZATION ");
            this.getDatabaseType().appendEscapedEntityName(sb, this.user);
            dc.executeStatement(sb.toString(), DatabaseConnection.DEFAULT_RESULT_FLAGS);
	    }
	    catch (SQLException e) { throw new RuntimeException(e); }
	}
    
    public void setSchema(String schema) {
        try {
            if (!isSchemaExists(schema)) {
                createSchema(schema);
            }
            DatabaseConnection dc = this.getReadWriteConnection();
            StringBuilder sb = new StringBuilder();
            sb.append("SET SCHEMA ");
            this.getDatabaseType().appendEscapedEntityName(sb, schema);
            dc.executeStatement(sb.toString(), DatabaseConnection.DEFAULT_RESULT_FLAGS);
            
            this.schema = schema;
        } 
        catch (SQLException e) { throw new RuntimeException(e); }
    }
    
    public String getSchema() {
        return this.schema;
    }

    /**
     * Schema-aware isTableExists implementation. Taken from JdbcDatabaseConnection.
     * @param tableName
     * @param tableSchem
     * @return
     * @throws SQLException
     */
    public boolean isTableExists(String tableName) {
        return this.isTableExists(tableName, this.schema);
    }
	public boolean isTableExists(String tableName, String tableSchem) {
	    try {
            // we only support schema checking for JdbcDatabaseConnections.
            DatabaseConnection dc = this.getReadWriteConnection();
            if (!JdbcDatabaseConnection.class.isAssignableFrom(dc.getClass()) || tableSchem == null) {
                throw new RuntimeException("Only JdbcDatabaseConnections are supported!");
            }

            JdbcDatabaseConnection jdc = (JdbcDatabaseConnection)dc;
            DatabaseMetaData metaData = jdc.getInternalConnection().getMetaData();
            ResultSet results = null;
            try {
                results = metaData.getTables(null, "%", "%", new String[] { "TABLE" });
                // we do it this way because some result sets don't like us to findColumn if no results
                if (!results.next()) {
                    return false;
                }
                int table_name = results.findColumn("TABLE_NAME");
                int table_schem = results.findColumn("TABLE_SCHEM");
                do {
                    String dbTableName = results.getString(table_name);
                    String dbTableSchem = results.getString(table_schem);
                    if (tableName.equalsIgnoreCase(dbTableName) && tableSchem.equalsIgnoreCase(dbTableSchem)) {
                        return true;
                    }
                } while (results.next());
                return false;
            } finally {
                if (results != null) {
                    results.close();
                }
            }
	    } 
	    catch (SQLException e) { throw new RuntimeException(e); }
	}
	
	public boolean isSchemaExists(String tableSchem) {
	    try {
            // we only support schema checking for JdbcDatabaseConnections.
            DatabaseConnection dc = this.getReadWriteConnection();
            if (!JdbcDatabaseConnection.class.isAssignableFrom(dc.getClass()) || tableSchem == null) {
                throw new RuntimeException("Only JdbcDatabaseConnections are supported!");
            }

            JdbcDatabaseConnection jdc = (JdbcDatabaseConnection)dc;
            DatabaseMetaData metaData = jdc.getInternalConnection().getMetaData();
            ResultSet results = null;
            try {
                results = metaData.getSchemas();
                // we do it this way because some result sets don't like us to findColumn if no results
                if (!results.next()) {
                    return false;
                }
                int table_schem = results.findColumn("TABLE_SCHEM");
                do {
                    String dbTableSchem = results.getString(table_schem);
                    if (tableSchem.equalsIgnoreCase(dbTableSchem)) {
                        return true;
                    }
                } while (results.next());
                return false;
            } finally {
                if (results != null) {
                    results.close();
                }
            }
	    } 
	    catch (SQLException e) { throw new RuntimeException(e); }
	}
}
