package org.bdgp.MMSlide.DB;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class ROI {
    @DatabaseField(generatedId=true) private int id;
    @DatabaseField private double x1;
    @DatabaseField private double y1;
    @DatabaseField private double x2;
    @DatabaseField private double y2;

    public ROI() {}
    public ROI(int id, int imageId, double x1, double y1, double x2, double y2) {
        this.id = id;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }
    public int getId() {return this.id;}
    public double getX1() {return this.x1;}
    public double getY1() {return this.y1;}
    public double getX2() {return this.x2;}
    public double getY2() {return this.y2;}
}
