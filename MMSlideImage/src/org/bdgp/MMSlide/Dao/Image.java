package org.bdgp.MMSlide.Dao;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Image {
    @DatabaseField private String poolId;
    @DatabaseField private int cartridgeId;
    @DatabaseField private int slideNumber;
    @DatabaseField private int roiId;
    @DatabaseField private String path;
    
    public Image (String poolId, int cartridgeId, int slideNumber, int roiId, String path) {
        this.poolId = poolId;
        this.cartridgeId = cartridgeId;
        this.slideNumber = slideNumber;
        this.roiId = roiId;
        this.path = path;
    }
    public String getPoolId() {return this.poolId;}
    public int getCartridgeId() {return this.cartridgeId;}
    public int getSlideNumber() {return this.slideNumber;}
    public int getRoiId() {return this.roiId;}
    public String getPath() {return this.path;}
}
