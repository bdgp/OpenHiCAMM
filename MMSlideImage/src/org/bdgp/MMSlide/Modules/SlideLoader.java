package org.bdgp.MMSlide.Modules;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import org.bdgp.MMSlide.Configuration;
import org.bdgp.MMSlide.Logger;
import org.bdgp.MMSlide.WorkflowRunner;
import org.bdgp.MMSlide.Dao.Config;
import org.bdgp.MMSlide.Dao.Dao;
import org.bdgp.MMSlide.Dao.Pool;
import org.bdgp.MMSlide.Dao.PoolData;
import org.bdgp.MMSlide.Dao.Task;
import org.bdgp.MMSlide.Dao.Task.Status;
import org.bdgp.MMSlide.Modules.Interfaces.Module;

public class SlideLoader implements Module {
	protected final String POOL = "pool.txt";
	protected final String POOL_DATA_FILE = "pool_contents.txt";
	protected Dao<Pool> pool;
	protected Dao<PoolData> poolData;
	
	public SlideLoader() { }
	
	public List<PoolData> getPoolData() throws SQLException {
		return poolData.select();
	}
	
	public void clearPoolData() throws SQLException {
		poolData.delete();
	}
	
    @Override
    public String getTitle() {
        return "Slide pool";
    }

    @Override
    public String getDescription() {
        return "Define and load (optional) slides from a pool";
    }

    @Override
    public Status run(WorkflowRunner workflow, Task task, Map<String,Config> config, Logger logger) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JPanel configure(Configuration config) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void createTaskRecords(WorkflowRunner workflow, String moduleId) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public Map<String, Integer> getResources() {
        // TODO Auto-generated method stub
        return null;
    }
}
