package org.bdgp.MMSlide.Modules;

public interface DataAccessBase {
	public void initData(); // for pre-computations
	public void nextData();
	public <T> T getData();
}
