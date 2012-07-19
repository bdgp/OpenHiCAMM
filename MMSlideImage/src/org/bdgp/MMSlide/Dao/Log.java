package org.bdgp.MMSlide.Dao;

import java.util.Date;

import org.bdgp.MMSlide.Logger.Level;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
     * A class to handle logging to files.
     */
@DatabaseTable
public class Log {
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
