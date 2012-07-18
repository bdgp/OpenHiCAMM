package org.bdgp.MMSlide;

import java.sql.SQLException;
import java.util.Date;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

public class Logger {
    public static enum Level {ALL, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, OFF};
    private Dao<Log> logger;
    private String source;
    private Level loglevel;
    public Logger(String logfile, String source, Level loglevel) {
        this.logger = Dao.get(Log.class, logfile);
        this.source = source;
        this.loglevel = loglevel;
    }
    public void log(Level loglevel, String message) {
        if (this.loglevel.compareTo(loglevel) <= 0) {
            try {
                this.logger.create(new Log(source, new Date(), loglevel, message));
            } catch (SQLException e) {throw new RuntimeException(e);}
        }
    }
    public void severe(String message) {log(Level.SEVERE, message);}
    public void warning(String message) {log(Level.WARNING, message);}
    public void info(String message) {log(Level.INFO, message);}
    public void config(String message) {log(Level.CONFIG, message);}
    public void fine(String message) {log(Level.FINE, message);}
    public void finer(String message) {log(Level.FINER, message);}
    public void finest(String message) {log(Level.FINEST, message);}
    
     /**
     * A class to handle logging to files.
     */
    @DatabaseTable
    public static class Log {
        public Log(String source, Date time, Level loglevel, String message) {
            this.source = source;
            this.time = time;
            this.loglevel = loglevel;
            this.message = message;
        }
        public String getSource() {
            return source;
        }
        public void setSource(String source) {
            this.source = source;
        }
        public Date getTime() {
            return time;
        }
        public void setTime(Date time) {
            this.time = time;
        }
        public Level getLoglevel() {
            return loglevel;
        }
        public void setLoglevel(Level loglevel) {
            this.loglevel = loglevel;
        }
        public String getMessage() {
            return message;
        }
        public void setMessage(String message) {
            this.message = message;
        }
        @DatabaseField
        private String source;
        @DatabaseField
        private Date time;
        @DatabaseField
        private Level loglevel;
        @DatabaseField
        private String message;
    }   
}
