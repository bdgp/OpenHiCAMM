package org.bdgp.OpenHiCAMM;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
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
        catch (Throwable e) {throw new RuntimeException(e);}
    }
    public Logger(String logfile, String source, Level loglevel) {
        super(source, null);
        if (logfile != null) {
            try { 
                FileHandler fh = new LogFileHandler(logfile);
                this.addHandler(fh); 
            }
            catch (IOException e) {throw new RuntimeException(e);}
        }
        this.setLevel(loglevel);
        
        // close handlers on exit
        final Logger thisLogger = this;
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                for (Handler h : thisLogger.getHandlers()) {
                    h.close();
                }
            }
        });
    }
    
    public LoggerOutputStream getOutputStream() {
        return new LoggerOutputStream(this, this.getLevel());
    }
    public LoggerOutputStream getOutputStream(Level level) {
        return new LoggerOutputStream(this, level);
    }
    
    public static class LogFileHandler extends FileHandler {
    	public LogFileHandler(String pattern, boolean append) 
    	throws IOException, SecurityException
    	{
    		super(pattern, append);
    	}
    	public LogFileHandler(String pattern) 
            throws IOException, SecurityException 
        {
    		super(pattern, false);
    		setFormatter();
    	}
		public LogFileHandler(String pattern, int limit, int count, boolean append) 
            throws IOException, SecurityException 
        {
			super(pattern, limit, count, append);
    		setFormatter();
		}
		public void setFormatter() {
		    final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");
			setFormatter(new SimpleFormatter() {
				public String format(LogRecord record) {
					return String.format("[%s %s %s] %s%n", 
							record.getLoggerName(),
							dateFormat.format(new Date(record.getMillis())), 
							record.getLevel(), 
							record.getMessage());
				}
			});
		}
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
