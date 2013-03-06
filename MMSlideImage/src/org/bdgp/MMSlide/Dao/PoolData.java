package org.bdgp.MMSlide.Dao;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class PoolData {
    @DatabaseField(canBeNull=false, uniqueCombo=true)
    private String poolId;
    @DatabaseField(canBeNull=false, uniqueCombo=true)
    private int cartridgeId;
    @DatabaseField(canBeNull=false, uniqueCombo=true)
    private int slideNumber;
    
    public PoolData(String poolId, int cartridgeId, int slideNumber) {
        this.poolId = poolId;
        this.cartridgeId = cartridgeId;
        this.slideNumber = slideNumber;
    }

    public String getPoolId() {
        return poolId;
    }
    public int getCartridgeId() {
        return cartridgeId;
    }
    public int getSlideNumber() {
        return slideNumber;
    }
}
