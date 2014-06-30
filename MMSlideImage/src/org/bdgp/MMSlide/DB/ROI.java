package org.bdgp.MMSlide.DB;
 
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
 
@DatabaseTable
public class ROI {
    @DatabaseField(generatedId=true) private int id;
    @DatabaseField(canBeNull=false) private int imageId;
    @DatabaseField(canBeNull=false) private int x1;
    @DatabaseField(canBeNull=false) private int y1;
    @DatabaseField(canBeNull=false) private int x2;
    @DatabaseField(canBeNull=false) private int y2;
 
    public ROI() {}
    public ROI(int imageId, int x1, int y1, int x2, int y2) {
        this.imageId = imageId;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }
    public int getId() {return this.id;}
    public int getImageId() {return this.imageId;}
    public int getX1() {return this.x1;}
    public int getY1() {return this.y1;}
    public int getX2() {return this.x2;}
    public int getY2() {return this.y2;}
}