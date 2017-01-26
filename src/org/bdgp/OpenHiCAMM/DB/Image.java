package org.bdgp.OpenHiCAMM.DB;

import org.bdgp.OpenHiCAMM.Dao;
import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.api.ImageCache;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;

import mmcorej.TaggedImage;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import ij.ImagePlus;
import ij.process.ImageProcessor;

import static org.bdgp.OpenHiCAMM.Util.where;

@DatabaseTable
public class Image {
    @DatabaseField(generatedId=true, canBeNull=false) private int id;
    @DatabaseField(canBeNull=false) private int slideId;
    @DatabaseField(canBeNull=false,uniqueCombo=true) private int acquisitionId;
    @DatabaseField(canBeNull=false,uniqueCombo=true) private int channel;
    @DatabaseField(canBeNull=false,uniqueCombo=true) private int slice;
    @DatabaseField(canBeNull=false,uniqueCombo=true) private int frame;
    @DatabaseField(canBeNull=false,uniqueCombo=true) private int position;
    @DatabaseField(canBeNull=true) private String path;
    
    public Image() {}
    public Image (int slideId, Acquisition acquisition, int channel, int slice, int frame, int position) {
        this.slideId = slideId;
        this.channel = channel;
        this.slice = slice;
        this.frame = frame;
        this.position = position;
        if (acquisition == null) throw new RuntimeException("Acquisition is null");
        this.acquisitionId = acquisition.getId();
    }
    public Image (int slideId, int acquisitionId, int channel, int slice, int frame, int position) {
        this.slideId = slideId;
        this.channel = channel;
        this.slice = slice;
        this.frame = frame;
        this.position = position;
        this.acquisitionId = acquisitionId;
    }
    public Image (String path, int slideId, int acquisitionId, int channel, int slice, int frame, int position) {
        this(slideId, acquisitionId, channel, slice, frame, position);
        this.path = path;
    }
    public Image (String path, int slideId) {
        this(slideId, 0, 0, 0, 0, 0);
        this.path = path;
    }
    public int getId() {return this.id;}
    public String getName() {return String.format("I%05d", this.id);}

    public int getSlideId() {return this.slideId;}
    public int getAcquisitionId() {return this.acquisitionId;}
    public int getChannel() {return this.channel;}
    public int getSlice() {return this.slice;}
    public int getFrame() {return this.frame;}
    public int getPosition() {return this.position;}
    public String getPath() {return this.path;}
    
    public TaggedImage getImage(ImageCache imageCache) {
    	return imageCache.getImage(this.channel, this.slice, this.frame, this.position);
    }

    public TaggedImage getTaggedImage(Dao<Acquisition> acqDao) {
        if (this.acquisitionId == 0) {
            ImagePlus img = new ImagePlus(this.getPath());
            return ImageUtils.makeTaggedImage(img.getProcessor());
        }
        // Initialize the acquisition
        Acquisition acquisition = acqDao.selectOneOrDie(where("id",this.getAcquisitionId()));
        MMAcquisition mmacquisition = acquisition.getAcquisition(acqDao);

        // Get the image cache object
        ImageCache imageCache = mmacquisition.getImageCache();
        if (imageCache == null) throw new RuntimeException("Acquisition was not initialized; imageCache is null!");
        // Get the tagged image from the image cache
        TaggedImage taggedImage = this.getImage(imageCache);
        if (taggedImage == null) throw new RuntimeException(String.format("Acqusition %s, Image %s is not in the image cache!",
                acquisition, this));
        return taggedImage;
    }
    
    public ImagePlus getImagePlus(Dao<Acquisition> acqDao) {
        if (this.acquisitionId == 0) {
            ImagePlus img = new ImagePlus(this.getPath());
            return img;
        }
        TaggedImage taggedImage = this.getTaggedImage(acqDao);
        ImageProcessor processor = ImageUtils.makeProcessor(taggedImage);
        ImagePlus imp = new ImagePlus(this.toString(), processor);
        return imp;
    }
    
    public String getLabel() {
        return MDUtils.generateLabel(this.channel, this.slice, this.frame, this.position);
    }
    
    public String toString() {
    	return String.format(
    			"%s(id=%d, path=%s, slideId=%d, channel=%d, slice=%d, frame=%d, position=%d, acquisitionId=%d)",
    			this.getClass().getSimpleName(),
    			this.id,
    			this.path,
    			this.slideId,
    			this.channel, 
    			this.slice,
    			this.frame,
    			this.position,
    			this.acquisitionId);
    }
}
