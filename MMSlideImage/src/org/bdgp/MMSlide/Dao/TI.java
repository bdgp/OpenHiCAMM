package org.bdgp.MMSlide.Dao;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class TI {
    @DatabaseField private int id;
    @DatabaseField private int imageId;
    @DatabaseField private String path;
    
    public TI(int id, int imageId, String path) {
        this.id = id;
        this.imageId = imageId;
        this.path = path;
    }
    public int getId() {return this.id;}
    public int getImageId() {return this.imageId;}
    public String getPath() {return this.path;}
}
