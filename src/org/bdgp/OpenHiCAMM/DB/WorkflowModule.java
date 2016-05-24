package org.bdgp.OpenHiCAMM.DB;

import org.bdgp.OpenHiCAMM.Util;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Module;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
 * Class for defining workflows. Each step has a unique String ID, 
 * a module to execute, and the ID of the parent step. Also check
 * that the given module name is valid.
 */
@DatabaseTable
public class WorkflowModule {
    @DatabaseField(generatedId=true, canBeNull=false)
    private int id;
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING,useGetSet=true,unique=true)
    private String name;
    @DatabaseField(canBeNull=false,useGetSet=true,dataType=DataType.LONG_STRING) 
    private String className;
    @DatabaseField()
    private Integer parentId;
    @DatabaseField(canBeNull=false)
    private Integer priority;
    
    private Class<Module> module;
    
    public WorkflowModule() {
    }
    public WorkflowModule(String name, String className, Integer parentId) {
        this.name = name;
        setClassName(className);
        this.parentId = parentId;
    }
    
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getClassName() {
        return className;
    }

    @SuppressWarnings("unchecked")
    public void setClassName(String className) {
        this.className = className;
        
        // assign the module class
        try {
            Class<?> module = Class.forName(className);
            if (!Module.class.isAssignableFrom(module)) {
                throw new RuntimeException("Module "+className
                        +" does not inherit Module interface.");
            }
            this.module = Module.class.getClass().cast(module);
        } 
        catch (ClassNotFoundException e) { 
            throw new RuntimeException("Module "+className+" is an unknown module.", e);
        }
    }
    
    public Class<Module> getModule() {
        return module;
    }

    public Integer getParentId() {
        return parentId;
    }
    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }

    public Integer getPriority() {
        return this.priority;
    }
    public void setPriority(Integer priority) {
        this.priority = priority;
    }
    
    public String toString() {
    	return String.format("%s(id=%d, name=%s, className=%s, parentId=%s)",
    			this.getClass().getSimpleName(),
    			this.id,
    			Util.escape(this.name), 
    			Util.escape(this.className), 
    			Util.escape(this.parentId));
    }
}
