package org.bdgp.MMSlide.DB;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class TI {
    @DatabaseField(generatedId=true, canBeNull=false) private int id;
    @DatabaseField(canBeNull=false) private int imageId;
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING) private String path;
    
    public TI() {}
    public TI(int imageId, String path) {
        this.imageId = imageId;
        this.path = path;
    }
    public int getId() {return this.id;}
    public int getImageId() {return this.imageId;}
    public String getPath() {return this.path;}
    public String getName() {return String.format("T%05d",this.id); }
}
