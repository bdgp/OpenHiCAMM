package org.bdgp.MMSlide;

import java.sql.SQLException;
import java.util.Date;

import org.bdgp.MMSlide.Dao.Dao;
import org.bdgp.MMSlide.Dao.Log;

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
    
}
