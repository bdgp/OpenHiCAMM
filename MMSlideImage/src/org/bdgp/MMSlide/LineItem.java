package org.bdgp.MMSlide;

public interface LineItem {
	public String [] toTokens();
	public void fromTokens(String [] line);
	public String key();
}
