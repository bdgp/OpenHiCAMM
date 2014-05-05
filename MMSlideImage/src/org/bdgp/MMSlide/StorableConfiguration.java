package org.bdgp.MMSlide;

import java.awt.Component;
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
    protected Component panel;
    
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Storable {};

    public StorableConfiguration(Component panel) {
        this.panel = panel;
    }

    /**
     * Use introspection to retrieve a list of configs for the panel.
     */
    @SuppressWarnings("rawtypes")
    @Override
    public Config[] retrieve() {
        List<Config> configs = new ArrayList<Config>();
        Field[] fields = this.panel.getClass().getDeclaredFields();
        try {
            for (Field field : fields) {
                if (!field.isAnnotationPresent(Storable.class)) continue;
                String value = null;
                if (JTextField.class.isAssignableFrom(field.getType())) {
                    value = ((JTextField)(field.get(this.panel))).getText();
                }
                else if (JTextArea.class.isAssignableFrom(field.getType())) {
                    value = ((JTextArea)(field.get(this.panel))).getText();
                }
                else if (JFormattedTextField.class.isAssignableFrom(field.getType())) {
                    value = ((JFormattedTextField)(field.get(this.panel))).getText();
                }
                else if (JEditorPane.class.isAssignableFrom(field.getType())) {
                    value = ((JEditorPane)(field.get(this.panel))).getText();
                }
                else if (JTextPane.class.isAssignableFrom(field.getType())) {
                    value = ((JTextPane)(field.get(this.panel))).getText();
                }
                else if (JPasswordField.class.isAssignableFrom(field.getType())) {
                    value = new String(((JPasswordField)(field.get(this.panel))).getPassword());
                }
                else if (JComboBox.class.isAssignableFrom(field.getType())) {
                    Object item = ((JComboBox)(field.get(this.panel))).getSelectedItem();
                    if (item != null) value = item.toString();
                }
                else if (JCheckBox.class.isAssignableFrom(field.getType())) {
                    value = ((JCheckBox)(field.get(this.panel))).isSelected()? "true" : "false";
                }
                else if (JRadioButton.class.isAssignableFrom(field.getType())) {
                    value = ((JRadioButton)(field.get(this.panel))).isSelected()? "true" : "false";
                }
                else if (JToggleButton.class.isAssignableFrom(field.getType())) {
                    value = ((JToggleButton)(field.get(this.panel))).isSelected()? "true" : "false";
                }
                else if (JSpinner.class.isAssignableFrom(field.getType())) {
                    Object val = ((JSpinner)(field.get(this.panel))).getModel().getValue();
                    if (val != null) value = val.toString();
                }
                else if (JSlider.class.isAssignableFrom(field.getType())) {
                    value = new Integer(((JSlider)(field.get(this.panel))).getValue()).toString();
                }
                else if (JList.class.isAssignableFrom(field.getType())) {
                    // TODO: Handle non-string models
                    int[] indices = ((JList)(field.get(this.panel))).getSelectedIndices();
                    ListModel model = ((JList)(field.get(this.panel))).getModel();
                    JsonArrayBuilder j = Json.createArrayBuilder();
                    for (int i : indices) {
                        Object o = model.getElementAt(i);
                        j.add(o.toString());
                    }
                    value = j.build().toString();
                }
                else if (JTable.class.isAssignableFrom(field.getType())) { 
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
                // else if (JTree.class.isAssignableFrom(field.getType())) { }
                if (value != null) configs.add(new Config(null, field.getName(), value));
            }
        }
        catch (IllegalArgumentException e) {throw new RuntimeException(e);} 
        catch (IllegalAccessException e) {throw new RuntimeException(e);}
        return configs.toArray(new Config[0]);
    }
    
    @SuppressWarnings("rawtypes")
    @Override
    public Component display(Config[] configs) {
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
    
                if (JTextField.class.isAssignableFrom(field.getType())) {
                    ((JTextField)(field.get(this.panel))).setText(value);
                }
                else if (JTextArea.class.isAssignableFrom(field.getType())) {
                    ((JTextArea)(field.get(this.panel))).setText(value);
                }
                else if (JFormattedTextField.class.isAssignableFrom(field.getType())) {
                    ((JFormattedTextField)(field.get(this.panel))).setText(value);
                }
                else if (JEditorPane.class.isAssignableFrom(field.getType())) {
                    ((JEditorPane)(field.get(this.panel))).setText(value);
                }
                else if (JTextPane.class.isAssignableFrom(field.getType())) {
                    ((JTextPane)(field.get(this.panel))).setText(value);
                }
                else if (JPasswordField.class.isAssignableFrom(field.getType())) {
                    ((JPasswordField)(field.get(this.panel))).setText(value);
                }
                else if (JComboBox.class.isAssignableFrom(field.getType())) {
                    // TODO: handle non-string Models
                    ((JComboBox)(field.get(this.panel))).setSelectedItem(value);
                }
                else if (JCheckBox.class.isAssignableFrom(field.getType())) {
                    ((JCheckBox)(field.get(this.panel))).setSelected(value.equals("true")? true : false);
                }
                else if (JRadioButton.class.isAssignableFrom(field.getType())) {
                    ((JRadioButton)(field.get(this.panel))).setSelected(value.equals("true")? true : false);
                }
                else if (JToggleButton.class.isAssignableFrom(field.getType())) {
                    ((JToggleButton)(field.get(this.panel))).setSelected(value.equals("true")? true : false);
                }
                else if (JSpinner.class.isAssignableFrom(field.getType())) {
                    // TODO: handle non-string Models
                    ((JSpinner)(field.get(this.panel))).getModel().setValue(value);
                }
                else if (JSlider.class.isAssignableFrom(field.getType())) {
                    ((JSlider)(field.get(this.panel))).setValue(new Integer(value));
                }
                else if (JList.class.isAssignableFrom(field.getType())) {
                    // TODO: handle non-string Models
                    JsonReader reader = Json.createReader(new StringReader(value));
                    JsonArray array = reader.readArray();
                    for (int i=0; i<array.size(); ++i) {
                        String s = array.getString(i);
                        ((JList)(field.get(this.panel))).setSelectedValue(s, true);
                    }
                    reader.close();
                }
                else if (JTable.class.isAssignableFrom(field.getType())) { 
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
                // else if (JTree.class.isAssignableFrom(field.getType())) { }
            }
        }
        catch (IllegalArgumentException e) {throw new RuntimeException(e);} 
        catch (IllegalAccessException e) {throw new RuntimeException(e);}
        return this.panel;
    }
    
    @Override
    public ValidationError[] validate() {
        return null;
    }
}
