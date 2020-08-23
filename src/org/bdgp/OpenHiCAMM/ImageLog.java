package org.bdgp.OpenHiCAMM;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

import javax.swing.JFrame;

import mmcorej.TaggedImage;
import net.miginfocom.swing.MigLayout;

import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.JScrollPane;

import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import javax.swing.ListSelectionModel;

import org.micromanager.internal.utils.imageanalysis.ImageUtils;


@SuppressWarnings("serial")
public class ImageLog extends JFrame {
    private JTable table;
    private List<ImageLogRecord> records;

    public void setRecords(List<ImageLogRecord> records) {
        this.records.clear();
        this.records.addAll(records);
    }

    public ImageLog(List<ImageLogRecord> records) {
        super("Image Log");
        this.records = new ArrayList<ImageLogRecord>();
        this.setRecords(records);

        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        getContentPane().setLayout(new MigLayout("", "[300px:n,grow]", "[100px:n,grow]"));
        
        table = new JTable(new AbstractTableModel() {
            String[] colNames = new String[] {"Task Name", "Image Stack Name"};
            @Override public String getColumnName(int col) {
                return colNames[col];
            }
            @Override public int getRowCount() {
                return ImageLog.this.records.size();
            }
            @Override public int getColumnCount() {
                return colNames.length;
            }
            @Override public Object getValueAt(int rowIndex, int columnIndex) {
                return columnIndex == 0? ImageLog.this.records.get(rowIndex).getTaskName() : 
                       columnIndex == 1? ImageLog.this.records.get(rowIndex).getImageStackName() : 
                       null;
            }
            @Override public boolean isCellEditable(int row, int col) { 
                return false; 
            }
        });
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.addKeyListener(new KeyAdapter() {
            @Override public void keyTyped(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && table.getSelectedRow() >= 0) {
                    FutureTask<ImageLogRunner> futureTask = ImageLog.this.records.get(table.getSelectedRow()).getRunner();
                    futureTask.run();
                    try { 
                        ImageLogRunner imageLogRunner = futureTask.get();
                        if (imageLogRunner != null) imageLogRunner.display(); 
                    }
                    catch (InterruptedException e1) {throw new RuntimeException(e1);} 
                    catch (ExecutionException e1) {throw new RuntimeException(e1);}
                }
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (table.getSelectedRow() >= 0 && e.getClickCount() == 2) {
                    FutureTask<ImageLogRunner> futureTask = ImageLog.this.records.get(table.getSelectedRow()).getRunner();
                    ImageLogRunner imageLogRunner;
                    try {
                        ij.macro.Interpreter.batchMode = true;
                        futureTask.run();
                        imageLogRunner = futureTask.get();
                    } 
                    catch (InterruptedException e1) {throw new RuntimeException(e1);} 
                    catch (ExecutionException e1) {throw new RuntimeException(e1);}
                    finally {
                        ij.macro.Interpreter.batchMode = false;
                    }
                    if (imageLogRunner != null) imageLogRunner.display();
                }
            }
        });
        table.setFillsViewportHeight(true);
        table.setColumnSelectionAllowed(true);
        table.setAutoCreateRowSorter(true);

        JScrollPane scrollPane = new JScrollPane(table);
        getContentPane().add(scrollPane, "cell 0 0,grow");
        
        this.getContentPane().setPreferredSize(new Dimension(500, 500));
        this.pack(); 
    }
    
    public static class ImageLogRecord {
        private String taskName;
        private String imageStackName;
        private FutureTask<ImageLogRunner> runner;

        public ImageLogRecord(
                String taskName, 
                String imageStackName,
                FutureTask<ImageLogRunner> runner) 
        {
            this.taskName = taskName;
            this.imageStackName = imageStackName;
            this.runner = runner;
        }
        
        public FutureTask<ImageLogRunner> getRunner() { return this.runner; }
        public String getTaskName() { return this.taskName; }
        public String getImageStackName() { return this.imageStackName; }
        
    }
    
    public static class ImageLogRunner {
        private ImageStack imageStack;
        private String imageStackName;
        
        public ImageLogRunner(String imageStackName) {
            this.imageStackName = imageStackName;
        }
        
        public void addImage(TaggedImage image, String description) {
            ImageProcessor ip = ImageUtils.makeProcessor(image);
            ip = ip.duplicate();
            if (this.imageStack == null) {
                this.imageStack = new ImageStack(ip.getWidth(), ip.getHeight());
            }
            if (ip.getWidth() != this.imageStack.getWidth() || ip.getHeight() != this.imageStack.getHeight()) {
                ip = ip.resize(this.imageStack.getWidth(), this.imageStack.getHeight());
            }
            this.imageStack.addSlice(description, ip);
        }
        public void addImage(ImagePlus imp, String description) {
            ImageProcessor ip = imp.getProcessor();
            ip = ip.duplicate();
            if (this.imageStack == null) {
                this.imageStack = new ImageStack(ip.getWidth(), ip.getHeight());
            }
            if (ip.getWidth() != this.imageStack.getWidth() || ip.getHeight() != this.imageStack.getHeight()) {
                ip = ip.resize(this.imageStack.getWidth(), this.imageStack.getHeight());
            }
            this.imageStack.addSlice(description, ip);
        }
        
        public void display() {
            if (imageStack != null) {
                ImagePlus imp = new ImagePlus(this.imageStackName, this.imageStack);
                imp.show();
            }
        }
    }
    
    public static class NullImageLogRunner extends ImageLogRunner {
        public NullImageLogRunner() {
            super(null);
        }
        @Override public void addImage(TaggedImage image, String description) { }
        @Override public void addImage(ImagePlus image, String description) { }
        @Override public void display() { }
    }
}
