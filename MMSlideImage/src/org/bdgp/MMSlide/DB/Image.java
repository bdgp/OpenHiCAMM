package org.bdgp.MMSlide.DB;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Image {
    @DatabaseField(generatedId=true, canBeNull=false) private int id;
    @DatabaseField(canBeNull=false) private int slideId;
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING) private String path;
    @DatabaseField(canBeNull=false) private int slidePosId;
    
    public Image() {}
    public Image (int slideId, int slidePosId, String path) {
        this.slideId = slideId;
        this.slidePosId = slidePosId;
        this.path = path;
    }
    public String getName() {return String.format("I%05d", this.id);}
    public int getSlideId() {return this.slideId;}
    public int getSlidePosId() {return this.slidePosId;}
    public String getPath() {return this.path;}
}
