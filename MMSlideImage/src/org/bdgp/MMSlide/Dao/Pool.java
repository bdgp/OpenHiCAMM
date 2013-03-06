package org.bdgp.MMSlide.Dao;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Pool {
    @DatabaseField(canBeNull=false)
    private String poolId;
   
    public Pool(String poolId) {
        this.poolId = poolId;
    }

    public String getPoolId() {
        return poolId;
    }
}
