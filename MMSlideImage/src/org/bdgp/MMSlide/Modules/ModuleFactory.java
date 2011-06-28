package org.bdgp.MMSlide.Modules;

import java.lang.reflect.Constructor;
import java.util.HashMap;

import org.bdgp.MMSlide.SlideStorage;

public class ModuleFactory {

	// New module - fill into following line
	protected String [] availableModules = new String[] {"Slide", "SlidePool", "ProcessImageROI", "SlideAssembleTrakEM2"};

	protected HashMap<String,String> mappedModules = new HashMap<String,String>();
	String [] labelModules;
	
	public ModuleFactory() {
		init(true);
	}
	
	
	public ModuleFactory(boolean instantiate) {
		init( instantiate ); 
	}
	
	
	protected void init(boolean instantiate) {
		
		if ( instantiate ) {
			labelModules = new String[availableModules.length];
			parseClasses();			
		}
	}
	
	
	protected String packageName() {
		String packageName = null;
		
		//
		// Create an instance of our class and again get its package name
		//
		ModuleFactory mf = new ModuleFactory(false);
		packageName = mf.getClass().getPackage().getName();
		return packageName;
	}
	
	protected void parseClasses() {
		
		int ct = 0;
		
		for ( String mod : availableModules ) {
			try {
				// Class cl = Class.forName("org.bdgp.MMSlide.Modules." + mod);
				Class cl = Class.forName(packageName() + "." + mod);
				Constructor con = cl.getConstructor(SlideStorage.class);
				Object moduleFromString = con.newInstance((SlideStorage) null);
				
				if ( moduleFromString instanceof ModuleBase ) {
					ModuleBase cmod =  (ModuleBase) moduleFromString;
					mappedModules.put(cmod.getLabel(), mod); 
					labelModules[ct++] = cmod.getLabel();
				} else {
					throw new Exception("Programming error: Module not of type ModuleBase");
				}
				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}		
			
		}
	}
	
	
	public String [] labels() {
		return labelModules;
	}
	
	 
	public ModuleBase makeModule(String label, SlideStorage stor) {
		
		String className = mappedModules.get(label);
		if ( className == null ) {
			return null;
		}
		
		try {
			Class cl = Class.forName(packageName() + "." + className);
			Constructor con = cl.getConstructor(SlideStorage.class);
			Object moduleFromString = con.newInstance(stor);
			
			if ( moduleFromString instanceof ModuleBase ) {
				return (ModuleBase) moduleFromString;
			} else {
				throw new Exception("Programming error: Module not of type ModuleBase");
			}
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
				
		return null;
	}
}
