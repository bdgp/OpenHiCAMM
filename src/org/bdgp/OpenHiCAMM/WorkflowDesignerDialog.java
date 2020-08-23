package org.bdgp.OpenHiCAMM;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.awt.Dialog;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeNode;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.io.File;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.bdgp.OpenHiCAMM.DB.WorkflowModule;

import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;

import net.miginfocom.swing.MigLayout;
import static org.bdgp.OpenHiCAMM.Util.where;

@SuppressWarnings("serial")
public class WorkflowDesignerDialog extends JDialog {
	private JTree treeForModules;
	private WorkflowModuleNode treeRoot;
	private JButton btnPlus;
	private JButton btnMinus;
	private JTextField moduleName;
	private JComboBox<String> moduleList;
    private Connection connection;
	
	public WorkflowDesignerDialog(JDialog parent, final File workflowDirectory) {
	    super(parent, "Workflow Designer", Dialog.ModalityType.DOCUMENT_MODAL);
	    
	    this.connection = Connection.get(
	            new File(workflowDirectory, WorkflowRunner.WORKFLOW_DB).getPath());
		
        addWindowListener(new WindowAdapter() {
            public void windowClosing(final WindowEvent e) { }
        });

		treeRoot = new WorkflowModuleNode();
		treeRoot.setUserObject("Start");
		
        setTitle("Slide imaging workflow");
        setSize(800,600);
        setLocationRelativeTo(null);
		getContentPane().setLayout(new MigLayout("", "[765.00][]", "[63px][586px][29px]"));
		
		JLabel lblWorkflow = new JLabel("Module Type");
		getContentPane().add(lblWorkflow, "flowx,cell 0 0,span 2");
		
		moduleList = new JComboBox<String>();
		getContentPane().add(moduleList, "cell 0 0");
		moduleList.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		        WorkflowDesignerDialog.this.setEnabledControls();
		        moduleName.setText(chooseModuleName((String)moduleList.getSelectedItem()));
		    }
		});
		moduleList.setToolTipText("select module");
		moduleList.setModel(new DefaultComboBoxModel<String>(
		        OpenHiCAMM.getModuleNames().toArray(new String[0])));
		
		JLabel lblNewLabel = new JLabel("Module Name");
		getContentPane().add(lblNewLabel, "cell 0 0,alignx left,growy");
		
		moduleName = new JTextField();
		getContentPane().add(moduleName, "cell 0 0 2 1,growx");
		moduleName.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { WorkflowDesignerDialog.this.setEnabledControls(); }
            @Override public void removeUpdate(DocumentEvent e) { WorkflowDesignerDialog.this.setEnabledControls(); }
            @Override public void changedUpdate(DocumentEvent e) { WorkflowDesignerDialog.this.setEnabledControls(); }
        });
		moduleName.setToolTipText("module name");
		moduleName.setColumns(26);
		JLabel lblTaskType = new JLabel("Task Type");
		getContentPane().add(lblTaskType, "cell 0 0");
		
		btnPlus = new JButton("+");
		getContentPane().add(btnPlus, "cell 0 0");
		btnPlus.setToolTipText("add a module to the workflow");
		btnPlus.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		        TreePath path = treeForModules.getSelectionPath();
		        WorkflowModuleNode parentNode = path != null
		                ? (WorkflowModuleNode) path.getLastPathComponent()
		                : treeRoot;
		                
		        WorkflowModule module = new WorkflowModule(
		                (String)moduleName.getText(), 
		                (String)moduleList.getSelectedItem(),
		                parentNode.getWorkflowModule() != null? parentNode.getWorkflowModule().getId() : null);
		        
		        DefaultTreeModel model = (DefaultTreeModel) treeForModules.getModel();
		        WorkflowModuleNode moduleNode = new WorkflowModuleNode(module);
		        model.insertNodeInto(moduleNode, parentNode, parentNode.getChildCount());
		        
		        // expand the tree
		        treeForModules.makeVisible(new TreePath(moduleNode.getPath()));
		        
		        moduleName.setText(chooseModuleName((String)moduleList.getSelectedItem()));
		    }
		});
		btnPlus.setEnabled(moduleList.getSelectedItem() != null);
				
		btnMinus = new JButton("-");
		getContentPane().add(btnMinus, "cell 0 0");
		btnMinus.setToolTipText("remove a module from the workflow");
		treeForModules = new JTree(treeRoot);
		treeForModules.setShowsRootHandles(false);
		treeForModules.setRootVisible(true);
		treeForModules.addTreeSelectionListener(new TreeSelectionListener() {
		    public void valueChanged(TreeSelectionEvent e) {
		        setEnabledControls();
		    }
		});
			
		JScrollPane scrollModulePane = new JScrollPane(treeForModules);
		getContentPane().add(scrollModulePane, "cell 0 1 2 1,grow");
		
		treeForModules.setSelectionPath(new TreePath(treeRoot.getPath()));
		
		JButton btnCancel = new JButton("Cancel");
		getContentPane().add(btnCancel, "flowx,cell 0 2,alignx left");
		btnCancel.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		        WorkflowDesignerDialog.this.dispose();
		    }
		});
		btnMinus.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		        TreePath path = treeForModules.getSelectionPath();
		        if (path != null) {
		            WorkflowModuleNode moduleNode = (WorkflowModuleNode) path.getLastPathComponent();
		            if (moduleNode != treeRoot) {
        	            DefaultTreeModel model = (DefaultTreeModel) (treeForModules.getModel());
        	            WorkflowModuleNode parentNode = (WorkflowModuleNode) moduleNode.getParent();
        	            model.removeNodeFromParent(moduleNode);
        	            treeForModules.makeVisible(new TreePath(parentNode.getPath()));
        	            
        	            moduleName.setText(
        	                    chooseModuleName((String)moduleList.getSelectedItem()));
		            }
		        }
		    }
		});
		
		moduleName.setText(
		        chooseModuleName((String)moduleList.getSelectedItem()));
		
		JButton doneButton = new JButton("Done");
		getContentPane().add(doneButton, "cell 1 2,alignx trailing");

		// Populate the tree model
        Dao<WorkflowModule> wf = connection.file(WorkflowModule.class, new File(WorkflowRunner.WORKFLOW_FILE).getPath());
        Map<String,Integer> idMapping = new HashMap<>();
        for (WorkflowModule wm : wf.select()) {
            idMapping.put(wm.getName(), wm.getId());
        }
        DefaultTreeModel model = (DefaultTreeModel) treeForModules.getModel();
		List<WorkflowModuleNode> moduleNodes = new ArrayList<WorkflowModuleNode>();
		moduleNodes.add(treeRoot);
		while (moduleNodes.size() > 0) {
		    List<WorkflowModuleNode> nextModuleNodes = new ArrayList<WorkflowModuleNode>();
		    for (WorkflowModuleNode moduleNode : moduleNodes) {
        		List<WorkflowModule> childModules = wf.select(
        		        where("parentId", 
        		                moduleNode.getWorkflowModule() != null? 
                                    moduleNode.getWorkflowModule().getId() : null));
        		for (WorkflowModule child : childModules) {
        		    WorkflowModuleNode childNode = new WorkflowModuleNode(child);
    		        model.insertNodeInto(childNode, moduleNode, moduleNode.getChildCount());
    	            treeForModules.makeVisible(new TreePath(childNode.getPath()));
    	            nextModuleNodes.add(childNode);
        		}
		    }
		    moduleNodes = nextModuleNodes;
		}
		
		doneButton.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		        // clear the workflow
	            Dao<WorkflowModule> wf = connection.file(WorkflowModule.class, new File(WorkflowRunner.WORKFLOW_FILE).getPath());
	            wf.delete();
	            // create the WorkflowModule records
	            int priority = 1;
	            for (Enumeration<TreeNode> enum_=treeRoot.breadthFirstEnumeration(); 
                    enum_.hasMoreElements();) 
	            {
	                WorkflowModuleNode node = (WorkflowModuleNode) enum_.nextElement();
	                if (node != treeRoot) {
	                    if (node.getWorkflowModule() != null) {
	                        node.getWorkflowModule().setPriority(priority);
	                        if (idMapping.containsKey(node.getWorkflowModule().getName())) {
	                            node.getWorkflowModule().setId(idMapping.get(node.getWorkflowModule().getName()));
	                        }
	                        if (node.getParent() != null && ((WorkflowModuleNode)node.getParent()).getWorkflowModule() != null) {
                                node.getWorkflowModule().setParentId(((WorkflowModuleNode)node.getParent()).getWorkflowModule().getId());
	                        }
	                        else {
                                node.getWorkflowModule().setParentId(null);
	                        }
                            wf.insert(node.getWorkflowModule());
	                    }
                        ++priority;
	                }
	            }
	            wf.updateSequence();
	            WorkflowDesignerDialog.this.dispose();
            }
	    });
		
		setEnabledControls();
	}
	
	private void setEnabledControls() {
	    // set various buttons to be enabled/disabled depending on the state
	    // of the input fields.
	    btnPlus.setEnabled(
	            moduleList.getSelectedItem() != null &&
	            !moduleNameExists(moduleName.getText()));
        btnMinus.setEnabled(treeForModules.getSelectionCount()>0);
	}
	
	/**
	 * Choose a unique ID for a module with the give module name.
	 * @param moduleName
	 * @return
	 */
	private String chooseModuleName(String moduleName) {
	    if (moduleName != null) {
    	    Matcher m = Pattern.compile("([^.]+)$").matcher(moduleName);
    	    if (m.find() && m.groupCount() > 0) {
    	        String name = m.group(1);
    	        int ext = 1;
    	        while (moduleNameExists(name+(ext>1 ? ext : ""))) {
    	            ++ext;
    	        }
    	        name = name+(ext>1 ? ext : "");
    	        return name;
    	    }
	    }
	    return moduleName;
	}
	
	/**
	 * @return whether or not the given module name already exists in the 
	 * workflow.
	 */
	private boolean moduleNameExists(String moduleName) {
	    for (Enumeration<TreeNode> enum_= treeRoot.breadthFirstEnumeration(); 
            enum_.hasMoreElements();) 
	    {
	        WorkflowModuleNode node = (WorkflowModuleNode) enum_.nextElement();
	        if (moduleName != null && node.getWorkflowModule() != null && moduleName.equals(node.getWorkflowModule().getName())) 
	        {
	            return true;
	        }
	    }
	    return false;
	}
	
	public static class WorkflowModuleNode extends DefaultMutableTreeNode {
	    private WorkflowModule workflowModule;

        public WorkflowModuleNode() { }
        public WorkflowModuleNode(WorkflowModule workflowModule) {
            this.workflowModule = workflowModule;
            this.setUserObject(workflowModule.getName());
        }
        public WorkflowModule getWorkflowModule() {
            return this.workflowModule;
        }
	}
}
