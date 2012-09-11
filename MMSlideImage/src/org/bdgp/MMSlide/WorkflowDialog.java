package org.bdgp.MMSlide;


import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.*;

import java.awt.FlowLayout;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Font;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import java.awt.Component;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;

import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;

import org.bdgp.MMSlide.Dao.Dao;
import org.bdgp.MMSlide.Dao.Config;
import org.bdgp.MMSlide.Dao.WorkflowModule;
import org.bdgp.MMSlide.Modules.Interfaces.Module;

import static org.bdgp.MMSlide.ChainMap.where;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;

@SuppressWarnings("serial")
public class WorkflowDialog extends JDialog {
    protected WorkflowDialog dialog;
	protected JTree treeForModules;
	protected JTextField storLocText;
	protected WorkflowModule treeRoot;
	protected String selectedLabel = null;
	protected ButtonGroup buttonGroup = new ButtonGroup();
	protected JComboBox<String> instanceIdList;
	protected JButton btnPlus;
	protected JButton btnMinus;
	protected JButton btnConfig;
	protected JButton btnRun;
	protected JTextField moduleName;
	protected JSpinner instanceCount;
	protected JSpinner numThreads;
	protected JComboBox<String> moduleList;
	protected boolean dataAcquisitionMode;
	protected Map<String,Config> configuration;
    protected File workflowFile;
	
	public WorkflowDialog() {
	    this(false);
	}
	public WorkflowDialog(boolean dataAcquisitionMode_) {
	    this.dataAcquisitionMode = dataAcquisitionMode_;
	    dialog = this;
	    configuration = new HashMap<String,Config>();
		
        addWindowListener(new WindowAdapter() {
            public void windowClosing(final WindowEvent e) { }
        });

        setTitle("Slide imaging workflow");
        setSize(1000,700);
        setLocationRelativeTo(null);
        
        getContentPane().setLayout(new BorderLayout(0, 0));
		
		JPanel northPanel = new JPanel();
		getContentPane().add(northPanel, BorderLayout.NORTH);
		northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
		
		JPanel filePanel = new JPanel();
		filePanel.setLayout(new FlowLayout());
		northPanel.add(filePanel);
		filePanel.add(new JLabel("Storage Location: "));
		storLocText = new JTextField();
		storLocText.setEditable(false);
		storLocText.setToolTipText("path to storage location");
		storLocText.setColumns(40);
		storLocText.setFont(new Font("Arial", Font.PLAIN, 12));
		filePanel.add(storLocText);
		JButton fileButton = new JButton("...");
		fileButton.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		        JFileChooser chooser = new JFileChooser();
		        chooser.setCurrentDirectory(
		                storLocText.getText() != null && !storLocText.getText().equals("") 
		                ? new File(storLocText.getText()) 
		                : new File("."));
		        chooser.setDialogTitle("Select a workflow directory");
		        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		        chooser.setAcceptAllFileFilterUsed(false);

		        if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
		            storLocText.setText(
    	                  chooser.getSelectedFile() != null 
    	                      ? chooser.getSelectedFile().getPath()
                          : chooser.getCurrentDirectory() != null 
                              ? chooser.getCurrentDirectory().getPath() 
                          : "");
		            
		            instanceIdList.setModel(new DefaultComboBoxModel<String>(
		                    WorkflowRunner.getInstanceIds(storLocText.getText()).toArray(new String[0])));
		        } 
		    }
		});
		filePanel.add(fileButton);
		
		JPanel modulePanel = new JPanel();
		modulePanel.setAlignmentY(Component.BOTTOM_ALIGNMENT);
		modulePanel.setLayout(new FlowLayout());
		northPanel.add(modulePanel);
		
		btnPlus = new JButton("+");
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
		                (Integer)instanceCount.getValue(),
		                parent.getId());
		        
		        DefaultTreeModel model = (DefaultTreeModel) treeForModules.getModel();
		        model.insertNodeInto(module, parent, parent.getChildCount());
		        
		        // expand the tree
		        treeForModules.makeVisible(new TreePath(module.getPath()));
		        
		        moduleName.setText(
		                chooseModuleName((String)moduleList.getSelectedItem()));
		    }
		});
		
		JPanel panel_2 = new JPanel();
		modulePanel.add(panel_2);
		panel_2.setLayout(new BoxLayout(panel_2, BoxLayout.X_AXIS));
		
		JPanel panel_3 = new JPanel();
		panel_3.setAlignmentX(Component.RIGHT_ALIGNMENT);
		panel_2.add(panel_3);
		panel_3.setLayout(new BoxLayout(panel_3, BoxLayout.Y_AXIS));
		
		JLabel lblWorkflow = new JLabel("Module Type");
		panel_3.add(lblWorkflow);
		
		moduleList = new JComboBox<String>();
		moduleList.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		        dialog.setEnabledControls();
		        moduleName.setText(chooseModuleName((String)moduleList.getSelectedItem()));
		    }
		});
		panel_3.add(moduleList);
		moduleList.setToolTipText("select module");
		moduleList.setModel(new DefaultComboBoxModel<String>(
		        WorkflowRunner.getModuleNames().toArray(new String[0])));
		btnPlus.setEnabled(moduleList.getSelectedItem() != null);
		
		JPanel panel_4 = new JPanel();
		panel_2.add(panel_4);
		panel_4.setLayout(new BoxLayout(panel_4, BoxLayout.Y_AXIS));
		
		JLabel lblNewLabel = new JLabel("Module Name");
		panel_4.add(lblNewLabel);
		
		moduleName = new JTextField();
		moduleName.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                dialog.setEnabledControls();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                dialog.setEnabledControls();
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
                dialog.setEnabledControls();
            }});
		panel_4.add(moduleName);
		moduleName.setToolTipText("module name");
		moduleName.setColumns(26);
		
		JPanel panel_5 = new JPanel();
		panel_2.add(panel_5);
		panel_5.setLayout(new BoxLayout(panel_5, BoxLayout.Y_AXIS));
		
		JLabel lblNewLabel_1 = new JLabel("Number of tasks");
		panel_5.add(lblNewLabel_1);
		
		instanceCount = new JSpinner();
		panel_5.add(instanceCount);
		instanceCount.setToolTipText("instance count");
		instanceCount.setModel(new SpinnerNumberModel(new Integer(1), new Integer(1), null, new Integer(1)));
		modulePanel.add(btnPlus);

		btnMinus = new JButton("-");
		btnMinus.setToolTipText("remove a module from the workflow");
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
		modulePanel.add(btnMinus);		
		Component horizontalGlue_1 = Box.createHorizontalGlue();
		modulePanel.add(horizontalGlue_1);
		
		btnConfig = new JButton("Config");
		btnConfig.setToolTipText("configure a workflow module");
		btnConfig.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		        WorkflowModule module = getSelectedModule();
		        if (module != null) {
		            try {
                        configuration.putAll(module.getModule().newInstance().configure());
                    } 
		            catch (InstantiationException e1) {throw new RuntimeException(e1);} 
		            catch (IllegalAccessException e1) {throw new RuntimeException(e1);}
		        }
		    }
		});
		modulePanel.add(btnConfig);		
		btnConfig.setEnabled(false);
		
		JPanel southPanel = new JPanel();
		getContentPane().add(southPanel, BorderLayout.SOUTH);
		southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.X_AXIS));
		
		JButton btnLoad = new JButton("Load");
		btnLoad.setToolTipText("load an existing workflow file");
		btnLoad.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		        JFileChooser chooser = new JFileChooser();
		        chooser.setCurrentDirectory(new java.io.File("."));
		        chooser.setDialogTitle("Load a workflow file.");
		        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		        chooser.setAcceptAllFileFilterUsed(false);
		        chooser.addChoosableFileFilter(new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        return f.getName().equals("workflow.txt");
                    }
                    @Override
                    public String getDescription() {
                        return "workflow files";
                    }});
		        if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
		            workflowFile = chooser.getSelectedFile();
		            Dao<WorkflowModule> wf = Dao.get(WorkflowModule.class, 
		                    workflowFile.getName());
		            List<WorkflowModule> modules = wf.select();
		            if (modules.size() > 0) {
		                treeRoot.removeAllChildren();
		                for (WorkflowModule module : modules) {
		                    if (module.getParentId() == null) {
		                        module.setParent(treeRoot);
		                    }
		                    else {
		                        WorkflowModule parent = wf.selectOne(
		                                where("parentId",module.getParentId()));    
		                        module.setParent(parent);
		                    }
		                }
		            }
		        }
		    }
		});
		southPanel.add(btnLoad);
		
		JButton btnSave = new JButton("Save");
		btnSave.setToolTipText("save the workflow to a file");
		btnSave.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		        JFileChooser chooser = new JFileChooser();
		        chooser.setCurrentDirectory(new java.io.File("."));
		        if (chooser.showSaveDialog(dialog) == JFileChooser.APPROVE_OPTION) {
		            File selected = chooser.getSelectedFile();
		            if (selected.exists()) {
		                selected.delete();
		            }
		            Dao<WorkflowModule> wf = Dao.get(WorkflowModule.class, 
		                    selected.getName());
		            
		            for ( @SuppressWarnings("unchecked")
                    Enumeration<WorkflowModule> enum_=treeRoot.breadthFirstEnumeration(); 
		                    enum_.hasMoreElements();) 
		            {
		                WorkflowModule node = enum_.nextElement();
		                if (node != treeRoot) {
    		                wf.insert(node);
		                }
		            }
	            }
		    }
		});
		southPanel.add(btnSave);
		Component horizontalGlue_2 = Box.createHorizontalGlue();
		southPanel.add(horizontalGlue_2);
		
		final JRadioButton newButton = new JRadioButton("New");
		newButton.setToolTipText("start a new workflow instance");
		newButton.addChangeListener(new ChangeListener() {
		    public void stateChanged(ChangeEvent e) {
		        if (buttonGroup.isSelected(newButton.getModel())) {
		            instanceIdList.setEnabled(false);
		        }
		    }
		});
		newButton.setSelected(true);
		newButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		buttonGroup.add(newButton);
		southPanel.add(newButton);
		
		final JRadioButton resumeButton = new JRadioButton("Resume");
		resumeButton.setToolTipText("resume an existing workflow instance");
		resumeButton.addChangeListener(new ChangeListener() {
		    public void stateChanged(ChangeEvent e) {
		        if (buttonGroup.isSelected(resumeButton.getModel())) {
		            instanceIdList.setEnabled(true);
		        }
		    }
		});
		buttonGroup.add(resumeButton);
		southPanel.add(resumeButton);
		
		final JRadioButton redoButton = new JRadioButton("Redo");
		redoButton.setToolTipText("redo an existing workflow instance");
		redoButton.addChangeListener(new ChangeListener() {
		    public void stateChanged(ChangeEvent e) {
		        if (buttonGroup.isSelected(redoButton.getModel())) {
		            instanceIdList.setEnabled(true);
		        }
		    }
		});
		buttonGroup.add(redoButton);
		southPanel.add(redoButton);
		
		instanceIdList = new JComboBox<String>();
		instanceIdList.setToolTipText("select an existing workflow to resume or redo");
		instanceIdList.setEnabled(false);
		southPanel.add(instanceIdList);
		
		btnRun = new JButton("Run");
		btnRun.setToolTipText("run the workflow");
		btnRun.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		        Integer instanceId = null;
		        boolean resume = false;
		        if (buttonGroup.isSelected(newButton.getModel())) { 
		            instanceId = null;
		            resume = false;
		        }
		        else if (buttonGroup.isSelected(resumeButton.getModel())) {
		            instanceId = Integer.parseInt((String)instanceIdList.getSelectedItem());
		            resume = true;
		        }
		        else if (buttonGroup.isSelected(redoButton.getModel())) {
		            instanceId = Integer.parseInt((String) instanceIdList.getSelectedItem());
		            resume = false;
		        }
		        WorkflowRunner runner = new WorkflowRunner(
		                new File(storLocText.getText()), 
		                instanceId,
		                workflowFile,
		                (Integer)numThreads.getValue(), 
		                null);
		        runner.run(resume, dataAcquisitionMode);
		    }
		});
		
		JLabel lblThreads = new JLabel(" Threads:");
		southPanel.add(lblThreads);
		
		numThreads = new JSpinner();
		numThreads.setModel(new SpinnerNumberModel(new Integer(4), new Integer(1), null, new Integer(1)));
		numThreads.setToolTipText("Number of threads");
		southPanel.add(numThreads);
		southPanel.add(btnRun);
		btnRun.setEnabled(false);
		
		treeRoot = new WorkflowModule();
		treeRoot.setUserObject("Start");
		treeForModules = new JTree(treeRoot);
		treeForModules.setToolTipText("workflow design tree");
		treeForModules.setShowsRootHandles(false);
		treeForModules.setRootVisible(true);
		treeForModules.addTreeSelectionListener(new TreeSelectionListener() {
		    public void valueChanged(TreeSelectionEvent e) {
		        pruneModuleList();
		        setEnabledControls();
		    }
		});
		
		JScrollPane scrollModulePane = new JScrollPane(treeForModules);
		getContentPane().add(scrollModulePane, BorderLayout.CENTER);
		
		setEnabledControls();
		
		treeForModules.setSelectionPath(new TreePath(treeRoot.getPath()));
		
		moduleName.setText(
		        chooseModuleName((String)moduleList.getSelectedItem()));
		try {
            storLocText.setText(new File(".").getCanonicalPath());
        } 
		catch (IOException e) {throw new RuntimeException(e);}
	}
	
	private void setEnabledControls() {
	    // set various buttons to be enabled/disabled depending on the state
	    // of the input fields.
	    btnPlus.setEnabled(
	            moduleList.getSelectedItem() != null &&
	            !moduleIdExists(moduleName.getText()));
        btnMinus.setEnabled(treeForModules.getSelectionCount()>0);
        btnConfig.setEnabled(treeForModules.getSelectionCount()>0);
        btnRun.setEnabled(treeRoot.getChildCount() > 0);
	}
	
	private void pruneModuleList() {
	    // make sure moduleList only shows module names that are successors
        // to the selected module.
        List<String> moduleNames = WorkflowRunner.getModuleNames();
        Object item = moduleList.getSelectedItem();
        moduleList.setModel(new DefaultComboBoxModel<String>(
                        moduleNames.toArray(new String[0])));
        
        TreePath path = treeForModules.getSelectionPath();
        if (path != null) {
            WorkflowModule module = (WorkflowModule) path.getLastPathComponent();
            if (module != treeRoot) {
                List<String> successorModuleNames = new ArrayList<String>();
                for (String moduleName : moduleNames) {
                    try {
                        Class<?> class_ = Class.forName(moduleName);
                        Class<Module<?>> moduleClass = module.getModule();
                        Module<?> wfModule = moduleClass.newInstance();
                        Class<?> successorInterface = wfModule.getSuccessorInterface();
                        if (successorInterface != null &&
                            successorInterface.isAssignableFrom(class_)) 
                        {
                            successorModuleNames.add(moduleName);
                        }
                    } 
                    catch (ClassNotFoundException e) {throw new RuntimeException(e);}
                    catch (InstantiationException e) {throw new RuntimeException(e);} 
                    catch (IllegalAccessException e) {throw new RuntimeException(e);}
                }
                moduleList.setModel(new DefaultComboBoxModel<String>(
                        successorModuleNames.toArray(new String[0])));           
            }
        }    
        if (item != null) {
            moduleList.setSelectedItem(item);
        }
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
	    for ( @SuppressWarnings("unchecked")
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
