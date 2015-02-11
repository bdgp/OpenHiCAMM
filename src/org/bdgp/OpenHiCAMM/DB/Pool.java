package org.bdgp.OpenHiCAMM.DB;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Pool {
    @DatabaseField(generatedId=true, canBeNull=false)
    private int id;

    public Pool() { }
    public Pool(int id) { 
        this.id = id;
    }
    public int getId() { return this.id; }
    public String getName() { return String.format("P%05d", this.id); }

	public static Integer name2id(String poolName) {
        Pattern pattern = Pattern.compile("^P([0-9]+)$");
        Matcher matcher = pattern.matcher(poolName);
        if (matcher.find()) {
            return new Integer(matcher.group(1));
        }
		return null;
	}
	
	public String toString() {
		return String.format("%s", this.getName());
	}
}
