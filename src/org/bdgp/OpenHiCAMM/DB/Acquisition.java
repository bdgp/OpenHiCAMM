package org.bdgp.OpenHiCAMM.DB;

import java.io.File;

import org.bdgp.OpenHiCAMM.Dao;
import org.bdgp.OpenHiCAMM.Util;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudio;
import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.utils.MMScriptException;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Acquisition {
    @DatabaseField(generatedId=true, canBeNull=false) private int id;
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING) private String name;
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING) private String prefix;
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING) private String directory;
    
    public Acquisition() {}
    public Acquisition (String name, String prefix, String directory) {
        this.name = name;
        this.prefix = prefix;
        this.directory = directory;
    }
    public int getId() {return this.id;}
    public String getName() {return this.name;}
    public String getPrefix() {return this.prefix;}
    public String getDirectory() { return this.directory; }

    public MMAcquisition getAcquisition(Dao<Acquisition> acquisitionDao) {
        return this.getAcquisition(acquisitionDao, true);
    }

    public MMAcquisition getAcquisition(Dao<Acquisition> acquisitionDao, boolean existing) {
        if (existing) {
            MMStudio mmstudio = MMStudio.getInstance();
            synchronized (mmstudio) {
                try {
                    MMAcquisition acquisition = mmstudio.getAcquisitionWithName(this.name);
                    JSONObject summaryMetadata = acquisition.getImageCache().getSummaryMetadata();
                    if (summaryMetadata != null &&
                        summaryMetadata.has("Directory") && 
                        summaryMetadata.get("Directory").toString().equals(this.directory) &&
                        summaryMetadata.has("Prefix") &&
                        summaryMetadata.get("Prefix").toString().equals(this.prefix))
                    {
                        return acquisition;
                    }
                } 
                catch (JSONException e) {throw new RuntimeException(e);}
                catch (MMScriptException e) { 
                    // this means there was no acquisition loaded with this name, that's OK. Let's try to 
                    // open the acquisition next...
                } 

                try {
                    File acquisitionPath = new File(this.directory, this.prefix);
                    if (!acquisitionPath.exists()) throw new RuntimeException(String.format(
                            "Acquisition path %s does not exist!", acquisitionPath.getPath()));
                    this.name = mmstudio.openAcquisitionData(acquisitionPath.getPath(), false, false);
                    MMAcquisition acquisition = mmstudio.getAcquisitionWithName(this.name);
                    if (acquisitionDao != null) {
                        acquisitionDao.update(this, "id");
                    }
                    return acquisition;
                } 
                catch (MMScriptException e1) {throw new RuntimeException(e1);}
            }
        }
        else {
            try { 
                MMAcquisition acquisition = new MMAcquisition(this.name, this.directory, false, true, false); 
                acquisition.initialize();
                // update the prefix metadata
                JSONObject summaryMetadata = acquisition.getSummaryMetadata();
                if (summaryMetadata != null && summaryMetadata.has("Prefix")) {
                    this.prefix = summaryMetadata.get("Prefix").toString();
                    if (acquisitionDao != null) {
                        acquisitionDao.update(this, "id");
                    }
                }
                return acquisition;
            } 
            catch (JSONException e) {throw new RuntimeException(e);}
            catch (MMScriptException e) {throw new RuntimeException(e);}
        }
    }
    
    public MMAcquisition getAcquisition() {
        return this.getAcquisition(null);
    }
    
    public String toString() {
    	return String.format("%s(id=%d, name=%s, prefix=%s, directory=%s)",
    			this.getClass().getSimpleName(), 
    			this.id, 
    			Util.escape(this.name), 
    			Util.escape(this.prefix), 
    			Util.escape(this.directory));
    }
}
