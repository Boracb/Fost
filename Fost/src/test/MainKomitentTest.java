package test; // ili db, ako želiš da bude u istom paketu

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import db.KomitentiDatabaseHelper;
import excel.ExcelImporter;

public class MainKomitentTest {
    public static void main(String[] args) {
        // Inicijaliziraj tablicu ako slučajno ne postoji
        //KomitentiDatabaseHelper.initializeDatabase();
        



    	// Reads and prints only the column headers (first row) with their indices
    	File putFilePath = new File("C:\\Users\\Ines\\Desktop\\\\boko2.xlsx");

    	try (FileInputStream fis = new FileInputStream(putFilePath); Workbook wb = new XSSFWorkbook(fis)) {
    	    Sheet sheet = wb.getSheetAt(0);
    	    Row headerRow = sheet.getRow(0);
    	    if (headerRow != null) {
    	        System.out.print("Headers: ");
    	        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
    	            Cell cell = headerRow.getCell(c);
    	            String cellValue = cell == null ? "" : cell.toString().trim();
    	            System.out.print("[" + c + "]='" + cellValue + "' ");
    	        }
    	        System.out.println();
    	    } else {
    	        System.out.println("No header row found.");
    	    }
    	} catch (IOException ex) {
    	    System.out.println("Error reading Excel file: " + ex.getMessage());
    	}


        // Učitaj sve retke iz baze
        //List<Object[]> podaci = KomitentiDatabaseHelper.loadAllRows();

     //   System.out.println("\n=== Provjera ispisa komitenata iz baze ===");
//        for (Object[] red : podaci) {
//            String opis = (String) red[0];
//            String predstavnik = (String) red[1];
//            System.out.println(opis + " | " + predstavnik);
//        }
    }
}
