package org.bdgp.MMSlide.DB;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Slide {
    @DatabaseField(generatedId=true, canBeNull=false) int id;
    @DatabaseField(canBeNull=false, uniqueCombo=true)
    private int cartridgeId;
    @DatabaseField(canBeNull=false, uniqueCombo=true)
    private int slideNumber;
    
    public Slide() {}
    public Slide(int cartridgeId, int slideNumber) {
        this.cartridgeId = cartridgeId;
        this.slideNumber = slideNumber;
    }

    public int getId() { return id; }
    public int getCartridgeId() { return cartridgeId; }
    public int getSlideNumber() { return slideNumber; }
    public String getName() { return String.format("S%05d",this.id); }
}
