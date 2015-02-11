package org.bdgp.OpenHiCAMM;

public class ValidationError {
    private String moduleId;
    private String message;
    
    public ValidationError(String moduleId, String message) {
        this.moduleId = moduleId;
        this.message = message;
    }
    
    public String getModuleId() {
        return this.moduleId;
    }
    public String getMessage() {
        return this.message;
    }
}
