package org.bdgp.OpenHiCAMM.DB;

import org.bdgp.OpenHiCAMM.Dao;
import org.bdgp.OpenHiCAMM.WorkflowRunner;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.internal.DefaultCoords;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import ij.ImagePlus;
import ij.process.ImageProcessor;

import static org.bdgp.OpenHiCAMM.Util.where;

import java.io.IOException;
import java.nio.file.Paths;

@DatabaseTable
public class Image {
    @DatabaseField(generatedId=true, canBeNull=false, allowGeneratedIdInsert=true) private int id;
    @DatabaseField(canBeNull=false) private int slideId;
    @DatabaseField(canBeNull=false) private int acquisitionId;
    @DatabaseField(canBeNull=false) private int channel;
    @DatabaseField(canBeNull=false) private int slice;
    @DatabaseField(canBeNull=false) private int frame;
    @DatabaseField(canBeNull=false) private int position;
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
    
    public org.micromanager.data.Image getImage(Datastore datastore) {
    	try {
			return datastore.getImage(new DefaultCoords.Builder().
					channel(this.channel).
					zSlice(this.slice).
					timePoint(this.frame).
					stagePosition(this.position).build());
		} catch (IOException e) {throw new RuntimeException(e);}
    }

    public org.micromanager.data.Image getImage(WorkflowRunner runner) {
        Dao<Acquisition> acqDao = runner.getWorkflowDb().table(Acquisition.class);
        if (this.acquisitionId < 1) {
            throw new RuntimeException("acquisitionId is not set!");
        }
        // Initialize the acquisition
        Acquisition acquisition = acqDao.selectOneOrDie(where("id",this.getAcquisitionId()));
        Datastore datastore = acquisition.getDatastore();

        // Get the image cache object
        if (datastore == null) throw new RuntimeException("Acquisition was not initialized; datastore is null!");
        // Get the tagged image from the image cache
        org.micromanager.data.Image image = this.getImage(datastore);
        if (image == null) throw new RuntimeException(String.format("Acqusition %s, Image %s is not in the image cache!",
                acquisition, this));
        return image;
    }
    
    public ImagePlus getImagePlus(WorkflowRunner runner) {
        if (this.acquisitionId == 0) {
            if (this.getPath() == null) throw new RuntimeException("getPath() is null!");
            String imagePath = Paths.get(runner.getWorkflowDir().getPath()).resolve(this.getPath()).toString();
            ImagePlus img = new ImagePlus(imagePath);
            return img;
        }
        org.micromanager.data.Image mmimage = this.getImage(runner);

        ImageProcessor processor = runner.getOpenHiCAMM().getApp().getDataManager().getImageJConverter().createProcessor(mmimage);
        ImagePlus imp = new ImagePlus(this.toString(), processor);
        return imp;
    }
    
    public String getLabel() {
    	return Image.generateLabel(this.channel, this.slice, this.frame, this.position);
    }

    public static String generateLabel(int channel, int slice, int frame, int position) {
        return String.format("%s_%s_%s_%s", channel, slice, frame, position);
    }
    
    public static String generateLabel(Coords coords) {
        return String.format("%s_%s_%s_%s", coords.getChannel(), coords.getZSlice(), coords.getTimePoint(), coords.getStagePosition());
    }
    
    public static int[] getIndices(String label) {
    	String[] split = label.split("_");
    	return new int[] {
    		Integer.parseInt(split[0]),
    		Integer.parseInt(split[1]),
    		Integer.parseInt(split[2]),
    		Integer.parseInt(split[3]),
    	};
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
