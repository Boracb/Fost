package ui;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.Component;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;

/**
 * A JSpinner-based TableCellEditor for time input that returns formatted String "HH:mm".
 * 
 * This editor uses a SpinnerDateModel and JSpinner.DateEditor to allow time selection.
 * The SimpleDateFormat pattern is configurable via constructor.
 */
public class DateCellEditor extends AbstractCellEditor implements TableCellEditor {
    
    private final JSpinner spinner;
    private final SimpleDateFormat formatter;
    
    /**
     * Creates a DateCellEditor with the specified time format pattern.
     * 
     * @param pattern the SimpleDateFormat pattern (e.g., "HH:mm")
     */
    public DateCellEditor(String pattern) {
        this.formatter = new SimpleDateFormat(pattern);
        
        // Create spinner with date model
        SpinnerDateModel model = new SpinnerDateModel();
        this.spinner = new JSpinner(model);
        
        // Set the date editor with the specified pattern
        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, pattern);
        spinner.setEditor(editor);
    }
    
    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        Date dateValue;
        
        if (value instanceof Date) {
            dateValue = (Date) value;
        } else if (value instanceof String && !((String) value).isEmpty()) {
            try {
                // Try to parse the string value using our formatter
                dateValue = formatter.parse((String) value);
            } catch (ParseException e) {
                // If parsing fails, use current time
                dateValue = new Date();
            }
        } else {
            // Default to current time
            dateValue = new Date();
        }
        
        // Set the spinner value
        spinner.setValue(dateValue);
        
        // Request focus and select text for better user experience
        SwingUtilities.invokeLater(() -> {
            JSpinner.DateEditor editor = (JSpinner.DateEditor) spinner.getEditor();
            JFormattedTextField textField = editor.getTextField();
            textField.requestFocus();
            textField.selectAll();
        });
        
        return spinner;
    }
    
    @Override
    public Object getCellEditorValue() {
        Date value = (Date) spinner.getValue();
        return formatter.format(value);
    }
}