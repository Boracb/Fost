package ui;

import excel.ExcelSalesImporter;
import model.AggregatedConsumption;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

/**
 * UI frame for displaying and analyzing inventory turnover based on sales data.
 * Shows aggregated consumption data from Excel sales imports.
 */
public class InventoryTurnoverPlannerFrame extends JFrame {
    
    private JTable table;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField searchField;
    private JSpinner radnihDanaSpinner;
    private JLabel statusLabel;
    
    private static final String[] COLUMN_NAMES = {
        "Šifra", "Naziv", "Ukupna količina", "Min. datum", "Max. datum",
        "Prosjek/dan", "Godišnja potrošnja", "Komitent", "Neto vrijednost"
    };
    
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    
    public InventoryTurnoverPlannerFrame() {
        initializeFrame();
        createComponents();
        layoutComponents();
        setupEventHandlers();
        loadInitialData();
    }
    
    private void initializeFrame() {
        setTitle("Planiranje obrtaja inventara - Analiza prodaje");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1200, 700);
        setLocationRelativeTo(null);
        
        // Set icon if available (same as main app)
        try {
            setIconImage(Toolkit.getDefaultToolkit().getImage("resources/app-icon.png"));
        } catch (Exception ignored) {
            // Ignore if icon not found
        }
    }
    
    private void createComponents() {
        // Create table model and table
        tableModel = new DefaultTableModel(COLUMN_NAMES, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table read-only
            }
            
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                switch (columnIndex) {
                    case 2: case 5: case 6: case 8: // Numeric columns
                        return Double.class;
                    default:
                        return String.class;
                }
            }
        };
        
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        
        // Set up sorter for filtering
        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        
        // Configure column widths
        setupColumnWidths();
        
        // Create other components
        searchField = new JTextField(20);
        searchField.setToolTipText("Pretraži po nazivu ili šifri");
        
        radnihDanaSpinner = new JSpinner(new SpinnerNumberModel(250, 200, 300, 1));
        radnihDanaSpinner.setToolTipText("Broj radnih dana u godini za izračun");
        
        statusLabel = new JLabel("Spremno za uvoz podataka");
    }
    
    private void setupColumnWidths() {
        int[] widths = {80, 250, 120, 100, 100, 100, 150, 200, 120};
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }
    
    private void layoutComponents() {
        setLayout(new BorderLayout());
        
        // Top toolbar
        JPanel toolbar = createToolbar();
        add(toolbar, BorderLayout.NORTH);
        
        // Main table in scroll pane
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(1150, 500));
        add(scrollPane, BorderLayout.CENTER);
        
        // Bottom status panel
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.add(statusLabel);
        add(statusPanel, BorderLayout.SOUTH);
    }
    
    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        toolbar.setBorder(BorderFactory.createEtchedBorder());
        
        // Import button
        JButton btnImport = new JButton("📊 Uvezi prodaju");
        btnImport.setToolTipText("Uvezi prodajne podatke iz Excel datoteke");
        btnImport.addActionListener(e -> importSalesData());
        
        // Refresh button
        JButton btnRefresh = new JButton("🔄 Osvježi");
        btnRefresh.setToolTipText("Ponovno izračunaj godišnje potrošnje");
        btnRefresh.addActionListener(e -> refreshCalculations());
        
        // Clear button
        JButton btnClear = new JButton("🗑️ Očisti");
        btnClear.setToolTipText("Očisti sve podatke");
        btnClear.addActionListener(e -> clearData());
        
        // Search components
        JLabel lblSearch = new JLabel("Pretraži:");
        
        // Working days components
        JLabel lblRadniDani = new JLabel("Radni dani/godina:");
        
        // Add components to toolbar
        toolbar.add(btnImport);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(btnRefresh);
        toolbar.add(btnClear);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(lblSearch);
        toolbar.add(searchField);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(lblRadniDani);
        toolbar.add(radnihDanaSpinner);
        
        return toolbar;
    }
    
    private void setupEventHandlers() {
        // Search field filter
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterTable(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterTable(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterTable(); }
        });
        
        // Working days spinner change
        radnihDanaSpinner.addChangeListener(e -> refreshCalculations());
    }
    
    private void loadInitialData() {
        statusLabel.setText("Spremno za uvoz podataka - koristite 'Uvezi prodaju' za učitavanje Excel datoteke");
    }
    
    private void importSalesData() {
        statusLabel.setText("Učitavam prodajne podatke...");
        
        SwingUtilities.invokeLater(() -> {
            try {
                List<AggregatedConsumption> data = ExcelSalesImporter.importSalesFromExcel();
                
                if (data != null && !data.isEmpty()) {
                    populateTable(data);
                    statusLabel.setText(String.format("Učitano %d stavki prodaje", data.size()));
                } else if (data != null) {
                    statusLabel.setText("Excel datoteka je prazna ili ne sadrži valjane podatke");
                    JOptionPane.showMessageDialog(this, 
                        "Excel datoteka ne sadrži valjane prodajne podatke.\n" +
                        "Provjerite da datoteka ima kolone za Naziv i Količinu.",
                        "Nema podataka", JOptionPane.WARNING_MESSAGE);
                } else {
                    statusLabel.setText("Uvoz otkazan");
                }
            } catch (Exception ex) {
                statusLabel.setText("Greška pri uvozu: " + ex.getMessage());
                JOptionPane.showMessageDialog(this,
                    "Greška pri uvozu prodajnih podataka:\n" + ex.getMessage(),
                    "Greška", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
    
    private void populateTable(List<AggregatedConsumption> data) {
        tableModel.setRowCount(0); // Clear existing data
        
        int radnihDana = (Integer) radnihDanaSpinner.getValue();
        
        for (AggregatedConsumption item : data) {
            Object[] row = new Object[COLUMN_NAMES.length];
            
            row[0] = item.getSifra() != null ? item.getSifra() : "";
            row[1] = item.getNaziv();
            row[2] = NUMBER_FORMAT.format(item.getTotalQty());
            row[3] = item.getMinDate() != null ? item.getMinDate().format(DATE_FORMAT) : "";
            row[4] = item.getMaxDate() != null ? item.getMaxDate().format(DATE_FORMAT) : "";
            row[5] = NUMBER_FORMAT.format(item.getAvgPerDay());
            row[6] = NUMBER_FORMAT.format(item.getAnnualConsumption(radnihDana));
            row[7] = item.getKomitent() != null ? item.getKomitent() : "";
            row[8] = item.getNetoVrijednost() != null ? NUMBER_FORMAT.format(item.getNetoVrijednost()) : "";
            
            tableModel.addRow(row);
        }
        
        // Auto-resize columns to content
        for (int col = 0; col < table.getColumnCount(); col++) {
            table.getColumnModel().getColumn(col).sizeWidthToFit();
        }
    }
    
    private void refreshCalculations() {
        if (tableModel.getRowCount() == 0) {
            return; // No data to refresh
        }
        
        statusLabel.setText("Ponovno izračunam godišnje potrošnje...");
        
        SwingUtilities.invokeLater(() -> {
            try {
                int radnihDana = (Integer) radnihDanaSpinner.getValue();
                
                // Update annual consumption column (index 6)
                for (int row = 0; row < tableModel.getRowCount(); row++) {
                    String avgPerDayStr = (String) tableModel.getValueAt(row, 5);
                    if (avgPerDayStr != null && !avgPerDayStr.isEmpty()) {
                        try {
                            double avgPerDay = NUMBER_FORMAT.parse(avgPerDayStr).doubleValue();
                            double annualConsumption = avgPerDay * radnihDana;
                            tableModel.setValueAt(NUMBER_FORMAT.format(annualConsumption), row, 6);
                        } catch (Exception ex) {
                            // Skip rows with invalid data
                        }
                    }
                }
                
                statusLabel.setText(String.format("Izračuni osvježeni za %d radnih dana godišnje", radnihDana));
            } catch (Exception ex) {
                statusLabel.setText("Greška pri osvježavanju: " + ex.getMessage());
            }
        });
    }
    
    private void clearData() {
        int result = JOptionPane.showConfirmDialog(this,
            "Jeste li sigurni da želite očistiti sve podatke?",
            "Potvrda brisanja", JOptionPane.YES_NO_OPTION);
        
        if (result == JOptionPane.YES_OPTION) {
            tableModel.setRowCount(0);
            searchField.setText("");
            statusLabel.setText("Podaci obrisani - spremno za novi uvoz");
        }
    }
    
    private void filterTable() {
        String searchText = searchField.getText().trim();
        
        if (searchText.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            // Filter by name (column 1) or code (column 0)
            RowFilter<DefaultTableModel, Object> filter = RowFilter.regexFilter(
                "(?i)" + Pattern.quote(searchText), 0, 1);
            sorter.setRowFilter(filter);
        }
        
        int visibleRows = table.getRowCount();
        int totalRows = tableModel.getRowCount();
        
        if (visibleRows != totalRows) {
            statusLabel.setText(String.format("Prikazano %d od %d stavki", visibleRows, totalRows));
        } else {
            statusLabel.setText(String.format("Prikazano %d stavki", totalRows));
        }
    }
    
    /**
     * Display the inventory turnover planner frame
     */
    public static void showInventoryTurnoverPlanner() {
        SwingUtilities.invokeLater(() -> {
            new InventoryTurnoverPlannerFrame().setVisible(true);
        });
    }
}