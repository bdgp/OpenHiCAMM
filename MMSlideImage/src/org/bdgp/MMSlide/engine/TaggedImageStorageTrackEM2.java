package org.bdgp.MMSlide.engine;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import org.json.JSONException;
import org.json.JSONObject;
// import org.micromanager.acquisition.TaggedImageStorageDiskDefault;
import org.micromanager.api.AcquisitionEngine;
import org.micromanager.utils.ReportingUtils;

public class TaggedImageStorageTrackEM2 extends TaggedImageStorageDiskDefault {

	final private String TrackEM2Fn_ = "trackem.txt";
	final private int MaxQueue_ = 3;
	
	private String dir_;
	private boolean createdTEMstream_ = false;
	private Writer trackEM2stream_ = null;
	
	public TaggedImageStorageTrackEM2(String dir, boolean newDataSet,
	           JSONObject summaryMetadata) {
		super(dir, newDataSet, summaryMetadata);
		
		dir_ = dir;
	}

	protected void saveImageFile(Object img, JSONObject md, String path, String tiffFileName) {
		super.saveImageFile(img, md, path, tiffFileName);
				
		try {
			if ( trackEM2stream_ == null ) {
				// make sure we try creating the file only once per run
				if ( createdTEMstream_ == false ) {
					createdTEMstream_ = true;
					trackEM2stream_ = new BufferedWriter(new FileWriter(dir_ + "/" + TrackEM2Fn_));
				}
			} 
			if ( trackEM2stream_ != null ) {
				String stageName;
				int x, y, z;
				
				trackEM2stream_.write(path + "/" + tiffFileName + "\t");
				
				try {
					stageName = md.getString("Core-XYStage");
					x = md.getInt("Acquisition-"+stageName+"RequestedXPosition");
					y = md.getInt("Acquisition-"+stageName+"RequestedYPosition");
					z = 0;
					trackEM2stream_.write(x + "\t" + y + "\t" + z + "\n");

				} catch (JSONException e) {
					// TODO Auto-generated catch block
					// e.printStackTrace();
					ReportingUtils.logError(e);
					trackEM2stream_.write("ERROR - no xyz data\n");
				}
								
				trackEM2stream_.flush();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ReportingUtils.logError(e);
			trackEM2stream_ = null; 
		}
		
	}

	
	public void finished() {
		super.finished();
		try {
			trackEM2stream_.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
