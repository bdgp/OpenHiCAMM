package org.bdgp.MMSlide.Dao;

import javax.swing.tree.DefaultMutableTreeNode;

import org.bdgp.MMSlide.Modules.Interfaces.Module;

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
    @DatabaseField(id=true)
    private String id;
    @DatabaseField(canBeNull=false,useGetSet=true) 
    private String moduleName;
    @DatabaseField
    private int instanceCount;
    @DatabaseField
    private String parentId;
    
    private Class<Module<?>> module;
    
    public WorkflowModule() {
        super();
    }
    public WorkflowModule(String id, String moduleName, int instanceCount, String parentId) {
        super();
        this.id = id;
        setModuleName(moduleName);
        this.instanceCount = instanceCount;
        this.parentId = parentId;
        setUserObject(id + (instanceCount>1 ? " ("+instanceCount+")" : ""));
    }
    
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
        setUserObject(id + (instanceCount>1 ? " ("+instanceCount+")" : ""));
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
    
    public Class<Module<?>> getModule() {
        return module;
    }
    
    public int getInstanceCount() {
        return instanceCount;
    }
    public void setInstanceCount(int instanceCount) {
        this.instanceCount = instanceCount;
        setUserObject(id + (instanceCount>1 ? " ("+instanceCount+")" : ""));
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }
}
