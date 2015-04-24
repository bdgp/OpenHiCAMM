package org.bdgp.OpenHiCAMM;

import ij.ImagePlus;
import ij.process.ImageProcessor;

import javax.swing.JFrame;

import mmcorej.TaggedImage;
import net.miginfocom.swing.MigLayout;

import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.JScrollPane;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import javax.swing.ListSelectionModel;

import org.micromanager.acquisition.MMImageCache;
import org.micromanager.acquisition.TaggedImageStorageLive;
import org.micromanager.api.ImageCache;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MMScriptException;

@SuppressWarnings("serial")
public class ImageLog extends JFrame {
    private JTable table;
    private List<FutureTask<ImageLogRecord>> records;

    public void setRecords(List<FutureTask<ImageLogRecord>> records) {
        this.records.clear();
        this.records.addAll(records);
    }

    public ImageLog() {
        super("Image Log");
        this.records = new ArrayList<FutureTask<ImageLogRecord>>();

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
                try {
                    return columnIndex == 0? ImageLog.this.records.get(rowIndex).get().taskName : 
                           columnIndex == 1? ImageLog.this.records.get(rowIndex).get().imageStackName : 
                           null;
                } 
                catch (InterruptedException e) {throw new RuntimeException(e);} 
                catch (ExecutionException e) {throw new RuntimeException(e);}
            }
            @Override public boolean isCellEditable(int row, int col) { 
                return false; 
            }
        });
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.addKeyListener(new KeyAdapter() {
            @Override public void keyTyped(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && table.getSelectedRow() >= 0) {
                    try { ImageLog.this.records.get(table.getSelectedRow()).get().display(); } 
                    catch (InterruptedException e1) {throw new RuntimeException(e1);} 
                    catch (ExecutionException e1) {throw new RuntimeException(e1);}
                }
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    try { ImageLog.this.records.get(table.getSelectedRow()).get().display(); } 
                    catch (InterruptedException e1) {throw new RuntimeException(e1);} 
                    catch (ExecutionException e1) {throw new RuntimeException(e1);}
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
        private VirtualAcquisitionDisplay vad;

        public ImageLogRecord(
                String taskName, 
                String imageStackName) 
        {
            this.taskName = taskName;
            this.imageStackName = imageStackName;
            if (this.taskName != null) {
                try { 
                    TaggedImageStorage storage = new TaggedImageStorageLive();
                    ImageCache cache = new MMImageCache(storage);
                    this.vad = new VirtualAcquisitionDisplay(cache, this.imageStackName); 
                } 
                catch (MMScriptException e) {throw new RuntimeException(e);}
            }
        }
        
        public void addImage(TaggedImage image, String description) {
            ImageProcessor ip = ImageUtils.makeProcessor(image);
            Font font = new Font("SansSerif", Font.PLAIN, 8);
            ip.setFont(font);
            ip.setColor(new Color(1.0f, 1.0f, 1.0f));
            ip.drawString(description, 0, 0);
            this.vad.getHyperImage().getImageStack().addSlice(ip);
        }
        public void addImage(ImagePlus imp, String description) {
            ImageProcessor ip = imp.getProcessor();
            Font font = new Font("SansSerif", Font.PLAIN, 8);
            ip.setFont(font);
            ip.setColor(new Color(1.0f, 1.0f, 1.0f));
            ip.drawString(description, 0, 0);
            this.vad.getHyperImage().getImageStack().addSlice(ip);
        }
        
        public void display() {
            vad.show();
        }
    }
    
    public static class NullImageLogRecord extends ImageLogRecord {
        public NullImageLogRecord() {
            super(null, null);
        }
        public void addImage(TaggedImage image) { }
        public void display() {}
    }
}
