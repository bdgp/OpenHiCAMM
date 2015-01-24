package org.bdgp.MMSlide.DB;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Slide {
    @DatabaseField(generatedId=true, canBeNull=false) 
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
}
