package org.bdgp.MMSlide;

import java.io.StringReader;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonReader;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JFormattedTextField;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.ListModel;
import javax.swing.table.TableModel;

import org.bdgp.MMSlide.DB.Config;
import org.bdgp.MMSlide.Modules.Interfaces.Configuration;

public class StorableConfiguration implements Configuration {
    private JPanel panel;
    
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Storable {};

    public StorableConfiguration(JPanel panel) {
        this.panel = panel;
    }

    /**
     * Use introspection to retrieve a list of configs for the panel.
     */
    @SuppressWarnings("rawtypes")
    @Override
    public List<Config> retrieve() {
        List<Config> configs = new ArrayList<Config>();
        Field[] fields = this.panel.getClass().getDeclaredFields();
        try {
            for (Field field : fields) {
                if (!field.isAnnotationPresent(Storable.class)) continue;
                String value = null;
                if (JTextField.class.isAssignableFrom(field.getClass())) {
                    value = ((JTextField)(field.get(this.panel))).getText();
                }
                else if (JTextArea.class.isAssignableFrom(field.getClass())) {
                    value = ((JTextArea)(field.get(this.panel))).getText();
                }
                else if (JFormattedTextField.class.isAssignableFrom(field.getClass())) {
                    value = ((JFormattedTextField)(field.get(this.panel))).getText();
                }
                else if (JEditorPane.class.isAssignableFrom(field.getClass())) {
                    value = ((JEditorPane)(field.get(this.panel))).getText();
                }
                else if (JTextPane.class.isAssignableFrom(field.getClass())) {
                    value = ((JTextPane)(field.get(this.panel))).getText();
                }
                else if (JPasswordField.class.isAssignableFrom(field.getClass())) {
                    value = new String(((JPasswordField)(field.get(this.panel))).getPassword());
                }
                else if (JComboBox.class.isAssignableFrom(field.getClass())) {
                    Object item = ((JComboBox)(field.get(this.panel))).getSelectedItem();
                    if (item != null) value = item.toString();
                }
                else if (JCheckBox.class.isAssignableFrom(field.getClass())) {
                    value = ((JCheckBox)(field.get(this.panel))).isSelected()? "true" : "false";
                }
                else if (JRadioButton.class.isAssignableFrom(field.getClass())) {
                    value = ((JRadioButton)(field.get(this.panel))).isSelected()? "true" : "false";
                }
                else if (JToggleButton.class.isAssignableFrom(field.getClass())) {
                    value = ((JToggleButton)(field.get(this.panel))).isSelected()? "true" : "false";
                }
                else if (JSpinner.class.isAssignableFrom(field.getClass())) {
                    Object val = ((JSpinner)(field.get(this.panel))).getModel().getValue();
                    if (val != null) value = val.toString();
                }
                else if (JSlider.class.isAssignableFrom(field.getClass())) {
                    value = new Integer(((JSlider)(field.get(this.panel))).getValue()).toString();
                }
                else if (JList.class.isAssignableFrom(field.getClass())) {
                    // TODO: Handle non-string models
                    int[] indices = ((JList)(field.get(this.panel))).getSelectedIndices();
                    ListModel model = ((JList)(field.get(this.panel))).getModel();
                    JsonArrayBuilder j = Json.createArrayBuilder();
                    for (int i=1; i < indices.length; ++i) {
                        Object o = model.getElementAt(i);
                        j.add(o.toString());
                    }
                    value = j.build().toString();
                }
                else if (JTable.class.isAssignableFrom(field.getClass())) { 
                    TableModel model = ((JTable)(field.get(this.panel))).getModel();
                    JsonArrayBuilder rows = Json.createArrayBuilder();
                    for (int i=0; i < model.getRowCount(); ++i) {
                        JsonArrayBuilder row = Json.createArrayBuilder();
                        for (int j=0; j < model.getColumnCount(); ++j) {
                            Object val = model.getValueAt(i, j);
                            row.add(val == null? null : val.toString());
                        }
                        rows.add(row.build());
                    }
                    value = rows.toString();
                }
                // TODO: handle tree models
                // else if (JTree.class.isAssignableFrom(field.getClass())) { }
                if (value != null) configs.add(new Config(null, field.getName(), value));
            }
        }
        catch (IllegalArgumentException e) {throw new RuntimeException(e);} 
        catch (IllegalAccessException e) {throw new RuntimeException(e);}
        return configs;
    }
    
    @SuppressWarnings("rawtypes")
    @Override
    public JPanel display(List<Config> configs) {
        Map<String,Config> conf = new HashMap<String,Config>();
        for (Config config : configs) {
            conf.put(config.getKey(), config);
        }
        Field[] fields = this.panel.getClass().getDeclaredFields();

        try {
            for (Field field : fields) {
                if (!field.isAnnotationPresent(Storable.class)) continue;
                Config config = conf.get(field.getName());
                if (config == null || config.getValue() == null) continue;
                String value = config.getValue();
    
                if (JTextField.class.isAssignableFrom(field.getClass())) {
                    ((JTextField)(field.get(this.panel))).setText(value);
                }
                else if (JTextArea.class.isAssignableFrom(field.getClass())) {
                    ((JTextArea)(field.get(this.panel))).setText(value);
                }
                else if (JFormattedTextField.class.isAssignableFrom(field.getClass())) {
                    ((JFormattedTextField)(field.get(this.panel))).setText(value);
                }
                else if (JEditorPane.class.isAssignableFrom(field.getClass())) {
                    ((JEditorPane)(field.get(this.panel))).setText(value);
                }
                else if (JTextPane.class.isAssignableFrom(field.getClass())) {
                    ((JTextPane)(field.get(this.panel))).setText(value);
                }
                else if (JPasswordField.class.isAssignableFrom(field.getClass())) {
                    ((JPasswordField)(field.get(this.panel))).setText(value);
                }
                else if (JComboBox.class.isAssignableFrom(field.getClass())) {
                    // TODO: handle non-string Models
                    ((JComboBox)(field.get(this.panel))).setSelectedItem(value);
                }
                else if (JCheckBox.class.isAssignableFrom(field.getClass())) {
                    ((JCheckBox)(field.get(this.panel))).setSelected(value.equals("true")? true : false);
                }
                else if (JRadioButton.class.isAssignableFrom(field.getClass())) {
                    ((JRadioButton)(field.get(this.panel))).setSelected(value.equals("true")? true : false);
                }
                else if (JToggleButton.class.isAssignableFrom(field.getClass())) {
                    ((JToggleButton)(field.get(this.panel))).setSelected(value.equals("true")? true : false);
                }
                else if (JSpinner.class.isAssignableFrom(field.getClass())) {
                    // TODO: handle non-string Models
                    ((JSpinner)(field.get(this.panel))).getModel().setValue(value);
                }
                else if (JSlider.class.isAssignableFrom(field.getClass())) {
                    ((JSlider)(field.get(this.panel))).setValue(new Integer(value));
                }
                else if (JList.class.isAssignableFrom(field.getClass())) {
                    // TODO: handle non-string Models
                    JsonReader reader = Json.createReader(new StringReader(value));
                    JsonArray array = reader.readArray();
                    for (int i=0; i<array.size(); ++i) {
                        String s = array.getString(i);
                        ((JList)(field.get(this.panel))).setSelectedValue(s, true);
                    }
                    reader.close();
                }
                else if (JTable.class.isAssignableFrom(field.getClass())) { 
                    // TODO: handle non-string Models
                    TableModel model = ((JTable)(field.get(this.panel))).getModel();
                    JsonReader reader = Json.createReader(new StringReader(value));
                    JsonArray rows = reader.readArray();
                    for (int i=0; i<rows.size(); ++i) {
                        JsonArray row = rows.getJsonArray(i);
                        for (int j=0; j<row.size(); ++j) {
                            String val = row.getString(j);
                            model.setValueAt(val, i, j);
                        }
                    }
                    reader.close();
                }
                // TODO: handle Tree models
                // else if (JTree.class.isAssignableFrom(field.getClass())) { }
            }
        }
        catch (IllegalArgumentException e) {throw new RuntimeException(e);} 
        catch (IllegalAccessException e) {throw new RuntimeException(e);}
        return this.panel;
    }
    
    @Override
    public String[] validate() {
        return null;
    }
}
