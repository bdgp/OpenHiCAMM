package org.bdgp.MMSlide;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.*;

import java.awt.Dialog;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.io.File;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.bdgp.MMSlide.Dao;
import org.bdgp.MMSlide.DB.WorkflowModule;
import org.bdgp.MMSlide.DB.WorkflowModule.TaskType;

import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import net.miginfocom.swing.MigLayout;

import static org.bdgp.MMSlide.Util.where;

@SuppressWarnings("serial")
public class WorkflowDesignerDialog extends JDialog {
    private WorkflowDesignerDialog dialog;
	private JTree treeForModules;
	private WorkflowModule treeRoot;
	private JButton btnPlus;
	private JButton btnMinus;
	private JTextField moduleName;
	private JComboBox<String> moduleList;
    private JComboBox<String> taskType;
    private Connection connection;
	
	public WorkflowDesignerDialog(JFrame parent, final File workflowDirectory) {
	    super(parent, "Workflow Designer", Dialog.ModalityType.APPLICATION_MODAL);
	    dialog = this;
	    
	    this.connection = Connection.get(
	            new File(workflowDirectory, WorkflowRunner.WORKFLOW_DB).getPath());
		
        addWindowListener(new WindowAdapter() {
            public void windowClosing(final WindowEvent e) { }
        });

		treeRoot = new WorkflowModule();
		treeRoot.setUserObject("Start");
		
        setTitle("Slide imaging workflow");
        setSize(800,600);
        setLocationRelativeTo(null);
		getContentPane().setLayout(new MigLayout("", "[765.00][]", "[63px][586px][29px]"));
		
		List<String> taskTypeList = new ArrayList<String>();
		for (WorkflowModule.TaskType type : WorkflowModule.TaskType.values()) {
		    taskTypeList.add(type.name());
		}
		
		JLabel lblWorkflow = new JLabel("Module Type");
		getContentPane().add(lblWorkflow, "flowx,cell 0 0,span 2");
		
		moduleList = new JComboBox<String>();
		getContentPane().add(moduleList, "cell 0 0");
		moduleList.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		        dialog.setEnabledControls();
		        moduleName.setText(chooseModuleName((String)moduleList.getSelectedItem()));
		    }
		});
		moduleList.setToolTipText("select module");
		moduleList.setModel(new DefaultComboBoxModel<String>(
		        WorkflowRunner.getModuleNames().toArray(new String[0])));
		
		JLabel lblNewLabel = new JLabel("Module Name");
		getContentPane().add(lblNewLabel, "cell 0 0,alignx left,growy");
		
		moduleName = new JTextField();
		getContentPane().add(moduleName, "cell 0 0 2 1,growx");
		moduleName.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { dialog.setEnabledControls(); }
            @Override public void removeUpdate(DocumentEvent e) { dialog.setEnabledControls(); }
            @Override public void changedUpdate(DocumentEvent e) { dialog.setEnabledControls(); }
        });
		moduleName.setToolTipText("module name");
		moduleName.setColumns(26);
		JLabel lblTaskType = new JLabel("Task Type");
		getContentPane().add(lblTaskType, "cell 0 0");
		
		taskType = new JComboBox<String>();
		getContentPane().add(taskType, "cell 0 0");
		taskType.setModel(new DefaultComboBoxModel<String>(taskTypeList.toArray(new String[0])));
		
		btnPlus = new JButton("+");
		getContentPane().add(btnPlus, "cell 0 0");
		btnPlus.setToolTipText("add a module to the workflow");
		btnPlus.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		        TreePath path = treeForModules.getSelectionPath();
		        WorkflowModule parent = path != null
		                ? (WorkflowModule) path.getLastPathComponent()
		                : treeRoot;
		                
		        WorkflowModule module = new WorkflowModule(
		                (String)moduleName.getText(), 
		                (String)moduleList.getSelectedItem(),
		                parent.getId(),
		                TaskType.valueOf((String)taskType.getSelectedItem()));
		        
		        DefaultTreeModel model = (DefaultTreeModel) treeForModules.getModel();
		        model.insertNodeInto(module, parent, parent.getChildCount());
		        
		        // expand the tree
		        treeForModules.makeVisible(new TreePath(module.getPath()));
		        
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
		        dialog.dispose();
		    }
		});
		btnMinus.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		        TreePath path = treeForModules.getSelectionPath();
		        if (path != null) {
		            WorkflowModule module = (WorkflowModule) path.getLastPathComponent();
		            if (module != treeRoot) {
        	            DefaultTreeModel model = (DefaultTreeModel) (treeForModules.getModel());
        	            WorkflowModule parent = (WorkflowModule) module.getParent();
        	            model.removeNodeFromParent(module);
        	            treeForModules.makeVisible(new TreePath(parent.getPath()));
        	            
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
		doneButton.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
	            Dao<WorkflowModule> wf = connection.table(WorkflowModule.class, WorkflowRunner.WORKFLOW);
		        // clear the file of any existing records 
	            wf.delete();
	            // create the WorkflowModule records
	            for (@SuppressWarnings("unchecked")
                    Enumeration<WorkflowModule> enum_=treeRoot.breadthFirstEnumeration(); 
                    enum_.hasMoreElements();) 
	            {
	                WorkflowModule node = enum_.nextElement();
	                if (node != treeRoot) {
		                wf.insert(node);
	                }
	            }
	            dialog.dispose();
            }
	    });
		
		// Populate the tree model
        Dao<WorkflowModule> wf = connection.table(WorkflowModule.class, WorkflowRunner.WORKFLOW);
        DefaultTreeModel model = (DefaultTreeModel) treeForModules.getModel();
		List<WorkflowModule> modules = new ArrayList<WorkflowModule>();
		modules.add(treeRoot);
		while (modules.size() > 0) {
		    List<WorkflowModule> nextModules = new ArrayList<WorkflowModule>();
		    for (WorkflowModule module : modules) {
        		List<WorkflowModule> childModules = wf.select(where("parentId",module.getId()));
        		for (WorkflowModule child : childModules) {
    		        model.insertNodeInto(child, module, module.getChildCount());
    	            treeForModules.makeVisible(new TreePath(child.getPath()));
        		}
        		nextModules.addAll(childModules);
		    }
		    modules = nextModules;
		}
		
		setEnabledControls();
	}
	
	private void setEnabledControls() {
	    // set various buttons to be enabled/disabled depending on the state
	    // of the input fields.
	    btnPlus.setEnabled(
	            moduleList.getSelectedItem() != null &&
	            !moduleIdExists(moduleName.getText()));
        btnMinus.setEnabled(treeForModules.getSelectionCount()>0);
	}
	
	public WorkflowModule getSelectedModule() {
    	TreePath path = treeForModules.getSelectionPath();
    	if (path != null) {
    	    WorkflowModule module = (WorkflowModule) path.getLastPathComponent();
    	    return module;
    	}
    	return null;
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
    	        int ext = 0;
    	        while (moduleIdExists(name+(ext>0 ? "-"+ext : ""))) {
    	            ++ext;
    	        }
    	        name = name+(ext>0 ? "-"+ext : "");
    	        return name;
    	    }
	    }
	    return moduleName;
	}
	
	/**
	 * @return whether or not the given module name already exists in the 
	 * workflow.
	 */
	private boolean moduleIdExists(String moduleName) {
	    for (@SuppressWarnings("unchecked")
    	    Enumeration<WorkflowModule> enum_=treeRoot.breadthFirstEnumeration(); 
            enum_.hasMoreElements();) 
	    {
	        WorkflowModule node = enum_.nextElement();
	        if (moduleName != null && moduleName.equals(node.getId())) {
	            return true;
	        }
	    }
	    return false;
	}
}
