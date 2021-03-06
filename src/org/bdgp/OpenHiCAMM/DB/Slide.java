package org.bdgp.OpenHiCAMM.DB;

import org.bdgp.OpenHiCAMM.Util;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Slide {
    @DatabaseField(generatedId=true, canBeNull=false, allowGeneratedIdInsert=true) 
    int id;
    @DatabaseField(canBeNull=false, dataType=DataType.LONG_STRING)
    private String experimentId;
    
    public Slide() {}
    public Slide(String experimentId) {
        this.experimentId = experimentId;
    }

    public int getId() { return id; }
    public String getExperimentId() { return experimentId; }
    public String getName() { return String.format("S%05d", this.id); }
    
    public String toString() {
    	return String.format("%s(id=%d, experimentId=%s)",
    			this.getClass().getSimpleName(), this.id, Util.escape(this.experimentId));
    }
}
