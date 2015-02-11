package org.bdgp.OpenHiCAMM.DB;

import org.micromanager.api.ImageCache;

import mmcorej.TaggedImage;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Image {
    @DatabaseField(generatedId=true, canBeNull=false) private int id;
    @DatabaseField(canBeNull=false) private int slideId;
    @DatabaseField(canBeNull=false) private int slidePosId;
    @DatabaseField(canBeNull=false,uniqueCombo=true) private int acquisitionId;
    @DatabaseField(canBeNull=false,uniqueCombo=true) private int channel;
    @DatabaseField(canBeNull=false,uniqueCombo=true) private int slice;
    @DatabaseField(canBeNull=false,uniqueCombo=true) private int frame;
    @DatabaseField(canBeNull=false,uniqueCombo=true) private int position;
    
    public Image() {}
    public Image (int slideId, int slidePosId, Acquisition acquisition, int channel, int slice, int frame, int position) {
        this.slideId = slideId;
        this.slidePosId = slidePosId;
        this.channel = channel;
        this.slice = slice;
        this.frame = frame;
        this.position = position;
        if (acquisition == null) throw new RuntimeException("Acquisition is null");
        this.acquisitionId = acquisition.getId();
    }
    public Image (int slideId, int slidePosId, int acquisitionId, int channel, int slice, int frame, int position) {
        this.slideId = slideId;
        this.slidePosId = slidePosId;
        this.channel = channel;
        this.slice = slice;
        this.frame = frame;
        this.position = position;
        this.acquisitionId = acquisitionId;
    }
    public int getId() {return this.id;}
    public String getName() {return String.format("I%05d", this.id);}

    public int getSlideId() {return this.slideId;}
    public int getSlidePosId() {return this.slidePosId;}
    public int getAcquisitionId() {return this.acquisitionId;}
    public int getChannel() {return this.channel;}
    public int getSlice() {return this.slice;}
    public int getFrame() {return this.frame;}
    public int getPosition() {return this.position;}
    
    public TaggedImage getImage(ImageCache imageCache) {
    	return imageCache.getImage(this.channel, this.slice, this.frame, this.position);
    }
    
    public String toString() {
    	return String.format(
    			"%s(slideId=%d, slidePosId=%d, channel=%d, slice=%d, frame=%d, position=%d, acquisitionId=%d)",
    			this.getClass().getSimpleName(),
    			this.slideId,
    			this.slidePosId,
    			this.channel, 
    			this.slice,
    			this.frame,
    			this.position,
    			this.acquisitionId);
    }
}
