package org.bdgp.OpenHiCAMM.Modules;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.bdgp.OpenHiCAMM.Connection;
import org.bdgp.OpenHiCAMM.Dao;
import org.bdgp.OpenHiCAMM.OpenHiCAMM;
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

import javax.swing.JTextField;
import javax.swing.JSpinner;

@SuppressWarnings("serial")
public class SlideLoaderDialog extends JTabbedPane {
	public JList<String> poolList;
	public JRadioButton radioButtonSlideLoader;
	public JRadioButton radioButtonSlideManual;
	public JTextField device;

    public DoubleSpinner initXCoord;
    public DoubleSpinner initYCoord;
    public DoubleSpinner loadXCoord;
    public DoubleSpinner loadYCoord;
	
	public SlideLoaderDialog(final Connection connection, final OpenHiCAMM mmslide) {
        final Dao<Slide> slideDao = connection.table(Slide.class);
        final Dao<PoolSlide> poolSlideDao = connection.table(PoolSlide.class);

		// Pool (define new pool or select previous one)
		JPanel poolPanel = new JPanel();
		this.addTab("Pool", null, poolPanel, null);
		poolPanel.setLayout(new MigLayout("", "[256px,grow][1px]", "[64px,center][][grow][]"));
		
		JPanel panelSelectPool = new JPanel();
		poolPanel.add(panelSelectPool, "cell 0 0,alignx left,aligny top");
		panelSelectPool.setLayout(new MigLayout("", "[77px][114px,grow]", "[100px][100px:100px,grow]"));
		
		// Populate the previous pool list from the Pool table
		final Dao<Pool> poolDao = connection.table(Pool.class);
		List<Pool> pools = poolDao.select();
		List<String> pool_names = new ArrayList<String>();
		for (Pool p : pools) {
		    pool_names.add(p.getName());
		}
		Collections.sort(pool_names);
		
		JLabel lblSelectPool = new JLabel("Select Pool:");
		panelSelectPool.add(lblSelectPool, "cell 0 1,aligny top");
		
		poolList = new JList<String>();
		poolList.setVisibleRowCount(-1);
		poolList.setListData(pool_names.toArray(new String[0]));
		poolList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		poolList.setLayoutOrientation(JList.VERTICAL);
		poolList.setEnabled(true);
		JScrollPane poolListPane = new JScrollPane(poolList);
		poolListPane.setPreferredSize(new Dimension(200, 200));
		panelSelectPool.add(poolListPane, "cell 1 1,grow");

		JScrollPane poolPane = new JScrollPane();
		poolPanel.add(poolPane, "cell 0 2,grow");
		
		JTextPane poolDescriptionHeader = new JTextPane();
		poolDescriptionHeader.setText("Cartridge Position\tSlide Position\tExperiment ID");
		poolDescriptionHeader.setEditable(false);
		poolDescriptionHeader.setFont(new Font(
		        poolDescriptionHeader.getFont().getName(), 
		        Font.BOLD, 
		        poolDescriptionHeader.getFont().getSize()));
		poolPane.setColumnHeaderView(poolDescriptionHeader);
		
		final JTextArea poolDescription = new JTextArea();
		poolPane.setViewportView(poolDescription);
		
		JPanel panel = new JPanel();
		poolPanel.add(panel, "cell 0 1,grow");
		panel.setLayout(new MigLayout("", "[]", "[]"));
		
		JTextPane poolInstructions = new JTextPane();
		poolInstructions.setText("Create New Pool:\n\nText must be whitespace-separated with three columns: Cartridge Position, Slide Position, Experiment ID. The Cartride Position and Slide Position must be non-negative integers, the Experiment ID can be any text string.\n\nYou may want to simply copy-paste from your favorite spreadsheet editor.");
		poolInstructions.setEditable(false);
		panel.add(poolInstructions, "cell 0 0,grow");
		
		JButton btnCreateNewPool = new JButton("Create New Pool");
		btnCreateNewPool.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		        BufferedReader poolReader = new BufferedReader(new StringReader(poolDescription.getText()));
	            Set<PoolSlide> poolSlides = new HashSet<PoolSlide>();
	            List<Slide> slides = new ArrayList<Slide>();
		        try {
    		        String line;
    		        int linenum=1;
                    while ((line = poolReader.readLine()) != null) {
                        // skip blank lines
                        if (line.matches("^\\s*$")) continue;

                        String[] fields = line.split("[\t ,]", 3);
                        if (fields.length < 3) {
                            JOptionPane.showMessageDialog(SlideLoaderDialog.this, "Line "+linenum+": Only "+fields.length+" fields found (should be 3)", 
                                    "Pool Description Formatting Error", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        if (!fields[0].matches("^[0-9]$") || new Integer(fields[0]).intValue() < 1) {
                            JOptionPane.showMessageDialog(SlideLoaderDialog.this, "Line "+linenum+": Cartridge Position \""+fields[0]+"\" is invalid",
                                    "Pool Description Formatting Error", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        if (!fields[1].matches("^[0-9]$") || new Integer(fields[1]).intValue() < 1) {
                            JOptionPane.showMessageDialog(SlideLoaderDialog.this, "Line "+linenum+": Slide Position \""+fields[1]+"\" is invalid",
                                    "Pool Description Formatting Error", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        Slide slide = new Slide(fields[2]);
                        PoolSlide poolSlide = new PoolSlide( 0,
                                new Integer(fields[0]).intValue(), 
                                new Integer(fields[1]).intValue(), 
                                        slides.size());
                        if (poolSlides.contains(poolSlide)) {
                            JOptionPane.showMessageDialog(SlideLoaderDialog.this, "Line "+linenum+": Slide Position "+fields[0]+","+fields[1]+"\" was already entered",
                                    "Pool Description Formatting Error", JOptionPane.ERROR_MESSAGE);
                            return;                          
                        }
                        slides.add(slide);
                        poolSlides.add(poolSlide);
                        linenum++;
                    }
                } 
		        catch (IOException e1) {throw new RuntimeException(e1);}
		        
		        // Create the pool record
	            Dao<Pool> poolDao = connection.table(Pool.class);
	            Pool pool = new Pool();
	            poolDao.insert(pool);

	            // Create the poolslide and slide records
		        for (PoolSlide poolSlide : poolSlides) {
		            Slide slide = slides.get(poolSlide.getSlideId());
		            slideDao.insertOrUpdate(slide,"experimentId");
		            slide = slideDao.reload(slide);
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
		poolPanel.add(btnCreateNewPool, "cell 0 3");
		// Populate the pool description if another pool ID was selected
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

		JPanel loaderPanel = new JPanel();
		this.addTab("Loading", null, loaderPanel, null);
		loaderPanel.setLayout(new MigLayout("", "[195px,grow]", "[22px][22px][][][][][][][]"));
		
		ButtonGroup slideLoaderGroup = new ButtonGroup();
		radioButtonSlideLoader = new JRadioButton("Slide loader");
		loaderPanel.add(radioButtonSlideLoader, "cell 0 0,alignx left,aligny center");
		slideLoaderGroup.add(radioButtonSlideLoader);
		
		radioButtonSlideManual = new JRadioButton("Manual (will prompt for slides)");
		radioButtonSlideManual.setSelected(true);
		loaderPanel.add(radioButtonSlideManual, "cell 0 1,alignx left,aligny center");
		slideLoaderGroup.add(radioButtonSlideManual);
		
		JLabel lblEnterFullPath = new JLabel("Enter full path to Slide Loader Device:");
		loaderPanel.add(lblEnterFullPath, "cell 0 3");
		
		device = new JTextField();
		device.setText("/dev/tty.usbserial-FTEKUITV");
		loaderPanel.add(device, "cell 0 4,growx");
		device.setColumns(10);
		
		JLabel lblEnterInitialStage = new JLabel("Enter Initial Stage X/Y Coordinates:");
		loaderPanel.add(lblEnterInitialStage, "cell 0 5");
		
		JLabel lblXCoordinate = new JLabel("X Coordinate:");
		loaderPanel.add(lblXCoordinate, "flowx,cell 0 6");
		
		initXCoord = new DoubleSpinner();
		initXCoord.setValue(new Double(0.0));
		loaderPanel.add(initXCoord, "cell 0 6");
		
		JLabel lblYCoordinate = new JLabel("Y Coordinate:");
		loaderPanel.add(lblYCoordinate, "cell 0 6");
		
		initYCoord = new DoubleSpinner();
		initYCoord.setValue(new Double(0.0));
		loaderPanel.add(initYCoord, "cell 0 6");
		
		JLabel lblEnterLoadingStage = new JLabel("Enter Loading Stage X/Y Coordinates:");
		loaderPanel.add(lblEnterLoadingStage, "cell 0 7");
		
		JLabel lblXCoordinate_1 = new JLabel("X Coordinate:");
		loaderPanel.add(lblXCoordinate_1, "flowx,cell 0 8");
		
		loadXCoord = new DoubleSpinner();
		loadXCoord.setValue(new Double(-115372.0));
		loaderPanel.add(loadXCoord, "cell 0 8");
		
		JLabel lblYCoordinate_1 = new JLabel("Y Coordinate:");
		loaderPanel.add(lblYCoordinate_1, "cell 0 8");
		
		loadYCoord = new DoubleSpinner();
		loadYCoord.setValue(new Double(-77000.0));
		loaderPanel.add(loadYCoord, "cell 0 8");
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
