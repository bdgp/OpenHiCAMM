package org.bdgp.MMSlide.Modules;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;

import org.bdgp.MMSlide.FileLineStorage;
import org.bdgp.MMSlide.SlideStorage;
import org.micromanager.api.DeviceControlGUI;
import javax.swing.JTextField;
import javax.swing.JList;
import java.awt.Component;
import javax.swing.Box;

public class SlidePoolMainDialog extends JDialog {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -8903037819713128061L;

	Vector<String> sl_cart, sl_pos, sl_expid;	
	
	protected JButton listButton_;
	private JTabbedPane tabbedPanel;
	private JTable table;
	protected Vector<String> dataColumns;
	protected Vector<Vector<String>> dataRows;
	
	protected DeviceControlGUI gui;
	private JTextField textField;
	protected JList listPrevPool;
	
	SlidePool slide_pool_module = null;
	SlideStorage.StorageCollection other_pools;
	
	public SlidePoolMainDialog(DeviceControlGUI mm_gui, SlidePool sp, SlideStorage.StorageCollection other_pools) {
		
		this.gui = mm_gui;
		slide_pool_module = sp;
		this.other_pools = other_pools;
		
		setVisible(false); // Not visible until requested
		setTitle("Configuration: Slide Pool");
        setSize(600,400);
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {

            public void windowClosing(final WindowEvent e) {
                close();
            }

        });
		
		tabbedPanel = new JTabbedPane();
		getContentPane().setLayout(new BorderLayout(0, 0));
		getContentPane().add(tabbedPanel, BorderLayout.CENTER);

		//
		// Pool (define new pool or select previous one)
		
		JPanel poolPanel = new JPanel();
		tabbedPanel.addTab("Pool", null, poolPanel, null);
		poolPanel.setLayout(new GridLayout(2, 1, 0, 0));
		
		JPanel panelNewPool = new JPanel();
		poolPanel.add(panelNewPool);
		panelNewPool.setLayout(new BoxLayout(panelNewPool, BoxLayout.X_AXIS));
		
		JRadioButton radioButtonNewPool = new JRadioButton("New pool");
		radioButtonNewPool.setSelected(true);
		panelNewPool.add(radioButtonNewPool);
		radioButtonNewPool.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setPool(null);
			}
		});
		
		Component horizontalStrut = Box.createHorizontalStrut(20);
		panelNewPool.add(horizontalStrut);
		
		JLabel lblTitle = new JLabel("Optional name:");
		panelNewPool.add(lblTitle);
		
		textField = new JTextField();
		panelNewPool.add(textField);
		textField.setColumns(10);
		
		JPanel panelPrevPool = new JPanel();
		poolPanel.add(panelPrevPool);
		panelPrevPool.setLayout(new BoxLayout(panelPrevPool, BoxLayout.X_AXIS));
		
		JRadioButton radioButtonPrevPool = new JRadioButton("Previous pool");
		panelPrevPool.add(radioButtonPrevPool);
		radioButtonPrevPool.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				populatePools();
			}
		});
	
		JScrollPane scrollPane_prevPool = new JScrollPane();
		panelPrevPool.add(scrollPane_prevPool);
		
		listPrevPool = new JList();
		listPrevPool.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		listPrevPool.setLayoutOrientation(JList.VERTICAL);
		listPrevPool.setVisibleRowCount(-1);

		scrollPane_prevPool.setViewportView(listPrevPool);
		listPrevPool.setEnabled(false);
		listPrevPool.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				 // Make sure value is final
				 if (e.getValueIsAdjusting() == false) {
					 setPool((String) listPrevPool.getSelectedValue());
				 }
			}
		});
		
		
		ButtonGroup poolSelGroup = new ButtonGroup();
		poolSelGroup.add(radioButtonNewPool);
		poolSelGroup.add(radioButtonPrevPool);


		
		//
		// Loading (Slide loader or manual)
		
		JPanel loaderPanel = new JPanel();
		tabbedPanel.addTab("Loading", null, loaderPanel, null);
		loaderPanel.setLayout(new BoxLayout(loaderPanel, BoxLayout.Y_AXIS));
		
		JRadioButton radioButtonSlideLoader = new JRadioButton("Slide loader");
		loaderPanel.add(radioButtonSlideLoader);
		
		JRadioButton radioButtonSlideManual = new JRadioButton("Manual (will prompt for slides)");
		radioButtonSlideManual.setSelected(true);
		loaderPanel.add(radioButtonSlideManual);
		if ( gui == null ) {
			loaderPanel.setEnabled(false);
		}
		
		//
		// Data (what is in the pool)
		
		DefaultTableModel tabModel;

		JPanel dataPanel = new JPanel();
		dataPanel.setLayout(new BorderLayout(0, 0));

		dataRows=new Vector<Vector<String>>();
		dataColumns= new Vector<String>();
		String[] columnNames = { "Cartridge", "Slide position", "Experiment id"	};
		for(int i=0;i<columnNames.length;i++)
			dataColumns.addElement((String) columnNames[i]);


		tabModel=new DefaultTableModel();
		tabModel.setDataVector(dataRows,dataColumns);

		table = new JTable();
		table.setBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null));
		table.setModel(tabModel);
		table.getColumnModel().getColumn(0).setPreferredWidth(142);
		table.getColumnModel().getColumn(1).setPreferredWidth(135);
		table.getColumnModel().getColumn(2).setPreferredWidth(104);
		table.getColumnModel().getColumn(2).setMinWidth(100);

		JScrollPane scrollPane = new JScrollPane();
		dataPanel.add(scrollPane, BorderLayout.CENTER);
		scrollPane.add(table);

		JPanel dataLoadPanel = new JPanel();
		dataPanel.add(dataLoadPanel, BorderLayout.SOUTH);
		dataLoadPanel.setLayout(new BoxLayout(dataLoadPanel, BoxLayout.X_AXIS));

		JLabel lblLoadContentsFrom = new JLabel("Load contents from ...");
		dataLoadPanel.add(lblLoadContentsFrom);

		JButton btnFile = new JButton("File");
		dataLoadPanel.add(btnFile);

		JButton btnDatabase = new JButton("Database");
		// database should update the database about the Pool-id
		// perhaps even send the metadata to the database (file location of the slides, etc).
		// Could be encapsuled in db object in Slide storage or passed around
		dataLoadPanel.add(btnDatabase);
		
		Component horizontalGlue = Box.createHorizontalGlue();
		dataLoadPanel.add(horizontalGlue);
		
		JButton btnClear = new JButton("Clear data");
		btnClear.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				clearData();
			}
		});
		dataLoadPanel.add(btnClear);

		JPanel dataEntryPanel = new JPanel();
		dataPanel.add(dataEntryPanel, BorderLayout.EAST);
		dataEntryPanel.setLayout(new BoxLayout(dataEntryPanel, BoxLayout.Y_AXIS));

		JLabel lblData = new JLabel("Rows:");
		dataEntryPanel.add(lblData);
		
		JButton addRowBut = new JButton("+");
		addRowBut.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				addRow();
			}
		});
		dataEntryPanel.add(addRowBut);

		JButton rmRowBut = new JButton("-");
		rmRowBut.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				deleteRow(table.getSelectedRow());
			}
		});
		dataEntryPanel.add(rmRowBut);

		tabbedPanel.addTab("Data", dataPanel);

		//
		// Positions (what to image: entire slide, previously defined positions)
		
		JPanel posPanel = new JPanel();
		posPanel.setLayout(new BorderLayout(0, 0));

		tabbedPanel.addTab("Positions", posPanel);

		JPanel posChoicePanel = new JPanel();
		posChoicePanel.setLayout(new GridLayout(2, 2, 0, 0));
		posPanel.add(posChoicePanel, BorderLayout.CENTER);

		ButtonGroup roiButtons = new ButtonGroup();

		JRadioButton radioSlideButton = new JRadioButton("Whole slide");
		radioSlideButton.setSelected(true);
		roiButtons.add(radioSlideButton);
		posChoicePanel.add(radioSlideButton);
		JButton posButton = new JButton("Define imaging area");
		posChoicePanel.add(posButton);

		JRadioButton radioRoiButton = new JRadioButton("ROIs");
		roiButtons.add(radioRoiButton);
		posChoicePanel.add(radioRoiButton);
		JButton roiButton = new JButton("Define ROI list");
		posChoicePanel.add(roiButton);
		roiButton.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                gui.showXYPositionList();
            }
        });
		
		if ( gui == null ) {
			posPanel.setEnabled(false);
		}

	}
	
	protected void close() {
		// TODO Auto-generated method stub
		getData();
	}
	
	
	// Data pool methods
	
	protected void populatePools() {
		DefaultListModel listModel = new DefaultListModel();
		
		Collection <String> others = other_pools.get();
		
		Iterator<String> it = others.iterator();
		while (it.hasNext()) {
			listModel.addElement(it.next());
		}
		
		listPrevPool.setModel(listModel);
		listPrevPool.setEnabled(true);
	}
	
	protected void setPool(String poolValue) {
		
	}
	
	
	// Data table methods
	
	protected void addRow() //Add Row
	{
		Vector<String> r = new Vector<String>();
		r.addElement(" ");
		r.addElement(" ");
		r.addElement(" ");
		dataRows.addElement(r);
		table.addNotify();
		
	}
		
	protected void deleteRow(int index) 
	{
		if(index!=-1)//At least one Row in Table
		{ 
			dataRows.removeElementAt(index);
			table.addNotify();
		}
		
	}//Delete Row

	
	public void clearData() {
		int selected = JOptionPane.showConfirmDialog( this, "Delete all pool contents?",
				"Delete pool?", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null);		
		if ( selected == 1 ) {			
			for (int r=table.getRowCount()-1; r >= 0; r++ ) {
				deleteRow(r);
			}
			slide_pool_module.clearPoolData();
		}
	}
	
	
	public void getData() {		
		readTable();
	}
	
	
	public void setData() {
		int r = 0;
		Collection<SlidePool.PoolData> data = slide_pool_module.getPoolData();
		
		for ( SlidePool.PoolData spd : data ) {
			table.setValueAt(spd.cartridge, r, 0);
			table.setValueAt(spd.slide, r, 1);
			table.setValueAt(spd.experiment, r, 2);
			r++;
		}
	}
		
	protected void readTable() {
		// TODO Sanity check of data
		for (int r=0; r < table.getRowCount(); r++ ) {
			String s_cart = (String) table.getValueAt(r,0);
			String s_pos  = (String) table.getValueAt(r,1);
			String s_exp  = (String) table.getValueAt(r,2);
			
			Integer i_cart = new Integer(s_cart);
			Integer i_pos = new Integer(s_pos);
			
			slide_pool_module.addPoolData(i_cart.intValue(), i_pos.intValue(), s_exp);
			
		}
	}
}
