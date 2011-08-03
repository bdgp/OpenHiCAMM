package org.bdgp.MMSlide.Modules;

import java.util.Collection;
import java.util.HashMap;
import org.bdgp.MMSlide.FileLineStorage;
import org.bdgp.MMSlide.LineItem;
import org.bdgp.MMSlide.SlideStorage;
import org.bdgp.MMSlide.SlideStorage.CollectionType;
import org.micromanager.api.AcquisitionEngine;
import org.micromanager.api.DeviceControlGUI;

import java.io.IOException;

public class SlidePool extends ModuleBase implements MMModule, WorkerRoot {

	// File name will be unique, even if several pool workers in same workflow (use same experiment)
	protected final String POOL_FILE = "pool_contents";
	
	FileLineStorage<PoolData> pool = null;
	SlidePoolMainDialog confDlg = null;
	DeviceControlGUI mm_gui = null;
	
	public SlidePool(SlideStorage storage) {
		super(storage);	
		// doesn't work on uninitialized storage
		// storage.setUnique(this); // there can be only one storage pool per workflow

		// What are we
		moduleLabel = "Slide pool";
		moduleText = "Define and load (optional) slides from a pool";		

		// storage.get should probably not ever be in the constructor!
		// Storage locations are possibly initialized after class is created. 
		// Everywhere else is fine. 
		
	}
	
	public void init() throws IOException {

		String store_dir = storage.get(this);
		pool = new FileLineStorage<PoolData>(PoolData.class, store_dir, POOL_FILE);
		pool.setCache(true);
		
	}
	
	
	@Override
	public
	boolean compatibleSuccessor(ModuleBase mod) {
		if ( mod instanceof WorkerSlide ) {
			return true;
		}
		if ( mod instanceof WorkerImageCamera ) {
			return true;
		}
		return false;
	}

	@Override
	public void test() {
		// TODO Auto-generated method stub

	}

	public void setNoAcquisition() {
		// TODO Auto-generated method stub
		
	}

	public void setMM(AcquisitionEngine acqEng, DeviceControlGUI gui) {
		// TODO Auto-generated method stub
		mm_gui = gui;
	}

	public void start()  {
		// TODO Auto-generated method stub
		// if slide loader found, load next slide
		// else prompt manual loading
		
		// pool.setFile(store_dir, POOL_FILE);
		
	}

	@Override
	public void configure(HashMap<String, String> options) {
		
		if ( confDlg == null ) {
			confDlg = new SlidePoolMainDialog(mm_gui, this, storage.getModuleStorage(this, CollectionType.SIBLINGS));
		}
		
		
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
	}

	@Override
	public void confSave() {
		// TODO Save items independent of storage pool
		// XY list/selection of ROI
		// Slide loader/manual
		
	}

	@Override
	public void confLoad() {
		// TODO Auto-generated method stub
		
	}
	
	public void addPoolData(int c, int s, String e) {
		try {
			pool.add(new PoolData(c, s, e));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
	public Collection<PoolData> getPoolData() {
		return pool.cachedValues();
	}
	
	public void clearPoolData() {
		pool.clear();
	}
	
	
	public class PoolData implements LineItem {
		public int cartridge = -1;
		public int slide = -1;
		public String experiment = null;
		
		public PoolData() {}
		
		public PoolData(int c, int s, String e) {
			cartridge = c;
			slide = s;
			experiment = e;
		}

		public String [] toTokens() {
			String [] t = new String[3];
			t[0] = new Integer(cartridge).toString();
			t[1] = new Integer(slide).toString();
			t[2] = experiment;
			return t;
		}
		
		public void fromTokens(String [] line) {
			cartridge = new Integer(line[0]).intValue();
			slide = new Integer(line[1]).intValue();
			experiment = line[2];
		}

		public String key() {
			return experiment;
		}
	}


	
}
