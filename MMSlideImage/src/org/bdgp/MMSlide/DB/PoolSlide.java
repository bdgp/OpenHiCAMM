package org.bdgp.MMSlide.DB;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class PoolSlide implements Comparable<PoolSlide> {
    @DatabaseField(generatedId=true, canBeNull=false)
    private int id;
    @DatabaseField(canBeNull=false, uniqueCombo=true)
    private int poolId;
    @DatabaseField(canBeNull=false, uniqueCombo=true)
    private int cartridgePosition;
    @DatabaseField(canBeNull=false, uniqueCombo=true)
    private int slidePosition;
    @DatabaseField
    private int slideId;
    
    public PoolSlide() {}
    public PoolSlide(int poolId, int cartridgePosition, int slidePosition, int slideId) {
        this.poolId = poolId;
        this.cartridgePosition = cartridgePosition;
        this.slidePosition = slidePosition;
        this.slideId = slideId;
    }

    public int getId() { return id; }
    public int getPoolId() { return poolId; }
    public int getCartridgePosition() { return cartridgePosition; }
    public int getSlidePosition() { return slidePosition; }
    public int getSlideId() { return slideId; }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + cartridgePosition;
        result = prime * result + slidePosition;
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PoolSlide other = (PoolSlide) obj;
        if (cartridgePosition != other.cartridgePosition)
            return false;
        if (slidePosition != other.slidePosition)
            return false;
        return true;
    }

    @Override
    public int compareTo(PoolSlide o) {
        int result = this.cartridgePosition-o.cartridgePosition;
        if (result == 0) {
            result = this.slidePosition-o.slidePosition;
        }
        return result;
    }
    
    public String toString() {
    	return String.format("%s(id=%d, poolId=%d, cartridgePosition=%d, slidePosition=%d, slideId=%d)",
    			this.getClass().getSimpleName(), this.poolId, this.cartridgePosition, this.slidePosition, this.slideId);
    }
}
