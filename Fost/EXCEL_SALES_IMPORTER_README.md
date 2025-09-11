# Excel Sales Importer - Croatian Format Support

## Overview

This implementation adds end-to-end "Uvezi prodaju" (Import Sales) functionality to the Fost application, allowing import and aggregation of Croatian sales data from Excel files.

## Features

### 1. Excel Sales Importer (`excel/ExcelSalesImporter.java`)

- **File Format Support**: .xlsx and .xls files
- **Croatian Header Detection**: Automatically detects Croatian column headers:
  - `Datum` → date (optional)
  - `Šifra`, `Kod`, `Artikl` → item code (optional)
  - `Naziv robe`, `Naziv`, `Proizvod`, `Opis` → item name (required)
  - `Količina`, `Kom`, `Komada` → quantity (required)
  - `Komitent`, `Kupac`, `Klijent` → customer (optional)
  - `Neto vrijednost`, `Vrijednost`, `Cijena` → net value (optional)

- **Flexible Column Detection**:
  - Header-based mapping when headers are present
  - Heuristic fallback for misaligned or missing headers
  - Content analysis to detect column types automatically

- **European Number Format Parsing**:
  - `1.234,56` (dot thousands, comma decimal)
  - `1 234,56` (space thousands, comma decimal)  
  - `1.078,00` format support
  - Currency symbol handling (€, $, £, ¥)
  - Percentage filtering (ignores % values as quantities)

- **Date Format Support**:
  - `dd.MM.yyyy` and `dd.MM.yyyy.`
  - `d.M.yyyy` and `d.M.yyyy.`
  - `yyyy-MM-dd`
  - `dd/MM/yyyy` and `d/M/yyyy`
  - Excel numeric date cells
  - Optional dates (rows imported even without dates)

- **Text Processing**:
  - Non-breaking space (NBSP) handling (U+00A0, U+202F, U+2007)
  - Croatian diacritic normalization (č→c, ć→c, š→s, ž→z, đ→d)
  - Robust string cleanup and trimming

### 2. Sales Aggregation (`excel/SalesAggregator.java`)

- **Data Aggregation**: Groups sales rows by item (using code + name)
- **Consumption Calculation**: Aggregates quantities per item
- **Date Range Tracking**: Tracks min/max sale dates per item
- **Flexible Sorting**: By quantity, name, or custom criteria
- **Date Range Filtering**: Filter aggregated data by date ranges

### 3. Consumption Model (`model/AggregatedConsumption.java`)

- **Total Quantity**: `getTotalQty()`
- **Daily Average**: `getAvgPerDay()` based on date range
- **Annual Projection**: `getAnnualConsumption(radnihDana)` 
- **Merging Support**: Combine multiple consumption entries
- **Optional Fields**: Handles missing codes, dates, or values gracefully

### 4. User Interface (`ui/InventoryTurnoverPlannerFrame.java`)

- **Import Button**: "📊 Uvezi prodaju" launches Excel file chooser
- **Data Table**: Displays aggregated consumption with sorting/filtering
- **Search Functionality**: Filter by item name or code
- **Working Days Setting**: Configurable annual working days (default: 250)
- **Refresh Calculations**: Recalculate projections with new working days
- **Status Display**: Shows import progress and row counts

## System Properties

Configure behavior using Java system properties:

```bash
-Dfost.excel.scanRows=500          # Header scan depth (default: 500)
-Dfost.debugexcel=1               # Enable debug logging (default: off)
```

## Usage

### 1. Main Application
- Click "Uvezi prodaju" button in main UI
- Opens InventoryTurnoverPlannerFrame
- Use "📊 Uvezi prodaju" to import Excel files

### 2. Programmatic Usage

```java
// Import sales data
List<AggregatedConsumption> data = ExcelSalesImporter.importSalesFromExcel(file);

// Or aggregate manual data
List<SalesRow> salesRows = Arrays.asList(
    new SalesRow(LocalDate.now(), "A001", "Item Name", 100.0)
);
List<AggregatedConsumption> aggregated = SalesAggregator.aggregateConsumption(salesRows);

// Calculate projections
for (AggregatedConsumption item : aggregated) {
    double dailyAvg = item.getAvgPerDay();
    double annualConsumption = item.getAnnualConsumption(250); // 250 working days
}
```

## File Structure

```
src/
├── excel/
│   ├── ExcelSalesImporter.java      # Main importer with Croatian format support
│   └── SalesAggregator.java         # Sales data aggregation utilities
├── model/
│   └── AggregatedConsumption.java   # Consumption data model with calculations
├── ui/
│   └── InventoryTurnoverPlannerFrame.java  # UI for viewing aggregated data
└── test/
    ├── TestExcelSalesImporter.java         # Basic aggregation tests
    ├── TestExcelSalesImporterFull.java     # Parsing functionality tests
    └── TestCompleteExcelSalesFlow.java     # End-to-end workflow tests
```

## Error Handling

- **Missing Files**: User-friendly file chooser with format validation
- **Invalid Data**: Gracefully skips rows with missing required fields
- **Format Errors**: Robust parsing with fallback to string representation  
- **Empty Files**: Validates minimum data requirements before processing
- **Header Detection**: Falls back to heuristic analysis if headers not found

## Testing

Run the included tests to verify functionality:

```bash
# Basic aggregation test
java -cp "bin:Jars/*" test.TestExcelSalesImporter

# Number parsing and header normalization
java -cp "bin:Jars/*" test.TestExcelSalesImporterFull  

# Complete workflow test
java -cp "bin:Jars/*" test.TestCompleteExcelSalesFlow
```

## Croatian Language Support

The importer is specifically designed for Croatian business environments:

- **Headers**: Recognizes Croatian column names and variations
- **Number Formats**: Handles European/Croatian decimal notation
- **Date Formats**: Supports Croatian date conventions (dd.MM.yyyy)
- **Text Processing**: Properly handles Croatian diacritical marks
- **Business Context**: Designed for Croatian sales/inventory terminology

This implementation provides a robust, user-friendly solution for importing Croatian sales data while maintaining compatibility with various Excel export formats and handling real-world data inconsistencies.