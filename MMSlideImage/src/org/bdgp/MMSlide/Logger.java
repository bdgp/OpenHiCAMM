package org.bdgp.MMSlide;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;

import com.j256.ormlite.logger.LocalLog;

public class Logger extends java.util.logging.Logger {
    // Turn off ORMlite logging since it's too inflexible to integrate into
    // our logging system.
    static {
        System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "FATAL");
    }
    
    public static Logger create(String logfile, String source, Level loglevel) {
        try { return new Logger(logfile, source, loglevel); }
        catch (Exception e) {throw new RuntimeException(e);}
    }
    public Logger(String logfile, String source, Level loglevel) {
        super(source, null);
        try { 
            FileHandler fh = new FileHandler(logfile);
            fh.setFormatter(new SimpleFormatter());
            this.addHandler(fh); 
        }
        catch (IOException e) {throw new RuntimeException(e);}
        this.setLevel(loglevel);
    }
    public LoggerOutputStream getOutputStream() {
        return new LoggerOutputStream(this, this.getLevel());
    }
    public LoggerOutputStream getOutputStream(Level level) {
        return new LoggerOutputStream(this, level);
    }
    
    public class LoggerOutputStream extends OutputStream {
        private Logger logger;
        private Level level;
        private StringBuilder buffer;
        
        public LoggerOutputStream(Logger logger, Level level) {
            this.logger = logger;
            this.level = level;
            this.buffer = new StringBuilder();
        }
        @Override public void write(int b) throws IOException {
            if (b == '\n') {
                logger.log(level, buffer.toString());
                buffer.setLength(0);
            }
            else {
                buffer.append((char)b);
            }
        }
        @Override public void close() throws IOException {
            flush();
        }
        @Override public void flush() throws IOException {
            if (buffer.length() > 0) {
                write('\n');
            }
        }    
    }
}
