package org.bdgp.MMSlide;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOError;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Vector;

import org.bdgp.MMSlide.Modules.ModuleBase;

public class MetaStorage {

	final static String STORAGE_DIR = "Metadata";
	final static String STORAGE_CHILD = "child_workers";
	final static String STORAGE_DATA = "child_storage";
	final static String STORAGE_INFO = "info";
	final static String STORAGE_TASK = "tasks";
	final static String STORAGE_TASK_STATUS = "tasks_status";
	
	final static String TASK_OK = "OK";
	final static String TASK_FAIL = "fail";
	
	final static int FIRST_NUMBER = 1;
	
	public enum MetaType {
		UNDEFINED, POOL, SLIDE, IMAGE, ROI  
	}
	
	String location;
	String meta_stor;
	
	ModuleInfo info = null;
	
	Vector<ChildStorage> child_data = null;
	// HashMap<ModuleBase, Integer> managed_modules = null;
	HashMap<String, Integer> managed_children = null;
	
	FileLineStorage<ChildStorage> child_storage;
	FileLineStorage<ChildWorker> child_workers;
	FileLineStorage<TaskStorage> task_storage = null;
	FileLineStorage<TaskStorage> task_status_storage;
	
	int task_id = 0;
	
	// Right now a file based storage - could become SQL based in future
	
	
	public MetaStorage(){
		
	}

	public MetaStorage(ModuleInfo inf){
		// init(inf);
	}

	
	public static String [] info(String dir) {
		return null;
	}
	
	
	// gets data from mb and (optional) user entered description
	public void init() {
	}
	
	
	public void initStorage(String location) throws IOException {
		this.location = location;
		
		// TODO: add number to STORAGE_DIR to avoid conflicts of several modules of same type
		// setUnique triggers this number behavior: 
		// 1st instance of pool in flow = #1
		// 2nd instance of pool in flow = #2 ,etc...
		// Actually, do we need that? tasks will be safely added to tasklist file, children too. 
		File tdir = new File(location, STORAGE_DIR);
		if ( ! tdir.exists() ) {
			tdir.mkdir();
		}
		if ( ! tdir.isDirectory() ) {
			throw new IOException("Meta-storage " + STORAGE_DIR + " exists and is not a directory");
		}
		
		meta_stor = tdir.getAbsolutePath();
		
		managed_children = new HashMap<String, Integer>();
		child_workers = new FileLineStorage<ChildWorker>(ChildWorker.class, meta_stor, STORAGE_CHILD);
		child_storage = new FileLineStorage<ChildStorage>(ChildStorage.class, meta_stor, STORAGE_DATA);
	}
	
	// Add a child storage location
	// Called every time by SlideStorage when a module requests a storage location
	// This way the parent knows where all the children's outputs are located
	// Keeps separate track of several identical children too
	// TODO!!!!!
	public void childAdd(String child_loc, ModuleBase child_mod) throws IOException {
		// keeping track of the child_mod in hash - change number if it's different (several child mod's)
		// NO GOOD - should be based on sig and not on workflow (otherwise we won't be able to run workflow twice)
		if ( ! managed_children.containsKey(child_mod.signature())) {
			int ct = FIRST_NUMBER;
//			for ( ModuleBase mod : managed_children.keySet()) {
//				if ( mod.getClass().getName().equals(child_mod)) {
//					ct++;
//				}
//			}
			managed_children.put(child_mod.signature(), new Integer(ct));
		}
		child_storage.add(new ChildStorage(child_mod.getClass().getName(), managed_children.get(child_mod), child_loc));
	}
		
	
	// The following task...() allow for (optional) management of module tasks
	// If found, can be used by SlideStorage.get() to resume at correct position
	// TODO: (??, see above) There could be several modules of same type (e.g. 2x SlidePool)
	
	public void taskAdd(String id) throws IOException {
		
		if ( task_storage == null ) {
			task_storage = new FileLineStorage<TaskStorage>(TaskStorage.class, meta_stor, STORAGE_TASK);
			task_status_storage = new FileLineStorage<TaskStorage>(TaskStorage.class, meta_stor, STORAGE_TASK_STATUS);
			task_status_storage.setCache(true);
		}
		
		task_storage.add(new TaskStorage(id));
	}
	
	
	public void taskFinish(String id, boolean ok) throws Exception  {
		if ( ok == true ) {
			task_status_storage.add(new TaskStorage(id, TASK_OK));
		} else {
			task_status_storage.add(new TaskStorage(id, TASK_FAIL));			
		}
	}
	
	public boolean taskStatus(String id) throws Exception {
		TaskStorage task = task_status_storage.find(id);
		if (task.status.equals(TASK_OK)) {
			return true;
		}
		return false;
	}
	
	// Returns true if task management storage exists
	public boolean taskExists() {
		return new FileLineStorage<TaskStorage>(TaskStorage.class, meta_stor, STORAGE_TASK_STATUS).exists();
	}
	
	
	public void readChildData() throws IOException {
		child_data = child_storage.read();
	}
	
	
	public String nextChild(ModuleBase child_mod) {
		return null;
	}
	
	
	public void register(ModuleBase child, MetaStorage childMeta, MetaType type) {
		
	}
	
	
	public Vector<MetaType> children() {
		// recursive 
		return null;
	}
	
	
	protected void readWorkers() {
		
	}
	
			
	
	protected class ChildStorage implements LineItem {
		public String class_name;
		public int class_instance; // Number of caller when it is used.
		public String location;
		
		public ChildStorage() {
			class_name = null;
			class_instance = 0;
			location = null;
		}
		
		public ChildStorage(String name, int instance, String loc) {
			class_name = name;
			class_instance = instance;
			location = loc;
			
		}
		
		public String [] toTokens() {
			String [] t = new String[3];
			t[0] = class_name;
			t[1] = Integer.toString(class_instance);
			t[2] = location;			
			return t;
		}
		
		public void fromTokens(String [] line) {
			if ( line.length < 3 ) { return; }
			class_name = line[0];
			class_instance = Integer.parseInt(line[1]);
			location = line[2];			
		}
		
		public String key() {
			return class_name;
		}
		
	}
	
	
	protected class ChildWorker implements LineItem {
		public String class_name = null;
		public MetaType type;
		public String signature;
		public int class_instance; // maps to ChildStorage class_instance
		public MetaStorage metastor;
		
		public ChildWorker() {
			
		}
		
		public ChildWorker(String name, MetaType type, MetaStorage stor ) {
			class_name = name;
			this.type = type;
			metastor = stor; 
		}
		
		public String [] toTokens() {
			String [] t = new String[2];
			t[0] = class_name;
			// TODO t[1] = type;
			return t;
		}
		
		public void fromTokens(String [] line) {
			if ( line.length < 2 ) { return; }
			class_name = line[0];
		}
		
		public String key() {
			return signature;
		}
		

	}
	
	
	protected class TaskStorage implements LineItem {
		public String id = null;
		public int instance = -1;
		public String status = null;
		
		public TaskStorage() {}
		
		public TaskStorage(String id) {
			this.id = id;
		}

		public TaskStorage(String id, String status) {
			this.id = id;
			this.status = status;
		}

		public String [] toTokens() {
			if ( status == null ) {
				String [] t = new String[1];
				t[0] = id;
			}
			String [] t = new String[2];
			t[0] = id;
			t[1] = status;
			return t;
		}
		
		public void fromTokens(String [] line) {
			switch (line.length) {
			case 0:
				return;
			case 2:
				status = line[1];
			case 1:
				id = line[0];
			}
		}
		public String key() {
			return id;
		}
		
	}
	
}
