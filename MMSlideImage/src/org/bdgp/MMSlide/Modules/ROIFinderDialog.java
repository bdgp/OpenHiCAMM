package org.bdgp.MMSlide.Modules;

import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import javax.swing.JLabel;
import javax.swing.JTextField;

@SuppressWarnings("serial")
public class ROIFinderDialog extends JPanel {
	JTextField posListName;
	public ROIFinderDialog() {
		
		this.setLayout(new MigLayout("", "[grow]", "[][]"));
		
		JLabel lblEnterPositionList = new JLabel("Enter Name for Saved Position List");
		this.add(lblEnterPositionList, "cell 0 0");
		
		posListName = new JTextField();
		this.add(posListName, "cell 0 1,growx");
		posListName.setColumns(10);
	}

}
