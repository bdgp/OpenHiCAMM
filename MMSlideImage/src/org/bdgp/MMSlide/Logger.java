package org.bdgp.MMSlide;

import java.util.Date;
import java.util.List;

import org.bdgp.MMSlide.Dao.Dao;
import org.bdgp.MMSlide.Dao.Log;

public class Logger {
    public static enum Level {ALL, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, OFF};
    private List<LogListener> logListeners;
    private Dao<Log> logger;
    private String source;
    private Level loglevel;
    public Logger(String logfile, String source, Level loglevel) {
        this.logger = Dao.get(Log.class, logfile);
        this.source = source;
        this.loglevel = loglevel != null ? loglevel : Level.INFO;
    }
    public void addListener(LogListener listener) {
        this.logListeners.add(listener);
    }
    public void removeListener(LogListener listener) {
        this.logListeners.remove(listener);
    }
    public void log(Level loglevel, String message) {
        if (this.loglevel.compareTo(loglevel) <= 0) {
            Log log = new Log(source, new Date(), loglevel, message);
            this.logger.insert(log);
            for (LogListener logListener : logListeners) {
                logListener.log(log);
            }
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
