package org.bdgp.MMSlide.Modules;

import javax.swing.JTabbedPane;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import javax.swing.JLabel;
import javax.swing.JTextField;

@SuppressWarnings("serial")
public class ROIFinderDialog extends JTabbedPane {
	JTextField posListName;
	public ROIFinderDialog() {
		
		JPanel panel = new JPanel();
		addTab("New tab", null, panel, null);
		panel.setLayout(new MigLayout("", "[grow]", "[][]"));
		
		JLabel lblEnterPositionList = new JLabel("Enter Name for Saved Position List");
		panel.add(lblEnterPositionList, "cell 0 0");
		
		posListName = new JTextField();
		panel.add(posListName, "cell 0 1,growx");
		posListName.setColumns(10);
	}

}
