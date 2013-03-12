package org.bdgp.MMSlide.DB;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Cartridge {
    @DatabaseField(generatedId=true, canBeNull=false)
    private int id;
    public Cartridge() { }
    public int getId() { return this.id; }
}
