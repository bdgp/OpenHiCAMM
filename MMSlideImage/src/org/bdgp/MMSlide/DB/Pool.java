package org.bdgp.MMSlide.DB;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Pool {
    @DatabaseField(generatedId=true, canBeNull=false)
    private int id;
    public Pool() { }
    public int getId() { return this.id; }
    public String getName() { return String.format("P%05d", this.id); }
}
