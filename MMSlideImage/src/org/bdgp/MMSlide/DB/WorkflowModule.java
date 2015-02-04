package org.bdgp.MMSlide.DB;

import javax.swing.tree.DefaultMutableTreeNode;

import org.bdgp.MMSlide.Util;
import org.bdgp.MMSlide.Modules.Interfaces.Module;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
 * Class for defining workflows. Each step has a unique String ID, 
 * a module to execute, and the ID of the parent step. Also check
 * that the given module name is valid.
 */
@SuppressWarnings("serial")
@DatabaseTable
public class WorkflowModule extends DefaultMutableTreeNode {
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING,useGetSet=true)
    private String id;
    @DatabaseField(canBeNull=false,useGetSet=true,dataType=DataType.LONG_STRING) 
    private String moduleName;
    @DatabaseField(dataType=DataType.LONG_STRING)
    private String parentId;
    
    private Class<Module> module;
    
    public WorkflowModule() {
        super();
    }
    public WorkflowModule(String id, String moduleName, String parentId) {
        super(id);
        this.id = id;
        setModuleName(moduleName);
        this.parentId = parentId;
    }
    
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
        setUserObject(this.id);
    }
    public String getModuleName() {
        return moduleName;
    }

    @SuppressWarnings("unchecked")
    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
        
        // assign the module class
        try {
            Class<?> module = Class.forName(moduleName);
            if (!Module.class.isAssignableFrom(module)) {
                throw new RuntimeException("Module "+moduleName
                        +" does not inherit Module interface.");
            }
            this.module = Module.class.getClass().cast(module);
        } 
        catch (ClassNotFoundException e) { 
            throw new RuntimeException("Module "+moduleName+" is an unknown module.", e);
        }
    }
    
    public Class<Module> getModule() {
        return module;
    }
    public String getParentId() {
        return parentId;
    }
    
    public String toString(boolean all) {
    	if (!all) return this.toString();
    	return String.format("%s(id=%s, moduleName=%s, parentId=%s)",
    			this.getClass().getSimpleName(),
    			Util.escape(this.id), 
    			Util.escape(this.moduleName), 
    			Util.escape(this.parentId));
    }
}
