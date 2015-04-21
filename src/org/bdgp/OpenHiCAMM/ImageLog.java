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
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

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
    public ImageLog(final ImageLogRecord[] records) {
        super("Image Log");
        setLayout(new MigLayout("", "[grow]", "[grow][grow]"));
        
        table = new JTable(new AbstractTableModel() {
            String[] colNames = new String[] {"Module ID", "Image Stack Name"};
            @Override public String getColumnName(int col) {
                return colNames[col];
            }
            @Override public int getRowCount() {
                return records.length;
            }
            @Override public int getColumnCount() {
                return colNames.length;
            }
            @Override public Object getValueAt(int rowIndex, int columnIndex) {
                return columnIndex == 0? records[rowIndex].moduleId : 
                       columnIndex == 1? records[rowIndex].imageStackName : 
                       null;
            }
            @Override public boolean isCellEditable(int row, int col) { 
                return false; 
            }
        });
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.addKeyListener(new KeyAdapter() {
            @Override public void keyTyped(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    records[table.getSelectedRow()].display();
                }
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    records[table.getSelectedRow()].display();
                }
            }
        });
        table.setFillsViewportHeight(true);
        table.setColumnSelectionAllowed(true);
        table.setAutoCreateRowSorter(true);

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, "cell 0 0,grow");
    }
    
    public static class ImageLogRecord {
        private String moduleId;
        private String imageStackName;
        private VirtualAcquisitionDisplay vad;

        public ImageLogRecord(
                String moduleId, 
                String imageStackName) 
        {
            this.moduleId = moduleId;
            this.imageStackName = imageStackName;
            if (this.moduleId != null) {
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
