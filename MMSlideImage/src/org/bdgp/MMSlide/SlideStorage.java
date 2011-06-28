package org.bdgp.MMSlide;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import org.bdgp.MMSlide.Modules.ModuleBase;

public class SlideStorage {
	
	protected final static String CONFIG_DIR = "configuration";
	protected final static String STORAGE_DIR = "data";
	
	protected String baseLocation;
	protected String configDir;
	protected boolean setConf = false;
	public enum WorkStatus { NEW, RESUME, REDO, PROCESS };
	public enum CollectionType { SIBLINGS, CHILDREN, PARENT };
	protected WorkStatus work = WorkStatus.NEW;
	
	HashMap<ModuleBase, StorageLocation> locMap;
	HashMap<ModuleBase, String> confMap;
	Vector<ModuleBase> uniqueList;
	
	public SlideStorage() {
		baseLocation = null;
		configDir = null;
		locMap = new HashMap<ModuleBase, StorageLocation>();
		confMap = new HashMap<ModuleBase, String>();
	}

	public SlideStorage(String location) {
		baseLocation = location;
		// Adjust config in directory hierarchy
		if ( setConf == false ) {
			configDir = new String(baseLocation);
			configDir.concat(CONFIG_DIR);
		}
	}
	
	public void setLocation(String location) {
		baseLocation = location;
	}
	
	// config should have two locations:
	// 1) the directory where the whole thing ran
	// 2) a user selectable custom directory
	
	public void setConfig(String location) {
		configDir = location;
		setConf = true;
	}

	
	public void setTempConfig(String location) {
		configDir = location;
	}

	public void unsetTempConfig() {
		
	}
	
	
	public String getConfig(ModuleBase mod) {
		File cd = new File(configDir, mod.storageLong);
		if ( ! cd.exists() ) {
			cd.mkdirs();
		}
		if ( ! cd.isDirectory() ) {
			return null;
		}
		return configDir;
	}
	
	// Initializes storage locations. creates base directories
	// returns false if anything fail
	public boolean initStorage() {

		// Iterate through long locations and mkDir (if not exists)
		// At each long location, if exists, find maxEntry, set currentEntry to max+1

		HashSet<ModuleBase> initialized = new HashSet<ModuleBase>();
		
		// find all modules with null parent 
		// init
		// repeat {
		// find all modules with already init parent
		// init
		// } until init-size == hashmap-size
		
		for ( ModuleBase mb : locMap.keySet()) {
			StorageLocation loc = locMap.get(mb);
			if ( loc.parent == null ) {
				// init
				initDir(mb, initialized);
				initialized.add(mb);
			}
		}
		
		while ( initialized.size() < locMap.size() ) {
			int no = 0;
			
			for ( ModuleBase mb : locMap.keySet() ) {
				StorageLocation loc = locMap.get(mb);
				if ( loc.parent == null ) {
					continue;
				}
				if ( initialized.contains(loc.parent)) {
					// init
					initDir(mb, initialized);
					initialized.add(mb);
					no++;
				}
			}
			
			// Make sure every iteration does actually something
			if (no == 0) {
				break;
			}
			
		}
				
		// Prune for unique modules
		
		return false;
	}
	
	
	protected void initDir(ModuleBase mb, HashSet<ModuleBase> initialized ) {
		
		StorageLocation loc = locMap.get(mb);
		
		if ( loc.unique == true ) {
			String loc_class = mb.getClass().getName();
			
			for ( ModuleBase cmb : locMap.keySet()) {
				if ( cmb == mb ) {
					continue;
				}
				if ( loc_class.equals(cmb.getClass().getName()) ) {
					if ( initialized.contains(cmb)) {
						return;
					}
				}
			}
		}
		
		// find largest entry with loc.shortName wildcard
		String dir = dirString(loc, true, true);
		final String filter = loc.shortName;
		
		File df = new File(dir);
		String [] dlist = df.list( new FilenameFilter() {
			public boolean accept(File d, String name) { return name.startsWith(filter); }
		});
		
		int max = 0;
		for (int i=0; i < dlist.length; i++) {
			int cno = Integer.parseInt(dlist[i].substring(filter.length()));
			if ( cno > max ) { max = cno; }
		}
		
		loc.maxEntry = max;
		loc.currentEntry = max;
		
	}
	
	// public void setModifier(String mod)
	
	
	public void register(ModuleBase parent, ModuleBase child) {
		StorageLocation loc;
		
		// Check if child has unique storage location 
		// If yes, point hash-map to the previously initialized storage
		// setUnique is in constructor - thus already set at this point
		// this is not signature based - locations are sequential!!!
		for ( StorageLocation locU : locMap.values()) {
			if ( locU.unique == true && locU.moduleLabelName.equals(child.getLabel()) ) {
				locMap.put(child, locU);
				confMap.put(child, child.storageLong);
				return;
			}
		}
		
		if ( parent == null || locMap.containsKey(parent) == false ) {
			loc = new StorageLocation(null, child.getLabel(), child.storageShort, child.storageLong);
		} else {
			loc = new StorageLocation(locMap.get(parent), child.getLabel(), child.storageShort, child.storageLong);
		}
		loc.firstEntry = 0;
		loc.currentEntry = 0; // TODO: go to location and find current max
		loc.maxEntry = 0;
		locMap.put(child, loc);
		confMap.put(child, child.storageLong); // register configuration location
		if ( uniqueList.contains(child)) {
			loc.unique = true;
			loc.single = true;
		}
		loc.metaData = new MetaStorage();
		
		// TODO: Does child signature/metadata exist and set current accordingly
		
		// child.init();
	}
	
	
	public void destroy(ModuleBase mb) {
		// TODO: deletes storage if nothing is in there (i.e. get has never been called)
	}
	
	
	// Encapsule all the logic into get:
	// - create dir/return dir if new/redo/process
	// - check for unique/single entry
	public String get(ModuleBase current) {
		
		StorageLocation loc = locMap.get(current);
		String dir = "";
		
		try {
			
			switch (work) {
			case NEW:
				if ( loc.single == false ) {
					loc.currentEntry++;
					loc.maxEntry++;
				}
				dir = dirString(loc, true, false);
				break;
			case RESUME:
				// dir = loc.parent.metaData.next();
				// if exists, return null
				break;
			case REDO:
				// dir = loc.parent.metaData.next();
				// Clear previous storage
				break;
			case PROCESS:
				// dir = loc.parent.metaData.next();
				break;
			}
			
			if ( loc.parent != null ) {
				loc.parent.metaData.childAdd(dir,current);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return baseLocation + dir;
	}
	
	
	// Returns the current ID for modules wanting to keep own storage
	public long getID(ModuleBase current) {
		StorageLocation loc = locMap.get(current);
		
		return loc.currentEntry;
	}
	
	
	public void taskStorageContents(ModuleBase mod) {
		
	}	
	
	public boolean taskStatus(ModuleBase mod, String id) {
		return false;
	}
	
	public void taskStorageAdd(ModuleBase mod, String id) {
		
	}
	
	public void taskStorageFinish(ModuleBase mod, String id, boolean status) {
		
	}
	
		
	// Sets the storage for this module to a unique place if module is called again in same workflow
	// otherwise modules like SlidePool will get a new storage location every time. 
	public void setUnique(ModuleBase mod) {
		
		uniqueList.add(mod);
		
		// loc doesn't exist at this point - kaboom
		// StorageLocation loc = locMap.get(mod);
		// loc.unique = true;
		// loc.single = true;
	}
	
	// Sets the storage for this module to single&same dir foreach call of Module
	public void singleEntry(ModuleBase mod) {
		StorageLocation loc = locMap.get(mod);
		loc.single = true;		
	}
	
	
	protected String dirString(StorageLocation loc, boolean mkDir, boolean parentDir) {
		String dir = new String();
		StorageLocation loc_curr = loc;
		Vector<String> subdir = new Vector<String>();
		
		if (parentDir == true) {
			loc_curr = loc_curr.parent;
		}
		
		while ( loc_curr != null ) {
			if ( loc_curr.shortName.isEmpty() == false ) {
				subdir.add(loc_curr.shortName + "_" + loc_curr.currentEntry);
			}
			loc_curr = loc_curr.parent;
		}
		
		subdir.add(loc.longName);
		
		for ( int i = subdir.size() - 1; i >= 0; i-- ) {
			dir.concat(subdir.elementAt(i));
			if ( i > 0 ) { 
				dir.concat(File.separator);
			}
		}
		dir.concat(File.separator);
		dir.concat(STORAGE_DIR);
		if (mkDir == true) { makeDir(dir); }
	
		return dir;
	}
	
	
	protected void makeDir(String path){
		File sdir = new File(baseLocation + File.separator + path);
		if ( ! sdir.exists() ) {
			sdir.mkdirs();
		}
	}
	
	public StorageCollection getModuleStorage(ModuleBase mb, CollectionType typ) {
		return new StorageCollection(mb, typ);
	}
	
	public void setModuleStorage(ModuleBase mb, StorageCollection sc) {
		// gets user selection for sc and adjust everything accordingly
		// Using sc makes sure selection is valid
	}
	
	
	protected class StorageLocation {
		StorageLocation parent;
		MetaStorage metaData;
		String moduleLabelName;
		String shortName;
		String longName;
		String modifier = null;
		long firstEntry;
		long currentEntry;
		long maxEntry;
		boolean unique = false;
		boolean single = false; 
		
		public StorageLocation(StorageLocation parent, String cl_name, String shortName, String longName) {
			this.parent = parent;
			this.shortName = shortName;
			this.longName = longName;
			this.moduleLabelName = cl_name;
			metaData = new MetaStorage();
		}
	}
	
	
	// This subclass avoids giving the whole storage class to a UI class rather than
	// a specific sub-module that provides limited access.
	// This class is a lazy implementation to allow for the (potentially time consuming) query to take process
	// only if/when requested by UI module
	//
	// returns e.g. list of previously done things
	// Could be used to return list of children too
	// e.g. for SlidePool dialog of previous pools
	public class StorageCollection {
		protected ModuleBase module;
		protected String selected = null;
		protected HashMap<String, String> coll = null;
		protected CollectionType type;
		
		// Initialize but do not run queries yet
		public StorageCollection(ModuleBase mb, CollectionType typ) {
			this.module = mb;
			coll = new HashMap<String, String>();
			type = typ;
		}
		
		// Run queries
		public void update() {
			StorageLocation loc = locMap.get(module);
			String dir = null;
			// TODO: more sophisticated version reads metadata too
			
			switch (type) {
			case SIBLINGS:
				dir = dirString(loc, false, false);
				break;
			case PARENT:
			case CHILDREN:
				break;
			}
			
			if (dir == null) {
				return;
			}
			
			File df = new File(dir);
			String [] dl = df.list();
			
			for (int i=0; i<dl.length; i++ ) {
				coll.put(dl[i], dl[i]);
				// should be coll.put(dl[i], metadata);
			}

		}
		
		// Returns the values for public display
		public Collection<String> get() {
			// If queries not run, run them now
			if ( coll == null ) {
				update();
			}
			
			return coll.values();
		}
		
		
		public String match(String key) {
			return coll.get(key);
		}
		
		// Set to a (user defined) selection
		public void set(String key) {
			selected = key;
		}
		
	}
	
}
