package org.bdgp.MMSlide;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.bdgp.MMSlide.Modules.ModuleBase;

// This class encapsulates Preferences for each module and can be used as such
// Better to use java.util.Preferences for that purpose, though
// This class is mainly used to specify the information that make a module unique in the flow
// If the flow is constructed again, it can figure out if this has been done already or not using this info. 

public class ModuleInfo {

	FileLineStorage<ModuleData> moduleInfo; 
	final static String DESCRIPTION = "Description";
	
	public ModuleInfo(ModuleBase mb) {
		moduleInfo = new FileLineStorage<ModuleData>(ModuleData.class);
	}
	
	public void description() {
		
	}
	
	public void putParam(boolean essential, String key, String value) {
		
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
		
		public ModuleData(String k, String v) {
			key = k;
			value = v;
		}

		public String [] toTokens() {
			String [] t = new String[2];
			t[0] = key;
			t[1] = value;
			return t;
		}
		
		public void fromTokens(String [] line) {
			key = line[0];
			value = line[1];
		}

		public String key() {
			return key;
		}
	}

}
