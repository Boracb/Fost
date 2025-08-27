package ui;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.text.*;
import java.util.Date;

public class DateCellEditor extends AbstractCellEditor implements TableCellEditor {
    private final JSpinner spinner;
    private final SimpleDateFormat sdf;
    private final JComponent editorComponent;

    public DateCellEditor(String pattern) {
        this.sdf = new SimpleDateFormat(pattern);
        SpinnerDateModel model = new SpinnerDateModel();
        this.spinner = new JSpinner(model);
        JSpinner.DateEditor de = new JSpinner.DateEditor(spinner, pattern);
        JFormattedTextField ftf = de.getTextField();
        ftf.setColumns(Math.max(5, pattern.length()));
        spinner.setEditor(de);
        this.editorComponent = spinner;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        Date date = null;
        if (value instanceof Date) {
            date = (Date) value;
        } else if (value instanceof String) {
            String s = ((String) value).trim();
            try {
                date = sdf.parse(s);
            } catch (ParseException e) {
                date = new Date();
            }
        } else {
            date = new Date();
        }
        spinner.setValue(date);
        SwingUtilities.invokeLater(() -> {
            editorComponent.requestFocusInWindow();
            Component tf = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
            if (tf instanceof JFormattedTextField) {
                ((JFormattedTextField) tf).selectAll();
            }
        });
        return editorComponent;
    }

    @Override
    public Object getCellEditorValue() {
        Object val = spinner.getValue();
        if (val instanceof Date) {
            return sdf.format((Date) val);
        }
        return val;
    }
}