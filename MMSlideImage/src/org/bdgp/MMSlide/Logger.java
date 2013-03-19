package org.bdgp.MMSlide;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.bdgp.MMSlide.DB.Log;

public class Logger {
    public static enum Level {ALL, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, OFF};
    private List<LogListener> logListeners;
    private String source;
    private Level loglevel;
    private BufferedWriter log;
    
    public Logger(String logfile, String source, Level loglevel) {
        this.source = source;
        this.loglevel = loglevel != null ? loglevel : Level.INFO;
        try { this.log = new BufferedWriter(new FileWriter(logfile)); }
        catch (IOException e) { throw new RuntimeException(e); }
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
            try { this.log.write(log.toString()); this.log.newLine(); } 
            catch (IOException e) { throw new RuntimeException(e); }
            
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
