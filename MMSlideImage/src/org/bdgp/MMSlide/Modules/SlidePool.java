package org.bdgp.MMSlide.Modules;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.bdgp.MMSlide.Logger;
import org.bdgp.MMSlide.WorkflowRunner;
import org.bdgp.MMSlide.Dao.Config;
import org.bdgp.MMSlide.Dao.Dao;
import org.bdgp.MMSlide.Dao.Task.Status;
import org.bdgp.MMSlide.Modules.Interfaces.Module;
import org.bdgp.MMSlide.Modules.Interfaces.Root;
import org.bdgp.MMSlide.Modules.Interfaces.WorkerSlide;
import org.micromanager.api.AcquisitionEngine;
import org.micromanager.api.DeviceControlGUI;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.io.File;

public class SlidePool implements Module<WorkerSlide>, Root {
    // File name will be unique, even if several pool workers in same workflow (use same experiment)
	protected final String POOL_FILE = "pool_contents.txt";
	protected Dao<PoolData> pool;
	
	SlidePoolMainDialog confDlg = null;
	DeviceControlGUI mm_gui = null;
	
	public SlidePool() { 
		// TODO doesn't work on uninitialized storage
		// storage.setUnique(this); // there can be only one storage pool per workflow

		// storage.get should probably not ever be in the constructor!
		// Storage locations are possibly initialized after class is created. 
		// Everywhere else is fine. 
	}
	
	public void setNoAcquisition() {
		// TODO Auto-generated method stub
		
	}

	public void setMM(AcquisitionEngine acqEng, DeviceControlGUI gui) {
		// TODO Auto-generated method stub
		mm_gui = gui;
	}

	public void addPoolData(int c, int s, String e) throws SQLException {
		pool.create(new PoolData(c, s, e));
	}
	
	public List<PoolData> getPoolData() throws SQLException {
		return pool.queryForAll();
	}
	
	public void clearPoolData() throws SQLException {
		pool.deleteBuilder().delete();
	}
	
	
	@DatabaseTable
	public static class PoolData {
	    public int getCartridge() {
            return cartridge;
        }

        public void setCartridge(int cartridge) {
            this.cartridge = cartridge;
        }

        public int getSlide() {
            return slide;
        }

        public void setSlide(int slide) {
            this.slide = slide;
        }

        public String getExperiment() {
            return experiment;
        }

        public void setExperiment(String experiment) {
            this.experiment = experiment;
        }

        @DatabaseField
		public int cartridge = -1;
	    @DatabaseField
		public int slide = -1;
		@DatabaseField(id=true)
		public String experiment = null;
		
		public PoolData() {}
		
		public PoolData(int c, int s, String e) {
			cartridge = c;
			slide = s;
			experiment = e;
		}
	}


    @Override
    public Status start(Map<String, Config> config) {
		// if slide loader found, load next slide
		// else prompt manual loading
		
		// pool.setFile(store_dir, POOL_FILE);
		String store_dir = config.get("storageLocation").getValue();
		pool = Dao.get(PoolData.class, new File(store_dir,POOL_FILE).getPath());
        return null;
    }

    @Override
    public Map<String, Config> configure() {
        // TODO Save items independent of storage pool
		// XY list/selection of ROI
		// Slide loader/manual
        
//        if ( confDlg == null ) {
//			confDlg = new SlidePoolMainDialog(mm_gui, this, storage.getModuleStorage(this, CollectionType.SIBLINGS));
//		}
		
		confDlg.setVisible(true);
		
		// TODO Auto-generated method stub
		// Define GUI
		/*
	    listButton_ = new JButton();
        listButton_.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                gui_.showXYPositionList();
            }
        });
        listButton_.setToolTipText("Open XY list dialog");
        listButton_.setIcon(SwingResourceManager.getIcon(AcquisitionDialog.class, "icons/application_view_list.png"));
        listButton_.setText("Edit position list...");
        listButton_.setMargin(new Insets(2, 5, 2, 5));
        listButton_.setFont(new Font("Dialog", Font.PLAIN, 10));
        listButton_.setBounds(42, 25, 136, 26);
        positionsPanel_.add(listButton_); 
        */
        return null;
    }

    @Override
    public Status callSuccessor(WorkerSlide successor, int instance_id,
            Map<String, Config> config, Logger logger) 
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Class<WorkerSlide> getSuccessorInterface() {
        return WorkerSlide.class;
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
    public boolean requiresDataAcquisitionMode() {
        return true;
    }
}
