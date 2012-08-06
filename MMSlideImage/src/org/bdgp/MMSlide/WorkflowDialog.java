package org.bdgp.MMSlide;


import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.Vector;

import javax.swing.*;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Font;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import org.bdgp.MMSlide.Modules.*;
//import org.bdgp.MMSlide.Modules.Interfaces.ModuleRoot;

import java.awt.Component;


public class SlideWorkflowDialog extends JDialog {

	protected Vector<WorkModule> workers;
//	protected StorageManager storage = null;
	
	protected JTree treeForModules;
	protected JTextField storLocText;
	protected Vector<DefaultMutableTreeNode> node;
	protected DefaultTreeModel treeModel;
	protected DefaultMutableTreeNode treeRoot;
	// protected DefaultMutableTreeNode selectedNode;
	protected String selectedLabel = null;
	
	public SlideWorkflowDialog()
	{
		workers = new Vector<WorkModule>();
//		storage = new StorageManager();
		treeRoot = new DefaultMutableTreeNode("JTree");
		node = new Vector<DefaultMutableTreeNode>();
		
        addWindowListener(new WindowAdapter() {

            public void windowClosing(final WindowEvent e) {
                close();
            }
        });

        setTitle("Slide imaging workflow");
        setSize(600,400);
        setLocationRelativeTo(null);
        
        // Layout
        // setDefaultCloseOperation( DISPOSE_ON_CLOSE );
        getContentPane().setLayout(new BorderLayout(0, 0));
		
		JPanel northPanel = new JPanel();
		getContentPane().add(northPanel, BorderLayout.NORTH);
		northPanel.setLayout(new GridLayout(2, 1, 0, 0));
		
		JPanel filePanel = new JPanel();
		filePanel.setLayout(new FlowLayout());
		northPanel.add(filePanel);
		filePanel.add(new JLabel("Storage Location: "));
		storLocText = new JTextField();
		storLocText.setColumns(20);
		storLocText.setFont(new Font("Arial", Font.PLAIN, 12));
		filePanel.add(storLocText);
		JButton fileButton = new JButton("...");
		filePanel.add(fileButton);
		fileButton.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                // call file dialog
            	selectStorage();
            }
        });
		
		JPanel modulePanel = new JPanel();
		modulePanel.setLayout(new FlowLayout());
		northPanel.add(modulePanel);
		
		JLabel lblWorkflow = new JLabel("Workflow: ");
		modulePanel.add(lblWorkflow);		
		JButton btnPlus = new JButton("+");
		modulePanel.add(btnPlus);
		btnPlus.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                addModule();
            }
        });

		JButton btnMinus = new JButton("-");
		modulePanel.add(btnMinus);		
		btnMinus.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                rmModule();
            }
        });
		
		Component horizontalGlue_1 = Box.createHorizontalGlue();
		modulePanel.add(horizontalGlue_1);
		
		JButton btnConfig = new JButton("Config");
		modulePanel.add(btnConfig);		
		btnConfig.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                configureModule();
            }
        });
		
		JButton btnTest = new JButton("Test");
		modulePanel.add(btnTest);
		
		JPanel southPanel = new JPanel();
		getContentPane().add(southPanel, BorderLayout.SOUTH);
		southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.X_AXIS));
		
		JButton btnLoad = new JButton("Load");
		southPanel.add(btnLoad);
		btnLoad.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                configureModule();
            }
        });
		
		JButton btnSave = new JButton("Save");
		southPanel.add(btnSave);
		btnSave.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                configureModule();
            }
        });
		
		Component horizontalGlue = Box.createHorizontalGlue();
		southPanel.add(horizontalGlue);
		
		JComboBox comboBox = new JComboBox();
		comboBox.setModel(new DefaultComboBoxModel(new String[] {"New", "Resume", "Redo", "No data acquisition"}));
		southPanel.add(comboBox);
		// No data acquisition good for MM workflow
		
		JButton btnRun = new JButton("Run");
		southPanel.add(btnRun);
		
		JScrollPane scrollModulePane = new JScrollPane();
		getContentPane().add(scrollModulePane, BorderLayout.CENTER);
		
		treeRoot = new DefaultMutableTreeNode("JTree");
		treeModel = new DefaultTreeModel(treeRoot);
		treeForModules = new JTree(treeModel);

		treeForModules.setRootVisible(false);
		// moduleList.setPreferredSize(new Dimension(300,200));
		scrollModulePane.setViewportView(treeForModules);		
		treeForModules.addTreeSelectionListener(new TreeSelectionListener() {
			public void valueChanged( TreeSelectionEvent e ) {
				if ( e.getSource() == treeForModules ) {
					TreePath path = treeForModules.getSelectionPath();
					// selectedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
				}
			}
		});
	}


	protected void selectStorage() {
    	JFileChooser dirChooser = new JFileChooser();
    	dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    	int returnVal = dirChooser.showDialog(this, "Select storage location");
    	if ( returnVal == 0 ) {
    		String dir = dirChooser.getCurrentDirectory().toString() + File.separator + dirChooser.getSelectedFile().toString();
//    		storage.setLocation(dir);
    		storLocText.setText(dir);
    	}
    	// JTextField storLocText to text
	}
	
	protected void rmModule() {
		// TODO Auto-generated method stub
		WorkModule selected_worker = null;
//		ModuleBase selected_module = null;
		DefaultMutableTreeNode selected_node = null;
		boolean rm_module = false;
		
		selected_worker = selectedModule(true);		
		if ( selected_worker == null ) {
			return;
		}
//		selected_module = selected_worker.module;		
		
//		if ( selected_module.countSuccessors() > 0 ) {
//			int selected = JOptionPane.showConfirmDialog( this, "The module has children that will be deleted too - continue?",
//					"Delete module?", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null);		
//			if ( selected == 1 ) {
//				rm_module = true;
//			}
//		} else {
//			rm_module = true;
//		}
		
		selected_node = selected_worker.gui_node;
		
		if ( rm_module == true ) {
//			selected_module.rmSucessors();
			selected_node.removeAllChildren();
			selected_node.removeFromParent();
		}
		
	}

	protected void addModule() {
		
		String label = null;
		
		// Dialog for asking which one to add
		// JOptionPane module_dialog = new JOptionPane();
		
//		Object [] mdlg_values = factory.labels();
		Object [] mdlg_values = null;
		Object dlg_selected = JOptionPane.showInputDialog( this, "Select module",
				"Question", JOptionPane.QUESTION_MESSAGE, null, mdlg_values, mdlg_values[0]);		
		label = (String) dlg_selected;
		
		WorkModule newWorker = new WorkModule();
		WorkModule parent = null;
//		newWorker.module = factory.makeModule(label, storage);
		
//		if ( newWorker.module == null ) {
//			JOptionPane.showMessageDialog( this, "Failed to instantiate the module - programming error",
//					"Error", JOptionPane.ERROR_MESSAGE);
//			return;
//		}
		
		if ( workers.size() == 0 ) {
			newWorker.is_root = true;
			addWorkerToTree(newWorker, treeRoot, label, true);
			return;
		}
		
		for ( WorkModule wm : workers ) {
			// use last module that's compatible
//			if ( wm.module.compatibleSuccessor(newWorker.module) == true ) {
//				parent = wm;
//			}			
		}
		
//		if ( newWorker.module instanceof ModuleRoot ) {
//			// give user choice if previous module found
//			if ( parent != null ) {
//				// Modal Dialog
//				// JOptionPane parent_dialog = new JOptionPane();
//				Object [] pdlg_values = {"Root", parent.module.getLabel() };
//				dlg_selected = JOptionPane.showInputDialog( this, "Please select the parent for the new Module",
//						"Question", JOptionPane.QUESTION_MESSAGE, null, pdlg_values, pdlg_values[1]);
//				if ( dlg_selected == pdlg_values[0] ) {
//					newWorker.is_root = true;
//					addWorkerToTree(newWorker, treeRoot, label, true);
//					newWorker.gui_level = 0;
//					return;
//				}
//			}
//		}
		
		if ( parent == null ) {
			// Error message, don't add
			JOptionPane.showMessageDialog( this, "Sorry, no compatible parent module found. Add parent first.",
					"Error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		newWorker.is_root = false;
		addWorkerToTree(newWorker, parent.gui_node, label, true);
		newWorker.gui_level = parent.gui_level + 1;
		workers.add(newWorker);

	}

	protected DefaultMutableTreeNode addWorkerToTree(
			WorkModule worker,
			DefaultMutableTreeNode parent,
            String child,
            boolean shouldBeVisible) {
		
		DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(child);

		treeModel.insertNodeInto(childNode, parent, parent.getChildCount());
		worker.gui_node = childNode;
		workers.add(worker);
		
		//Make sure the user can see the lovely new node.
		if (shouldBeVisible) {
			treeForModules.scrollPathToVisible(new TreePath(childNode.getPath()));
		}
		return childNode;
	}
	
	
	protected WorkModule selectedModule(boolean show_error) {
		WorkModule sel = null;
		
		// TreePath parentPath = treeForModules.getSelectionPath();
	    DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) treeForModules.getLastSelectedPathComponent();
		
		if ( selectedNode == null ) {
			return null;
		}
		
		for ( WorkModule wm : workers ) {
			if ( wm.gui_node == selectedNode ) {
				sel = wm;
				break;
			}
		}

		if ( show_error == true && sel == null ) {
			JOptionPane.showMessageDialog( this, "Failed to find the module - internal error",
					"Error", JOptionPane.ERROR_MESSAGE);
			return null;
		}
		
		return sel;
	}
	
	
	
	protected void close() {
		// TODO Auto-generated method stub
		
	}
	
	
	protected void configureModule() {
		WorkModule conf_m = selectedModule(true);		
		if (conf_m == null) {
			return;
		}
		
//		conf_m.module.configure(null);		
	}
	
	
	protected void configureSave() {
    	JFileChooser confChooser = new JFileChooser();
    	confChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    	int returnVal = confChooser.showDialog(this, "Select configuration");
    	if ( returnVal == 0 ) {
    		String dir = confChooser.getCurrentDirectory().toString() + File.separator + confChooser.getSelectedFile().toString() + ".cnf";
//    		storage.setTempConfig(dir);
    	}
    	
		for ( WorkModule wm : workers ) {
			// TODO call confSave foreach worker
		}
		// TODO: Save Workflow
		
//		storage.unsetTempConfig();
	}
	
	
	protected void configureLoad() {
		
	}
	
	protected void run() {
		// launch root modules sequentially
//		for ( ModuleBase m : rootModules ) {
			
//		}
	}
	
	
	protected class WorkModule {
//		public ModuleBase module;
		public int gui_level;
		public DefaultMutableTreeNode gui_node; 
		// public Vector<ModuleBase> successors = new Vector<ModuleBase>();
		public boolean is_root; 
	}
	
	
	// also allow for multiple roots
	// perhaps don't use, link always to the most recent provider of the function
	protected class ModuleParentsDialog extends JDialog {
		
		private JCheckBox [] cb_modules;
		
//		public ModuleParentsDialog(ModuleBase newModule, Vector<ModuleBase> modules) {
//			
//			int i = 0;
//			cb_modules = new JCheckBox[modules.size()];
//			
//			setTitle("Select parents:");
//			setDefaultCloseOperation( DISPOSE_ON_CLOSE );
//			
//			getContentPane().setLayout(new GridLayout(modules.size(), 1, 0, 0));
//			
//			for ( ModuleBase m : modules ) {
//				if ( m.compatibleSuccessor(newModule) ) {
//					cb_modules[i] = new JCheckBox(m.getLabel(),false);
//					getContentPane().add(cb_modules[i]);
//					i++;
//				}
//			}
//			
//			JPanel btnPanel = new JPanel();
//			btnPanel.setLayout(new FlowLayout());
//			getContentPane().add(btnPanel);
//			
//			JButton btnCancel = new JButton("Cancel");
//			btnPanel.add(btnCancel);		
//			JButton btnOK = new JButton("OK");
//			btnPanel.add(btnOK);		
//			
//		}
		
	}
	
}
