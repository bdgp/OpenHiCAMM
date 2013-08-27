package org.bdgp.MMSlide.DB;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class PoolSlide {
    @DatabaseField(canBeNull=false, uniqueCombo=true)
    private int poolId;
    @DatabaseField(canBeNull=false, uniqueCombo=true)
    private int cartridgePosition;
    @DatabaseField(canBeNull=false, uniqueCombo=true)
    private int slidePosition;
    @DatabaseField
    private int slideId;
    
    public PoolSlide() {}
    public PoolSlide(int cartridgeId, int slideNumber, int slideId) {
        this.cartridgePosition = cartridgeId;
        this.slidePosition = slideNumber;
        this.slideId = slideId;
    }

    public int getPoolId() { return poolId; }
    public int getCartridgePosition() { return cartridgePosition; }
    public int getSlidePosition() { return slidePosition; }
    public int getSlideId() { return slideId; }
}
