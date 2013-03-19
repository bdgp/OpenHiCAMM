package org.bdgp.MMSlide.DB;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Image {
    @DatabaseField(generatedId=true, canBeNull=false) private int id;
    @DatabaseField(canBeNull=false) private int poolId;
    @DatabaseField(canBeNull=false) private int cartridgeId;
    @DatabaseField(canBeNull=false) private int slideNumber;
    @DatabaseField(canBeNull=false) private int roiId;
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING) private String path;
    
    public Image() {}
    public Image (int poolId, int cartridgeId, int slideNumber, int roiId, String path) {
        this.poolId = poolId;
        this.cartridgeId = cartridgeId;
        this.slideNumber = slideNumber;
        this.roiId = roiId;
        this.path = path;
    }
    public String getName() {return String.format("I%05d", this.id);}
    public int getPoolId() {return this.poolId;}
    public int getCartridgeId() {return this.cartridgeId;}
    public int getSlideNumber() {return this.slideNumber;}
    public int getRoiId() {return this.roiId;}
    public String getPath() {return this.path;}
}
