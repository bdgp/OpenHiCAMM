package org.bdgp.MMSlide;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import org.bdgp.MMSlide.LineItem;

// Class using generics for maintaining data structs both in memory and on disk
// Each data struct of type LineItem must have tokenizers for its data and provide a key value for searches
// Optionally data can be cached in memory with a HashMap (setCache(true))
// Otherwise each struct is written to a File the minute it is added - this should help in case of a crash


public class FileLineStorage<L extends LineItem> implements Iterator {
	private Class<L> cl;
	protected static final String ext = ".txt";
	protected HashMap<String, L> kv_stor = null;
	boolean kv_cache = false;
	protected File f;
	
	final protected static String separator = "\t";
	final protected static String version = "V1.0";
	// TODO: First line = cl.getName() + version to validate
	
	public FileLineStorage(Class<L> cl) {
		this.cl = cl;
		kv_cache = true;
		kv_stor = new HashMap<String, L>();
		f = null;
	}

	public FileLineStorage(Class<L> cl, String dir, String file) {
		f = new File(dir, file + ext);
		this.cl = cl;
	}

	public boolean exists() {
		if ( f == null ) {
			return false;
		}
		return f.exists();
	}
	
	// Static utility function since we provide the extension inside this class
	public static boolean exists(String dir, String file) {
		File fs = new File(dir, file + ext);
		return fs.exists();
	}

	public void setCache(boolean cache) throws IOException {
		if ( f == null ) {
			return;
		}
		
		if ( kv_stor == null && cache == true ) {
			kv_stor = new HashMap<String, L>();
			Vector<L> v = read();
			for ( L item : v ) {
				kv_stor.put(item.key(), item);
			}
		}
		if ( kv_stor != null && cache == false ) {
			kv_stor.clear();
			kv_stor = null;
		}
		
		kv_cache = cache;
	}
	
	public void setFile(String dir, String file) throws IOException {
		f = new File(dir, file);

		if ( kv_stor != null ) {
			for ( L item : kv_stor.values() ) {
				add(item, true, false);
			}
		}
	}
	

	public void clear() {
		kv_stor.clear();
		kv_stor = null;
		if ( f != null ) {
			f.delete();
		}
	}
	
	public void add(L item) throws IOException {
		add(item, true, true);
	}
	
	protected void add(L item, boolean file, boolean cache) throws IOException {
		if ( f != null && file == true ) {
			PrintWriter dw = writeFile(true);
			String [] tokens = item.toTokens();
			String line = new String();
			for ( int i=0; i < tokens.length - 1; i++) {
				line.concat(tokens[i] + separator);
			}
			line.concat(tokens[tokens.length - 1]);
			dw.println(line);
			dw.close(); // close after each line so that multiple children can write
		}
		
		if ( kv_stor != null && cache == true ) {
			kv_stor.put(item.key(), item);
		}
	}
	
	protected PrintWriter writeFile(boolean append) throws IOException {
		PrintWriter out = new PrintWriter(new FileWriter(f), append);
		return out;
	}
	
	
	public L find(String key)  throws IOException {
		if ( kv_stor != null && kv_cache == true ) {
			return kv_stor.get(key);
		}
		
		L result;
		Vector<L> v = read();
		for ( L item : v ) {
			if ( key.equals(item.key()) ) {
				result = item;
				return result;
			}
		}
		
		return null;
	}
	
	
	
	
	protected Vector<L> read() throws IOException {
		if ( f == null ) {
			return null;
		}
		
		BufferedReader in = new BufferedReader(new FileReader(f));
		String line;
		Vector<L> data = new Vector<L>(10);
		while (( line = in.readLine() ) != null ) {
			L item;
			try {
				item = cl.newInstance();
				String [] sp = line.split(separator);
				item.fromTokens(sp);
				data.add(item);
			} catch (Exception e) {
				throw new IOException("Cannot initialize class");
			}
		}
		return data;
	}

	// Iteration methods
	
	public Collection<L> cachedValues() {
		if ( kv_stor == null ) {
			return null;
		}
		return kv_stor.values();		
	}
	
	
	public Iterator<L> iterator() {
		// TODO Right now only with cached
		if ( kv_stor == null ) {
			return null;
		}
		return this;
	}
	
	
	public boolean hasNext() {
		// TODO Auto-generated method stub
		return false;
	}

	public Object next() {
		// TODO Auto-generated method stub
		return null;
	}

	public void remove() {
		// TODO Auto-generated method stub
		
	}
}
