package org.bdgp.MMSlide.DB;

import org.micromanager.api.PositionList;
import org.micromanager.utils.MMSerializationException;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class SlidePosList {
    @DatabaseField(generatedId=true) 
    private int id;
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING,unique=true) 
    private String name;
    @DatabaseField(canBeNull=false,dataType=DataType.LONG_STRING,useGetSet=true) 
    private String posList;
    private PositionList positionList;

    public SlidePosList(String name, PositionList positionList) {
        setPositionList(positionList);
    }
    public SlidePosList(String name, String posList) {
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
    public int getId() { return id; }
    public String getName() { return name; }
    public String getPosList() { return posList; }
    public PositionList getPositionList() { return positionList; }
}
