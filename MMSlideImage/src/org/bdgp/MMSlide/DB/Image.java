package org.bdgp.MMSlide.DB;

import org.json.JSONException;
import org.json.JSONObject;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Image {
    @DatabaseField(generatedId=true, canBeNull=false) private int id;
    @DatabaseField(canBeNull=false) private int slideId;
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING) private String path;
    @DatabaseField(canBeNull=false) private int slidePosId;
    @DatabaseField() private String tags;
    
    public Image() {}
    public Image (int slideId, int slidePosId, String path) {
        this.slideId = slideId;
        this.slidePosId = slidePosId;
        this.path = path;
    }
    public Image (int slideId, int slidePosId, String path, String tags) {
        this.slideId = slideId;
        this.slidePosId = slidePosId;
        this.path = path;
        this.tags = tags;
    }
    public Image (int slideId, int slidePosId, String path, JSONObject tags) {
        this.slideId = slideId;
        this.slidePosId = slidePosId;
        this.path = path;
        this.tags = tags.toString();
    }
    public int getId() {return this.id;}
    public String getName() {return String.format("I%05d", this.id);}
    public int getSlideId() {return this.slideId;}
    public int getSlidePosId() {return this.slidePosId;}
    public String getPath() {return this.path;}
    public JSONObject getTags() {
    	try { return new JSONObject(this.tags); } 
    	catch (JSONException e) { throw new RuntimeException(e); }
    }
    public String getTagsAsString() { return this.tags; }
}
