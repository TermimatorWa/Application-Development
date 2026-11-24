package com.example.app.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.app.data.HerbivoreWorkbook.CellType;
import com.example.app.data.HerbivoreWorkbook.ObservationBlock;
import com.example.app.data.HerbivoreWorkbook.ObservationType;
import com.example.app.data.HerbivoreWorkbook.ReportedDates;
import com.example.app.model.Species;

class HerbivoreWorkbookReaderTest {
    @TempDir
    Path directory;

    @Test
    void readsIndependentBlocksWithReorderedHeadersAndPreservesCells() throws IOException {
        Path file = directory.resolve("synthetic.xlsx");
        try (var workbook = new XSSFWorkbook()) {
            var sheet = layout(workbook);
            var row = sheet.createRow(8);
            row.createCell(2).setCellValue(2040);
            row.createCell(3).setCellValue(0);
            row.createCell(5).setBlank();
            var total = row.createCell(6);
            total.setCellFormula("SUM(D9:F9)");
            total.setCellValue(45);
            row.createCell(9).setCellValue(2043);
            row.createCell(10).setCellValue(-2);
            row.createCell(11).setCellErrorValue(FormulaError.DIV0.getCode());
            row.createCell(12).setCellValue("unknown");
            row.createCell(16).setCellValue(2044);
            row.createCell(17).setCellFormula("1+1");
            var textFormula = row.createCell(18);
            textFormula.setCellFormula("\"pending\"");
            textFormula.setCellValue("pending");
            var errorFormula = row.createCell(19);
            errorFormula.setCellFormula("1/0");
            errorFormula.setCellErrorValue(FormulaError.DIV0.getCode());
            sheet.createRow(9).createCell(3).setCellValue("unrecorded");
            sheet.createRow(10).createCell(2).setCellValue(2042);
            sheet.createRow(11).createCell(11).setCellValue("1)");
            write(workbook, file);
        }

        HerbivoreWorkbook result = HerbivoreWorkbookReader.read(file);
        assertEquals(List.of(ObservationType.POPULATION, ObservationType.MORTALITY, ObservationType.BIRTHS),
                result.blocks().stream().map(ObservationBlock::type).toList());
        assertEquals(List.of(List.of(2040, -1, 2042), List.of(2043), List.of(2044)),
                result.blocks().stream()
                        .map(block -> block.rows().stream().map(row -> row.year().orElse(-1)).toList())
                        .toList());
        var undated = result.blocks().getFirst().rows().get(1);
        assertEquals(CellType.ABSENT, undated.yearCell().originalValue().type());
        assertEquals("unrecorded", undated.speciesCells().get(Species.HORSES).originalValue().rawText());
        var population = result.blocks().getFirst().rows().getFirst();
        assertEquals("2025", population.worksheet());
        assertEquals(9, population.rowNumber());
        assertEquals("C9", population.yearCell().address());
        assertEquals(List.of(Species.HORSES, Species.DEER, Species.CATTLE),
                List.copyOf(population.speciesCells().keySet()));
        assertEquals(0, population.speciesCells().get(Species.HORSES).originalValue().numericValue().signum());
        assertEquals(CellType.ABSENT, population.speciesCells().get(Species.DEER).originalValue().type());
        assertEquals("E9", population.speciesCells().get(Species.DEER).address());
        assertEquals(CellType.BLANK, population.speciesCells().get(Species.CATTLE).originalValue().type());
        assertEquals(CellType.FORMULA, population.reportedTotal().originalValue().type());
        assertEquals("SUM(D9:F9)", population.reportedTotal().formula());
        assertEquals(45, population.reportedTotal().cachedResult().numericValue().intValueExact());
        var mortality = result.blocks().get(1).rows().getFirst().speciesCells();
        assertEquals(-2, mortality.get(Species.HORSES).originalValue().numericValue().intValueExact());
        assertEquals("#DIV/0!", mortality.get(Species.DEER).originalValue().rawText());
        assertEquals("unknown", mortality.get(Species.CATTLE).originalValue().rawText());
        var births = result.blocks().getLast().rows().getFirst().speciesCells();
        assertNull(births.get(Species.HORSES).cachedResult());
        assertEquals("pending", births.get(Species.DEER).cachedResult().rawText());
        assertEquals(CellType.ERROR, births.get(Species.CATTLE).cachedResult().type());
        assertEquals((int) FormulaError.DIV0.getCode(), births.get(Species.CATTLE).cachedResult().errorCode());
        assertTrue(result.notes().isEmpty());
        Files.delete(file);
    }

    @Test
    void preservesVerbatimNotesAndRawFontColourAttributes() throws IOException {
        Path file = directory.resolve("metadata.xlsx");
        try (var workbook = new XSSFWorkbook()) {
            var sheet = layout(workbook);
            var row = sheet.createRow(8);
            row.createCell(2).setCellValue(2040);
            var themed = workbook.createFont();
            var colour = themed.getCTFont().addNewColor();
            colour.setTheme(5);
            colour.setTint(-0.2);
            var rgb = workbook.createFont();
            rgb.getCTFont().addNewColor().setRgb(new byte[]{(byte) 255, 1, 2, 3});
            var indexed = workbook.createFont();
            indexed.getCTFont().addNewColor().setIndexed(11);
            var fonts = List.of(themed, rgb, indexed);
            for (int i = 0; i < fonts.size(); i++) {
                var cell = row.createCell(3 + i);
                cell.setCellValue(i);
                var style = workbook.createCellStyle();
                style.setFont(fonts.get(i));
                cell.setCellStyle(style);
            }
            sheet.createRow(0).createCell(0).setCellValue("Teaching use only.");
            sheet.createRow(13).createCell(3).setCellValue("Blue counts may be uncertain.");
            sheet.createRow(14).createCell(11).setCellValue("Transfer between April 2040 and 2042.");
            write(workbook, file);
        }
        long size = Files.size(file);
        var result = HerbivoreWorkbookReader.read(file);
        var cells = result.blocks().getFirst().rows().getFirst().speciesCells();
        var style = cells.get(Species.HORSES).style();
        assertTrue(style.styleIndex() > 0);
        assertEquals(5, style.fontColour().theme());
        assertEquals(new BigDecimal("-0.2"), style.fontColour().tint());
        assertEquals("FF010203", cells.get(Species.DEER).style().fontColour().argb());
        assertEquals(11, cells.get(Species.CATTLE).style().fontColour().indexed());
        assertEquals(List.of("A1", "D14", "L15"), result.notes().stream().map(note -> note.address()).toList());
        assertEquals("Blue counts may be uncertain.", result.notes().get(1).text());
        assertEquals("2025", result.notes().getLast().worksheet());
        assertEquals("Transfer between April 2040 and 2042.", result.notes().getLast().text());
        assertEquals(new ReportedDates("April 2040", "2042"), result.notes().getLast().dates());
        assertEquals(size, Files.size(file));
    }

    @Test
    void reportsMissingCorruptAndWrongSheetFiles() throws IOException {
        Path missing = directory.resolve("missing.xlsx");
        assertTrue(assertThrows(IOException.class, () -> HerbivoreWorkbookReader.read(missing))
                .getMessage().contains("does not exist"));
        Path corrupt = Files.writeString(directory.resolve("corrupt.xlsx"), "plain text");
        assertTrue(assertThrows(IOException.class, () -> HerbivoreWorkbookReader.read(corrupt))
                .getMessage().contains(corrupt.toString()));
        Path wrongSheet = directory.resolve("wrong-sheet.xlsx");
        try (var workbook = new XSSFWorkbook()) {
            workbook.createSheet("Other");
            write(workbook, wrongSheet);
        }
        assertTrue(assertThrows(IOException.class, () -> HerbivoreWorkbookReader.read(wrongSheet))
                .getMessage().contains("Missing worksheet '2025'"));
    }

    @Test
    void reportsMissingAndAmbiguousHeadersAndSections() throws IOException {
        Path file = directory.resolve("headers.xlsx");
        try (var workbook = new XSSFWorkbook()) {
            var sheet = layout(workbook);
            var headers = sheet.getRow(6);
            headers.getCell(5).setCellValue("Unknown species");
            write(workbook, file);
            assertTrue(assertThrows(IOException.class, () -> HerbivoreWorkbookReader.read(file))
                    .getMessage().contains("Missing required headers"));
            headers.getCell(5).setCellValue("Red deer");
            write(workbook, file);
            assertTrue(assertThrows(IOException.class, () -> HerbivoreWorkbookReader.read(file))
                    .getMessage().contains("Ambiguous POPULATION header at F7"));
            headers.getCell(5).setCellValue("Heck cattle");
            sheet.getRow(4).getCell(17).setBlank();
            write(workbook, file);
            assertTrue(assertThrows(IOException.class, () -> HerbivoreWorkbookReader.read(file))
                    .getMessage().contains("Missing required BIRTHS section"));
        }
    }

    private static XSSFSheet layout(XSSFWorkbook workbook) {
        var sheet = workbook.createSheet("2025");
        var headings = sheet.createRow(4);
        headings.createCell(3).setCellValue("Total number of animals on January 1st");
        headings.createCell(10).setCellValue("Total mortality");
        headings.createCell(17).setCellValue("Total births");
        var headers = sheet.createRow(6);
        String[] labels = {"Year", "Konik horses", "Red deer", "Heck cattle", "Total"};
        for (int first : new int[]{2, 9, 16}) {
            for (int i = 0; i < labels.length; i++) {
                headers.createCell(first + i).setCellValue(labels[i]);
            }
        }
        return sheet;
    }

    private static void write(XSSFWorkbook workbook, Path path) throws IOException {
        try (var output = Files.newOutputStream(path)) {
            workbook.write(output);
        }
    }
}
