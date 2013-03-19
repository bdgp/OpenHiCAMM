package org.bdgp.MMSlide.DB;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class ROI {
    @DatabaseField(generatedId=true) private int id;
    @DatabaseField private long x1;
    @DatabaseField private long y1;
    @DatabaseField private long x2;
    @DatabaseField private long y2;

    public ROI() {}
    public ROI(int id, int imageId, long x1, long y1, long x2, long y2) {
        this.id = id;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }
    public int getId() {return this.id;}
    public long getX1() {return this.x1;}
    public long getY1() {return this.y1;}
    public long getX2() {return this.x2;}
    public long getY2() {return this.y2;}
}
