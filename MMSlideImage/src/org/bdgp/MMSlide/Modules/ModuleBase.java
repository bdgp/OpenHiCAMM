package org.bdgp.MMSlide.Modules;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Vector;

import javax.swing.JDialog;

import org.bdgp.MMSlide.FileLineStorage;
import org.bdgp.MMSlide.LineItem;
import org.bdgp.MMSlide.MetaStorage;
import org.bdgp.MMSlide.StorageManager;
import org.bdgp.MMSlide.ModuleInfo.ModuleData;

public abstract class ModuleBase extends JDialog {

	protected StorageManager storage;
//	protected ModuleInfo info;
	protected String moduleLabel = "unknown";
	protected String moduleText = "unknown";
	public String storageLong = "unknown";
	public String storageShort = new String(); // defaults to empty - we don't need more subdivisions if kept that way
	public MetaStorage.MetaType type_ = MetaStorage.MetaType.UNDEFINED;
	
	FileLineStorage<ModuleData> moduleInfo = null; 

	protected Vector<ModuleBase> child_workers;
	
	public ModuleBase(StorageManager storage) {
		this.storage = storage;
		moduleInfo = new FileLineStorage<ModuleData>(ModuleData.class);
		// moduleInfo.add(new ModuleData(true, "", "")); // add class name as first signature parameter
//		info = new ModuleInfo(this);
		// storage.setInfo(info);
	}
	
	// Initialization after storage
	public void init() throws Exception {
		
	}
	
	public String getLabel() {
		return moduleLabel;
	}
	
	public String getText() {
		return moduleText;
	}
	
	
	public void addSuccessor(ModuleBase mod) {
		if ( compatibleSuccessor(mod) ) {
			child_workers.add(mod);
			storage.register(this, mod);
		}
	}
	
	public int countSuccessors() {
		return child_workers.size();
	}
	
	public void rmSucessors() {
		if ( child_workers.size() == 0 ) {
			return;
		}
		
		for ( ModuleBase cw : child_workers ) {
			cw.rmSucessors();
			cw.destroy();
			storage.destroy(cw);
			child_workers.remove(cw);
		}
	}
	
	public void destroy() {
		
	}

	// If something needs to be done before successor is removed
	public void rmSuccessor(ModuleBase mod) {
	}
	
	
	//
	// Abstract classes

	// Test the module
	public abstract void test();
	// What successors are compatible
	public abstract boolean compatibleSuccessor(ModuleBase mod);
	// Configure everything, optional pass configurations to it
	public abstract void configure(HashMap<String, String> options);
	// Save data-independent config from disk
	public abstract void confSave();
	// Load data-independent config from disk
	public abstract void confLoad();
	
	public String description() {
		return null;
	}
	
	public void putParam(boolean essential, String key, String value) {
		if ( moduleInfo == null ) {
			moduleInfo = new FileLineStorage<ModuleData>(ModuleData.class);
		}
		try {
			moduleInfo.add(new ModuleData(essential,key,value));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public boolean getParam(String key, String value) {
		return false; 
	}
	
	
	// Returns a signature string to identify "uniqueness" 
	public String signature() {
		byte[] digest = null;
		String plaintext = "your text here";
		MessageDigest m;
		try {
			m = MessageDigest.getInstance("MD5");
			m.reset();
			m.update(plaintext.getBytes());
			digest = m.digest();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		BigInteger bigInt = new BigInteger(1,digest);
		String hashtext = bigInt.toString(16);
		// Now we need to zero pad it if you actually want the full 32 chars.
		while(hashtext.length() < 32 ){
		  hashtext = "0"+hashtext;
		}
		
		return null; 
	}
	
	public void toFile() {
		
	}
	
	
	public class ModuleData implements LineItem {
		public boolean essential;
		public String key;
		public String value;
		
		public ModuleData() {}
		
		public ModuleData(boolean essential, String k, String v) {
			this.key = k;
			this.value = v;
			this.essential = essential; 
		}

		public String [] toTokens() {
			String [] t = new String[3];
			t[0] = key;
			t[1] = value;
			t[2] = essential == true ? "1" : "0";
			return t;
		}
		
		public void fromTokens(String [] line) {
			key = line[0];
			value = line[1];
			essential = line[2].equals("1") ? true : false;
		}

		public String key() {
			return key;
		}
	}

	
}
