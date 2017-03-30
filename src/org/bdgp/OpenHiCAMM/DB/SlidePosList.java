package org.bdgp.OpenHiCAMM.DB;

import org.bdgp.OpenHiCAMM.Util;
import org.micromanager.api.PositionList;
import org.micromanager.utils.MMSerializationException;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class SlidePosList {
    @DatabaseField(generatedId=true, allowGeneratedIdInsert=true) 
    private int id;
    @DatabaseField(canBeNull=false,uniqueCombo=true) 
    private Integer moduleId;
    @DatabaseField(canBeNull=true,uniqueCombo=true) 
    private Integer taskId;
    @DatabaseField(canBeNull=false,uniqueCombo=true) 
    private Integer slideId;
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING,useGetSet=true) 
    private String posList;
    private PositionList positionList;

    public SlidePosList() {}
    public SlidePosList(Integer moduleId, Integer slideId, Integer taskId, PositionList positionList) {
    	this.moduleId = moduleId;
    	this.slideId = slideId;
    	this.taskId = taskId;
        setPositionList(positionList);
    }
    public SlidePosList(Integer moduleId, Integer slideId, Integer taskId, String posList) {
    	this.moduleId = moduleId;
    	this.slideId = slideId;
    	this.taskId = taskId;
        setPosList(posList);
    }
    public void setPositionList(PositionList positionList) {
        this.positionList = positionList;
        try { this.posList = positionList.serialize(); } 
        catch (MMSerializationException e) { throw new RuntimeException(e); }
    }
    public void setPosList(String posList) {
        this.positionList = new PositionList();
        try { this.positionList.restore(posList); } 
        catch (MMSerializationException e) { throw new RuntimeException(e); }
        this.posList = posList;
    }
    public int getId() { return this.id; }
    public Integer getModuleId() { return this.moduleId; }
    public Integer getTaskId() { return this.taskId; }
    public String getPosList() { return this.posList; }
    public PositionList getPositionList() { return this.positionList; }
    public Integer getSlideId() { return this.slideId; }
    
    public String toString() {
    	return String.format("%s(id=%d, moduleId=%s, slideId=%d, taskId=%s)", 
    			this.getClass().getSimpleName(),
    			this.id, 
    			Util.escape(this.moduleId), 
    			this.slideId,
    			Util.escape(this.taskId));
    }
}
