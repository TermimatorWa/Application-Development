package com.example.app.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import com.example.app.data.HerbivoreWorkbook.CellStyle;
import com.example.app.data.HerbivoreWorkbook.CellType;
import com.example.app.data.HerbivoreWorkbook.CellValue;
import com.example.app.data.HerbivoreWorkbook.FontColour;
import com.example.app.data.HerbivoreWorkbook.ObservationBlock;
import com.example.app.data.HerbivoreWorkbook.ObservationRow;
import com.example.app.data.HerbivoreWorkbook.ObservationType;
import com.example.app.data.HerbivoreWorkbook.ReportedDates;
import com.example.app.data.HerbivoreWorkbook.SourceCell;
import com.example.app.data.HerbivoreWorkbook.SourceNote;
import com.example.app.model.Species;

class HerbivoreWorkbookTest {
    private static final String SHEET = "Synthetic counts";

    @Test
    void keepsIndependentBlocksAndYearCoverage() {
        ObservationBlock population = new ObservationBlock(ObservationType.POPULATION,
                List.of(row(6, 2040), row(7, 2042)));
        ObservationBlock births = new ObservationBlock(ObservationType.BIRTHS, List.of(row(12, 2038)));
        ObservationBlock mortality = new ObservationBlock(ObservationType.MORTALITY, List.of(row(17, 2041)));
        HerbivoreWorkbook workbook = new HerbivoreWorkbook(List.of(population, births, mortality), List.of());

        assertEquals(List.of(ObservationType.POPULATION, ObservationType.BIRTHS, ObservationType.MORTALITY),
                workbook.blocks().stream().map(ObservationBlock::type).toList());
        assertEquals(List.of(List.of(2040, 2042), List.of(2038), List.of(2041)),
                workbook.blocks().stream()
                        .map(block -> block.rows().stream().map(row -> row.year().getAsInt()).toList())
                        .toList());
    }

    @Test
    void preservesZeroMissingBlankInvalidAndTextWithLocations() {
        SourceCell zero = cell("B9", number("0"));
        SourceCell absent = cell("C9", new CellValue(CellType.ABSENT, null, null, null));
        SourceCell blank = cell("D9", new CellValue(CellType.BLANK, null, null, null));
        SourceCell invalid = cell("E9", new CellValue(CellType.NUMERIC, "NaN", null, null));
        SourceCell year = cell("A9", new CellValue(CellType.TEXT, "undated", null, null));
        Map<Species, SourceCell> species = new LinkedHashMap<>();
        species.put(Species.CATTLE, zero);
        species.put(Species.HORSES, absent);
        species.put(Species.DEER, blank);
        ObservationRow row = new ObservationRow(SHEET, 9, year, OptionalInt.empty(), species, invalid);

        assertEquals(SHEET, row.worksheet());
        assertEquals(9, row.rowNumber());
        assertEquals("A9", row.yearCell().address());
        assertEquals("undated", row.yearCell().originalValue().rawText());
        assertTrue(row.year().isEmpty());
        assertEquals(CellType.NUMERIC, row.speciesCells().get(Species.CATTLE).originalValue().type());
        assertEquals(BigDecimal.ZERO, row.speciesCells().get(Species.CATTLE).originalValue().numericValue());
        assertEquals(CellType.ABSENT, row.speciesCells().get(Species.HORSES).originalValue().type());
        assertEquals("C9", row.speciesCells().get(Species.HORSES).address());
        assertEquals(CellType.BLANK, row.speciesCells().get(Species.DEER).originalValue().type());
        assertEquals(CellType.NUMERIC, row.reportedTotal().originalValue().type());
        assertEquals("NaN", row.reportedTotal().originalValue().rawText());
        assertNull(row.reportedTotal().originalValue().numericValue());
    }

    @Test
    void preservesFormulaCacheErrorsAndReportedTotalWithoutRecalculation() {
        CellValue formulaType = new CellValue(CellType.FORMULA, null, null, null);
        SourceCell total = new SourceCell(SHEET, "E8", formulaType, "SUM(B8:D8)", number("23.00"), null);
        SourceCell noCache = new SourceCell(SHEET, "C8", formulaType, "B8*2", null, null);
        SourceCell errorCache = new SourceCell(SHEET, "D8", formulaType, "1/0",
                new CellValue(CellType.ERROR, "#DIV/0!", null, 7), null);
        ObservationRow row = new ObservationRow(SHEET, 8, cell("A8", number("2040")), OptionalInt.of(2040),
                Map.of(Species.CATTLE, cell("B8", number("4")),
                        Species.HORSES, noCache, Species.DEER, errorCache), total);

        assertEquals(CellType.FORMULA, row.reportedTotal().originalValue().type());
        assertEquals("SUM(B8:D8)", row.reportedTotal().formula());
        assertEquals(new BigDecimal("23.00"), row.reportedTotal().cachedResult().numericValue());
        assertEquals("23.00", row.reportedTotal().cachedResult().rawText());
        assertEquals("B8*2", row.speciesCells().get(Species.HORSES).formula());
        assertNull(row.speciesCells().get(Species.HORSES).cachedResult());
        CellValue error = row.speciesCells().get(Species.DEER).cachedResult();
        assertEquals(CellType.ERROR, error.type());
        assertEquals("#DIV/0!", error.rawText());
        assertEquals(7, error.errorCode().intValue());
    }

    @Test
    void preservesFontColourAndNotesWithReportedDatePrecision() {
        CellStyle style = new CellStyle(4, new FontColour(null, null, 5, new BigDecimal("-0.2"), null));
        SourceCell count = new SourceCell(SHEET, "B10", number("5"), null, null, style);
        SourceNote warning = new SourceNote(SHEET, "B18", "Colour identifies qualified entries.", null);
        SourceNote event = new SourceNote(SHEET, "G19", "A transfer occurred from April 2040 to 2042.",
                new ReportedDates("April 2040", "2042"));
        ObservationRow row = new ObservationRow(SHEET, 10, cell("A10", number("2040")), OptionalInt.of(2040),
                Map.of(Species.CATTLE, count), cell("E10", number("5")));
        HerbivoreWorkbook workbook = new HerbivoreWorkbook(
                List.of(new ObservationBlock(ObservationType.POPULATION, List.of(row))), List.of(warning, event));

        CellStyle storedStyle = workbook.blocks().getFirst().rows().getFirst()
                .speciesCells().get(Species.CATTLE).style();
        assertEquals(4, storedStyle.styleIndex());
        assertEquals(5, storedStyle.fontColour().theme().intValue());
        assertEquals(new BigDecimal("-0.2"), storedStyle.fontColour().tint());
        assertEquals(SHEET, workbook.notes().getFirst().worksheet());
        assertEquals("B18", workbook.notes().getFirst().address());
        assertEquals("Colour identifies qualified entries.", workbook.notes().getFirst().text());
        assertNull(workbook.notes().getFirst().dates());
        assertEquals(new ReportedDates("April 2040", "2042"), workbook.notes().getLast().dates());
    }

    @Test
    void copiesCollectionsAndPreservesWorksheetSpeciesOrder() {
        Map<Species, SourceCell> species = new LinkedHashMap<>();
        species.put(Species.HORSES, cell("B6", number("2")));
        species.put(Species.DEER, cell("C6", number("3")));
        species.put(Species.CATTLE, cell("D6", number("1")));
        ObservationRow row = new ObservationRow(SHEET, 6, cell("A6", number("2040")), OptionalInt.of(2040),
                species, cell("E6", number("6")));
        List<ObservationRow> rows = new ArrayList<>(List.of(row));
        ObservationBlock block = new ObservationBlock(ObservationType.POPULATION, rows);
        List<ObservationBlock> blocks = new ArrayList<>(List.of(block));
        List<SourceNote> notes = new ArrayList<>(List.of(new SourceNote(SHEET, "A20", "Source note.", null)));
        HerbivoreWorkbook workbook = new HerbivoreWorkbook(blocks, notes);

        species.clear();
        rows.clear();
        blocks.clear();
        notes.clear();
        assertEquals(List.of(Species.HORSES, Species.DEER, Species.CATTLE), List.copyOf(row.speciesCells().keySet()));
        assertEquals(List.of(row), block.rows());
        assertEquals(List.of(block), workbook.blocks());
        assertEquals(1, workbook.notes().size());
        assertThrows(UnsupportedOperationException.class, () -> row.speciesCells().clear());
        assertThrows(UnsupportedOperationException.class, () -> block.rows().clear());
        assertThrows(UnsupportedOperationException.class, () -> workbook.blocks().clear());
        assertThrows(UnsupportedOperationException.class, () -> workbook.notes().clear());
    }

    private static ObservationRow row(int rowNumber, int year) {
        return new ObservationRow(SHEET, rowNumber, cell("A" + rowNumber, number(Integer.toString(year))),
                OptionalInt.of(year), Map.of(), cell("E" + rowNumber, new CellValue(CellType.ABSENT, null, null, null)));
    }

    private static SourceCell cell(String address, CellValue value) {
        return new SourceCell(SHEET, address, value, null, null, null);
    }

    private static CellValue number(String text) {
        return new CellValue(CellType.NUMERIC, text, new BigDecimal(text), null);
    }
}
