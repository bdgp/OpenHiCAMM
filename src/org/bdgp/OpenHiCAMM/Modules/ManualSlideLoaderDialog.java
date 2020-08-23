package org.bdgp.OpenHiCAMM.Modules;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.bdgp.OpenHiCAMM.Connection;
import org.bdgp.OpenHiCAMM.Dao;
import org.bdgp.OpenHiCAMM.WorkflowRunner;
import org.bdgp.OpenHiCAMM.DB.Pool;
import org.bdgp.OpenHiCAMM.DB.PoolSlide;
import org.bdgp.OpenHiCAMM.DB.Slide;

import javax.swing.JList;

import net.miginfocom.swing.MigLayout;

import javax.swing.JTextPane;

import java.awt.Dimension;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import javax.swing.JLabel;
import javax.swing.JTextArea;

import static org.bdgp.OpenHiCAMM.Util.where;

import javax.swing.JSpinner;

@SuppressWarnings("serial")
public class ManualSlideLoaderDialog extends JTabbedPane {
	JList<String> poolList;
	JTextArea poolDescription;
    ButtonGroup slideScanButtonGroup = new ButtonGroup();
    ButtonGroup slideLoaderGroup;
    
    ManualSlideLoader slideLoader;
    WorkflowRunner workflowRunner;
    Integer cartridge;
    Integer slide;

	public ManualSlideLoaderDialog(ManualSlideLoader slideLoader, WorkflowRunner workflowRunner) {
	    this.slideLoader = slideLoader;
	    this.workflowRunner = workflowRunner;
	    Connection connection = this.workflowRunner.getWorkflowDb();

        Dao<Slide> slideDao = connection.table(Slide.class);
        Dao<PoolSlide> poolSlideDao = connection.table(PoolSlide.class);
		
		slideLoaderGroup = new ButtonGroup();

		// Pool (define new pool or select previous one)
		JPanel poolPanel = new JPanel();
		this.addTab("Pool", null, poolPanel, null);
		poolPanel.setLayout(new MigLayout("", "[256px,grow][1px]", "[64px,center][][grow][]"));
		
		JPanel panelSelectPool = new JPanel();
		poolPanel.add(panelSelectPool, "cell 0 0,alignx left,aligny top");
		panelSelectPool.setLayout(new MigLayout("", "[179.00px]", "[pref!,grow][][100px]"));
		
		// Populate the previous pool list from the Pool table
		final Dao<Pool> poolDao = connection.table(Pool.class);
		List<Pool> pools = poolDao.select();
		List<String> pool_names = new ArrayList<String>();
		for (Pool p : pools) {
		    pool_names.add(p.getName());
		}
		Collections.sort(pool_names);
		
		final JLabel lblSelectPool = new JLabel("Select Pool:");
		panelSelectPool.add(lblSelectPool, "flowx,cell 0 0,aligny top");
		
		poolList = new JList<String>();
		poolList.setVisibleRowCount(-1);
		poolList.setListData(pool_names.toArray(new String[0]));
		poolList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		poolList.setLayoutOrientation(JList.VERTICAL);
		final JScrollPane poolListPane = new JScrollPane(poolList);
		poolListPane.setEnabled(false);
		poolListPane.setPreferredSize(new Dimension(200, 200));
		panelSelectPool.add(poolListPane, "cell 0 0,grow");
		// Populate the pool description if another pool ID was selected
		JScrollPane poolPane = new JScrollPane();
		poolPanel.add(poolPane, "cell 0 2,grow");
		
		final JTextPane poolDescriptionHeader = new JTextPane();
		poolDescriptionHeader.setText("Cartridge Position\tSlide Position\tExperiment ID");
		poolDescriptionHeader.setEditable(false);
		poolDescriptionHeader.setFont(new Font(
		        poolDescriptionHeader.getFont().getName(), 
		        Font.BOLD, 
		        poolDescriptionHeader.getFont().getSize()));
		poolPane.setColumnHeaderView(poolDescriptionHeader);
		
		poolDescription = new JTextArea();
		poolPane.setViewportView(poolDescription);
		
		JPanel panel = new JPanel();
		poolPanel.add(panel, "cell 0 1,grow");
		panel.setLayout(new MigLayout("", "[]", "[]"));
		
		final JTextPane poolInstructions = new JTextPane();
		poolInstructions.setText("Create New Pool:\n\nText must be whitespace-separated with three columns: Cartridge Position, Slide Position, Experiment ID. The Cartride Position and Slide Position must be non-negative integers, the Experiment ID can be any text string.\n\nYou may want to simply copy-paste from your favorite spreadsheet editor.");
		poolInstructions.setEditable(false);
		panel.add(poolInstructions, "cell 0 0,grow");
		
		final JButton btnCreateNewPool = new JButton("Create New Pool");
		poolPanel.add(btnCreateNewPool, "flowx,cell 0 3");

		btnCreateNewPool.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		        // Create the pool record
	            Dao<Pool> poolDao = connection.table(Pool.class);
	            Pool pool = new Pool();
	            poolDao.insert(pool);

	            // Create poolslide+slide records if we're not scanning for slides
                BufferedReader poolReader = new BufferedReader(new StringReader(poolDescription.getText()));
                Set<PoolSlide> poolSlides = new LinkedHashSet<PoolSlide>();
                List<Slide> slides = new ArrayList<Slide>();
                try {
                    String line;
                    int linenum=1;
                    while ((line = poolReader.readLine()) != null) {
                        // skip blank lines
                        if (line.matches("^\\s*$")) continue;

                        String[] fields = line.split("[\t ,]", 3);
                        if (fields.length < 3) {
                            JOptionPane.showMessageDialog(ManualSlideLoaderDialog.this, "Line "+linenum+": Only "+fields.length+" fields found (should be 3)", 
                                    "Pool Description Formatting Error", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        if (!fields[0].matches("^[0-9]+$") || Integer.parseInt(fields[0]) < 1 || Integer.parseInt(fields[0]) > 4) {
                            JOptionPane.showMessageDialog(ManualSlideLoaderDialog.this, "Line "+linenum+": Cartridge Position \""+fields[0]+"\" is invalid",
                                    "Pool Description Formatting Error", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        if (!fields[1].matches("^[0-9]+$") || Integer.parseInt(fields[1]) < 1 || Integer.parseInt(fields[1]) > 50) {
                            JOptionPane.showMessageDialog(ManualSlideLoaderDialog.this, "Line "+linenum+": Slide Position \""+fields[1]+"\" is invalid",
                                    "Pool Description Formatting Error", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        Slide slide = new Slide(fields[2]);
                        PoolSlide poolSlide = new PoolSlide( 0,
                                Integer.parseInt(fields[0]), 
                                Integer.parseInt(fields[1]), 
                                        slides.size());
                        if (poolSlides.contains(poolSlide)) {
                            JOptionPane.showMessageDialog(ManualSlideLoaderDialog.this, "Line "+linenum+": Slide Position "+fields[0]+","+fields[1]+"\" was already entered",
                                    "Pool Description Formatting Error", JOptionPane.ERROR_MESSAGE);
                            return;                          
                        }
                        slides.add(slide);
                        poolSlides.add(poolSlide);
                        linenum++;
                    }
                } 
                catch (IOException e1) {throw new RuntimeException(e1);}
                
                // Create the poolslide and slide records
                for (PoolSlide poolSlide : poolSlides) {
                    Slide slide = slides.get(poolSlide.getSlideId());
                    slideDao.insert(slide);
                    poolSlideDao.insert(new PoolSlide(pool.getId(), 
                            poolSlide.getCartridgePosition(), 
                            poolSlide.getSlidePosition(), 
                            slide.getId()));
                }

    		    // Update the pool list and select the newly created pool
        		List<Pool> pools = poolDao.select();
        		List<String> pool_names = new ArrayList<String>();
        		for (Pool p : pools) {
        		    pool_names.add(p.getName());
        		}
        		Collections.sort(pool_names);
        		poolList.setListData(pool_names.toArray(new String[0]));
        		poolList.setSelectedValue(pool.getName(), true);
		    }
		});

        // Create an initial pool record if no pool records exist yet
		if (poolDao.select().size() == 0) {
            final Pool pool = new Pool();
            poolDao.insert(pool);
            updatePoolList(connection);
		}
		
		// Make sure there is always a selection
		SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                if (poolList.getModel().getSize() > 0 && poolList.getSelectedIndex() < 0) {
                    poolList.setSelectedIndex(0);
                }
            }});
		
		// Update the pool list when the tab is made visible
		this.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) {
                ManualSlideLoaderDialog.this.updatePoolList(connection);
            }});
		
		poolList.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
			    String name = (String)poolList.getSelectedValue();
        		List<Pool> pools = poolDao.select();
			    for (Pool p : pools) {
			        if (p.getName().equals(name)) {
			            StringBuilder sb = new StringBuilder();
			            List<PoolSlide> poolSlides = poolSlideDao.select(where("poolId",p.getId()));
			            Collections.sort(poolSlides);
			            for (PoolSlide poolSlide : poolSlides) {
			                Slide slide = slideDao.selectOne(where("id",poolSlide.getSlideId()));
			                sb.append(String.format("%d\t%d\t%s%n", 
			                        poolSlide.getCartridgePosition(), 
			                        poolSlide.getSlidePosition(), 
			                        slide.getExperimentId()));
			            }
			            poolDescription.setText(sb.toString());
			            return;
			        }
			    }
			}
		});

	}
	
    /**
     *  Update the pool list and select the newly created pool
     * @param connection
     */
	public void updatePoolList(Connection connection) {
		final Dao<Pool> poolDao = connection.table(Pool.class);
	    final String poolName = poolList.getSelectedValue();
        List<Pool> pools = poolDao.select();
        final List<String> pool_names = new ArrayList<String>();
        for (Pool p : pools) {
            pool_names.add(p.getName());
        }
        Collections.sort(pool_names);
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                poolList.setListData(pool_names.toArray(new String[0]));
                poolList.setSelectedValue(poolName, true);
            }
        });
	}
	
    public static class DoubleSpinner extends JSpinner {

        private static final long serialVersionUID = 1L;
        private static final double STEP_RATIO = 0.1;

        private SpinnerNumberModel model;

        public DoubleSpinner() {
            super();
            // Model setup
            model = new SpinnerNumberModel(0.0, -1000000.0, 1000000.0, 1.0);
            this.setModel(model);

            // Step recalculation
            this.addChangeListener(new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent e) {
                    Double value = getDouble();
                    // Steps are sensitive to the current magnitude of the value
                    long magnitude = Math.round(Math.log10(value));
                    double stepSize = STEP_RATIO * Math.pow(10, magnitude);
                    model.setStepSize(stepSize);
                }
            });
        }

        /**
         * Returns the current value as a Double
         */
        public Double getDouble() {
            return (Double)getValue();
        }

    }
}
