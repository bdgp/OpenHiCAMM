package org.bdgp.MMSlide.Modules;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;

import org.bdgp.MMSlide.Connection;
import org.bdgp.MMSlide.Dao;
import org.bdgp.MMSlide.DB.Pool;
import org.micromanager.api.DeviceControlGUI;

import javax.swing.JTextField;
import javax.swing.JList;

import net.miginfocom.swing.MigLayout;
import static org.bdgp.MMSlide.StorableConfiguration.Storable;

@SuppressWarnings("serial")
public class SlideLoaderDialog extends JPanel {
	List<String> sl_cart, sl_pos, sl_expid;	
	
	JTabbedPane tabbedPanel;
	
	DeviceControlGUI gui;
	JTextField textField;
	JList<String> listPrevPool;
	JButton listButton_;

	@Storable JTable table;
	JRadioButton radioButtonNewPool;
	
	public SlideLoaderDialog(Connection connection) {
		tabbedPanel = new JTabbedPane();
		getRootPane().add(tabbedPanel);
		
		ButtonGroup poolSelGroup = new ButtonGroup();
		// Pool (define new pool or select previous one)
		JPanel poolPanel = new JPanel();
		tabbedPanel.addTab("Pool", null, poolPanel, null);
		poolPanel.setLayout(new MigLayout("", "[256px][1px]", "[64px,center]"));
		
		JPanel panelSelectPool = new JPanel();
		poolPanel.add(panelSelectPool, "cell 0 0,alignx left,aligny top");
		panelSelectPool.setLayout(new MigLayout("", "[77px][41px][114px]", "[22px][]"));
		
		radioButtonNewPool = new JRadioButton("New pool");
		radioButtonNewPool.setSelected(true);
		panelSelectPool.add(radioButtonNewPool, "flowy,cell 0 0,alignx left,aligny center");
		radioButtonNewPool.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
			}
		});
		
		JLabel lblTitle = new JLabel("Name: ");
		panelSelectPool.add(lblTitle, "cell 1 0,alignx left,aligny center");
		
		textField = new JTextField();
		panelSelectPool.add(textField, "cell 2 0,alignx center,growy");
		textField.setColumns(10);
		poolSelGroup.add(radioButtonNewPool);
		
		JRadioButton radioButtonPrevPool = new JRadioButton("Previous pool");
		panelSelectPool.add(radioButtonPrevPool, "cell 0 1 2 1");
		radioButtonPrevPool.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
			}
		});
		poolSelGroup.add(radioButtonPrevPool);
		
		JScrollPane scrollPane_prevPool = new JScrollPane();
		panelSelectPool.add(scrollPane_prevPool, "cell 2 1");
		
		// Populate the previous pool list from the Pool table
		Dao<Pool> pool = connection.table(Pool.class);
		List<Pool> pools = pool.select();
		List<String> pool_names = new ArrayList<String>();
		for (Pool p : pools) {
		    pool_names.add(p.getName());
		}
		Collections.sort(pool_names);
		
		listPrevPool = new JList<String>();
		listPrevPool.setListData(pool_names.toArray(new String[0]));
		listPrevPool.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		listPrevPool.setLayoutOrientation(JList.VERTICAL);
		listPrevPool.setVisibleRowCount(-1);
		
		scrollPane_prevPool.setViewportView(listPrevPool);
		listPrevPool.setEnabled(false);
		listPrevPool.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
			}
		});

		JPanel loaderPanel = new JPanel();
		tabbedPanel.addTab("Loading", null, loaderPanel, null);
		loaderPanel.setLayout(new MigLayout("", "[195px]", "[22px][22px]"));
		
		JRadioButton radioButtonSlideLoader = new JRadioButton("Slide loader");
		loaderPanel.add(radioButtonSlideLoader, "cell 0 0,alignx left,aligny center");
		
		JRadioButton radioButtonSlideManual = new JRadioButton("Manual (will prompt for slides)");
		radioButtonSlideManual.setSelected(true);
		loaderPanel.add(radioButtonSlideManual, "cell 0 1,alignx left,aligny center");
		if ( gui == null ) {
			loaderPanel.setEnabled(false);
		}
		
		//
		// Data (what is in the pool)
		
		DefaultTableModel tabModel = new DefaultTableModel();
		JPanel dataPanel = new JPanel();
		table = new JTable();
		table.setBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null));
		table.setModel(tabModel);
		table.getColumnModel().getColumn(0).setPreferredWidth(142);
		table.getColumnModel().getColumn(1).setPreferredWidth(135);
		table.getColumnModel().getColumn(2).setPreferredWidth(104);
		table.getColumnModel().getColumn(2).setMinWidth(100);
		dataPanel.setLayout(new MigLayout("", "[554px][41px]", "[323px][24px]"));

		JScrollPane scrollPane = new JScrollPane();
		dataPanel.add(scrollPane, "cell 0 0,grow");
		scrollPane.add(table);

		JPanel dataLoadPanel = new JPanel();
		dataPanel.add(dataLoadPanel, "cell 0 1 2 1,growx,aligny top");
		dataLoadPanel.setLayout(new MigLayout("", "[123px][54px][89px][93px][][][][]", "[24px]"));

		JLabel lblLoadContentsFrom = new JLabel("Load contents from ...");
		dataLoadPanel.add(lblLoadContentsFrom, "cell 0 0,alignx left,aligny center");

		JButton btnFile = new JButton("File");
		btnFile.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		    }
		});
		dataLoadPanel.add(btnFile, "cell 1 0,alignx left,aligny center");

		JButton btnDatabase = new JButton("Database");
		btnDatabase.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		    }
		});
		// database should update the database about the Pool-id
		// perhaps even send the metadata to the database (file location of the slides, etc).
		// Could be encapsuled in db object in Slide storage or passed around
		dataLoadPanel.add(btnDatabase, "cell 2 0,alignx left,aligny center");
		
		JButton btnClear = new JButton("Clear data");
		btnClear.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
			}
		});
		dataLoadPanel.add(btnClear, "cell 7 0,alignx left,aligny center");

		JPanel dataEntryPanel = new JPanel();
		dataPanel.add(dataEntryPanel, "cell 1 0,alignx left,growy");
		dataEntryPanel.setLayout(new MigLayout("", "[41px]", "[14px][24px][24px]"));

		JLabel lblData = new JLabel("Rows:");
		dataEntryPanel.add(lblData, "cell 0 0,alignx left,aligny center");
		
		JButton addRowBut = new JButton("+");
		addRowBut.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
			}
		});
		dataEntryPanel.add(addRowBut, "cell 0 1,alignx left,aligny center");

		JButton rmRowBut = new JButton("-");
		rmRowBut.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
			}
		});
		dataEntryPanel.add(rmRowBut, "cell 0 2,alignx left,aligny center");

		tabbedPanel.addTab("Data", dataPanel);

		// Positions (what to image: entire slide, previously defined positions)
		JPanel posPanel = new JPanel();

		tabbedPanel.addTab("Positions", posPanel);
		posPanel.setLayout(new MigLayout("", "[595px]", "[347px]"));

		JPanel posChoicePanel = new JPanel();
		posPanel.add(posChoicePanel, "cell 0 0,grow");

		ButtonGroup roiButtons = new ButtonGroup();
		posChoicePanel.setLayout(new MigLayout("", "[90px][148px][53px][115px]", "[24px][]"));

		JRadioButton radioSlideButton = new JRadioButton("Whole slide");
		radioSlideButton.setSelected(true);
		roiButtons.add(radioSlideButton);
		posChoicePanel.add(radioSlideButton, "cell 0 0,alignx left,aligny center");
		JButton posButton = new JButton("Define imaging area");
		posButton.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		    }
		});
		posChoicePanel.add(posButton, "cell 1 0,alignx left,aligny top");
		
		JRadioButton radioRoiButton = new JRadioButton("ROIs");
		roiButtons.add(radioRoiButton);
		posChoicePanel.add(radioRoiButton, "cell 0 1,alignx left,aligny center");
		JButton roiButton = new JButton("Define ROI list");
		posChoicePanel.add(roiButton, "cell 1 1,alignx left,aligny top");
		roiButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                gui.showXYPositionList();
            }
        });
		
		if ( gui == null ) {
			posPanel.setEnabled(false);
		}
	}
}
