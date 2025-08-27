package excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import model.KomitentInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Klasa za čitanje Excel (.xlsx) datoteke s komitentima.
 * Čita po nazivu kolona "komitentOpis" i "trgovackiPredstavnik".
 * Učitava samo one redove gdje je komitent popunjen (predstavnik može biti prazan).
 * Preskače duplikate istog para (komitentOpis + trgovackiPredstavnik).
 */

//  * - Stil za zaglavlja (bold, centrirano, obrub).
//  * - Stil za datume (dd.MM.yyyy).
//  * - Stil za datume i vrijeme (dd.MM.yyyy HH:mm).
//  * - Stil za decimalne brojeve (2 decimale).
//  * - Automatsko prilagođavanje širine stupaca.
//  * @param model Swing TableModel s podacima za izvoz.

public class ExcelKomitentReader {

    private static final SimpleDateFormat DATE_FMT_DOTS = new SimpleDateFormat("dd.MM.yyyy");
    private final String excelFilePath;

	/**
	 * Konstruktor prima putanju do Excel datoteke.
	 */
    public ExcelKomitentReader(String excelFilePath) {
        this.excelFilePath = excelFilePath;
    }

    /**
     * Čita Excel datoteku i vraća listu KomitentInfo objekata.
     */
    public List<KomitentInfo> readData() throws IOException {
        List<KomitentInfo> lista = new ArrayList<>();
        Set<String> seenPairs = new HashSet<>();

        try (FileInputStream fis = new FileInputStream(new File(excelFilePath));
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            if (!rowIterator.hasNext()) {
                return lista; // prazna datoteka
            }

            // --- 1) Učitaj header i pronađi indekse kolona po nazivu ---
            Row headerRow = rowIterator.next();
            Map<String, Integer> colIndexMap = new HashMap<>();

            for (Cell cell : headerRow) {
                String colName = getCellString(cell).trim().toLowerCase();
                if (!colName.isEmpty()) {
                    colIndexMap.put(colName, cell.getColumnIndex());
                }
            }

            Integer idxKomitent = colIndexMap.get("komitentopis");
            Integer idxPredstavnik = colIndexMap.get("trgovackipredstavnik");

            if (idxKomitent == null || idxPredstavnik == null) {
                throw new IllegalStateException(
                    "Excel ne sadrži potrebne kolone: komitentOpis i trgovackiPredstavnik"
                );
            }

            // --- 2) Čitaj redove - uvjet: komitent popunjen ---
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                String komitentOpis = getCellString(row.getCell(idxKomitent));
                String trgovackiPredstavnik = getCellString(row.getCell(idxPredstavnik));

                if (komitentOpis != null && !komitentOpis.isBlank()) {
                    String key = (komitentOpis + "|" +
                                 (trgovackiPredstavnik != null ? trgovackiPredstavnik : ""))
                                 .toLowerCase();

                    if (!seenPairs.contains(key)) {
                        seenPairs.add(key);
                        lista.add(new KomitentInfo(
                                komitentOpis,
                                trgovackiPredstavnik != null ? trgovackiPredstavnik : ""
                        ));
                    }
                }
            }
        }
        return lista;
    }

    // ===== Pomoćna metoda =====
// Dohvaća sadržaj ćelije kao String, bez obzira na tip ćelije.
 // Ako je ćelija null ili dođe do greške, vraća prazan String.
    // Podržava tipove: STRING, NUMERIC (broj ili datum), BOOLEAN, FORMULA.
    // Za NUMERIC tip uklanja decimalni dio ako je nula (npr. 123.0 → "123").
    // Za datume koristi format dd.MM.yyyy.
    // Za FORMULA evaluira formulu i vraća rezultat kao String.
    // Ostale tipove tretira kao prazan String.
    // U slučaju greške vraća prazan String.
    private static String getCellString(Cell cell) {
        if (cell == null) return "";
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return DATE_FMT_DOTS.format(cell.getDateCellValue());
                    } else {
                        return Double.toString(cell.getNumericCellValue()).replaceAll("\\.0$", "");
                    }
                case BOOLEAN:
                    return Boolean.toString(cell.getBooleanCellValue());
                case FORMULA:
                    FormulaEvaluator ev = cell.getSheet().getWorkbook()
                            .getCreationHelper().createFormulaEvaluator();
                    CellValue cv = ev.evaluate(cell);
                    if (cv == null) return "";
                    switch (cv.getCellType()) {
                        case STRING:  return cv.getStringValue().trim();
                        case NUMERIC: return Double.toString(cv.getNumberValue()).replaceAll("\\.0$", "");
                        case BOOLEAN: return Boolean.toString(cv.getBooleanValue());
                        default: return "";
                    }
                default:
                    return cell.toString().trim();
            }
        } catch (Exception e) {
            return "";
        }
    }
}
