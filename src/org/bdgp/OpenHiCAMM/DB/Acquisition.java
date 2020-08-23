package org.bdgp.OpenHiCAMM.DB;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bdgp.OpenHiCAMM.Dao;
import org.bdgp.OpenHiCAMM.Util;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.internal.MMStudio;
import org.micromanager.acquisition.internal.MMAcquisition;
import org.micromanager.internal.utils.MMScriptException;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Acquisition {
    @DatabaseField(generatedId=true, canBeNull=false,allowGeneratedIdInsert=true) private int id;
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING) private String name;
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING) private String prefix;
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING) private String directory;
    
    private static Map<String,MMAcquisition> acquisitionCache = new ConcurrentHashMap<>();
    
    public static void clearCache() {
        acquisitionCache.clear();
    }
    
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
        File acquisitionPath = new File(this.directory, this.prefix);
        if (acquisitionCache.containsKey(acquisitionPath.getPath())) {
            return acquisitionCache.get(acquisitionPath.getPath());
        }
        if (existing) {
            MMStudio mmstudio = MMStudio.getInstance();
            synchronized (mmstudio) {
                try {
                    for (String acqName : mmstudio.getAcquisitionNames()) {
                        MMAcquisition acquisition = mmstudio.getAcquisitionWithName(acqName);
                        JSONObject summaryMetadata = acquisition.getImageCache().getSummaryMetadata();
                        if (summaryMetadata != null &&
                            summaryMetadata.has("Directory") && 
                            summaryMetadata.get("Directory").toString().equals(this.directory) &&
                            summaryMetadata.has("Prefix") &&
                            summaryMetadata.get("Prefix").toString().equals(this.prefix))
                        {
                            this.name = acqName;
                            acquisitionCache.put(acquisitionPath.getPath(), acquisition);
                            return acquisition;
                        }
                    }
                } 
                catch (JSONException e) {throw new RuntimeException(e);}
                catch (MMScriptException e) { 
                    // this means there was no acquisition loaded with this name, that's OK. Let's try to 
                    // open the acquisition next...
                } 

                try {
                    if (!acquisitionPath.exists()) throw new RuntimeException(String.format(
                            "Acquisition path %s does not exist!", acquisitionPath.getPath()));
                    this.name = mmstudio.openAcquisitionData(acquisitionPath.getPath(), false, false);
                    MMAcquisition acquisition = mmstudio.getAcquisitionWithName(this.name);
                    if (acquisitionDao != null) {
                        acquisitionDao.update(this, "id");
                    }
                    acquisitionCache.put(acquisitionPath.getPath(), acquisition);
                    return acquisition;
                } 
                catch (MMScriptException e1) {
                    StringWriter sw = new StringWriter();
                    e1.printStackTrace(new PrintWriter(sw));
                    throw new RuntimeException(String.format("Could not open acquisition %s:\n%s", this, sw));
                }
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
                acquisitionCache.put(acquisitionPath.getPath(), acquisition);
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
