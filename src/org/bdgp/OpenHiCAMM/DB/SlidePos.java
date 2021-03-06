package org.bdgp.OpenHiCAMM.DB;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class SlidePos {
    @DatabaseField(generatedId=true,canBeNull=false, allowGeneratedIdInsert=true) 
    private int id;
    @DatabaseField(canBeNull=false)
    private int slidePosListId;
    @DatabaseField(canBeNull=false)
    private int slidePosListIndex;
    @DatabaseField
    private int roiId;

    public SlidePos() { }
    public SlidePos(int slidePosListId, int slidePosListIndex) {
        this.slidePosListId = slidePosListId;
        this.slidePosListIndex = slidePosListIndex;
    }
    public SlidePos(int slidePosListId, int slidePosListIndex, int roiId) {
        this.slidePosListId = slidePosListId;
        this.slidePosListIndex = slidePosListIndex;
        this.roiId = roiId;
    }
    public int getId() { return this.id; }
    public int getSlidePosListId() { return this.slidePosListId; }
    public int getSlidePosListIndex() { return this.slidePosListIndex; }
    public int getRoiId() { return this.roiId; }
    
    public String toString() {
    	return String.format("%s(id=%d, slidePosListId=%d, slidePosListIndex=%d, roiId=%d)",
    			this.getClass().getSimpleName(),
    			this.id, this.slidePosListId, this.slidePosListIndex, this.roiId);
    }
}
