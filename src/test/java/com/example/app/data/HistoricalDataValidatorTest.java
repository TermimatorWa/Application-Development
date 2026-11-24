package com.example.app.data;

import static com.example.app.data.HistoricalDataValidator.Alignment.BIRTHS_AND_DEATHS_YEAR_T_APPLY_TO_INTERVAL_T_TO_T_PLUS_1;
import static com.example.app.data.HerbivoreWorkbook.ObservationType.BIRTHS;
import static com.example.app.data.HerbivoreWorkbook.ObservationType.MORTALITY;
import static com.example.app.data.HerbivoreWorkbook.ObservationType.POPULATION;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import com.example.app.data.HerbivoreWorkbook.*;
import com.example.app.data.HistoricalDataValidator.*;
import com.example.app.model.Species;

class HistoricalDataValidatorTest {
    private static final String SHEET = "Synthetic";

    @Test
    void acceptsCompleteRowAndExplicitZeroAndComparesExactTotals() {
        var report = validate(row(6, 2040, number("B6", "0"), number("E6", "5.00")));
        assertEquals(new BlockSummary(1, 1), report.summaries().get(POPULATION));
        assertTrue(report.diagnostics().isEmpty());
        assertTrue(report.totals().getFirst().matches());
        assertEquals(new BigDecimal("5.00"), report.totals().getFirst().reportedCount());
    }

    @Test
    void preservesBlankAbsentAndPartialTotal() {
        var blank = cell("B6", CellType.BLANK, null);
        var absent = cell("B7", CellType.ABSENT, null);
        var report = validate(row(6, 2040, blank, number("E6", "5")), row(7, 2041, absent, number("E7", "5")));
        assertEquals(2, report.summaries().get(POPULATION).incomplete());
        assertEquals(Map.of(Code.BLANK_CELL, 1L, Code.MISSING_CELL, 1L, Code.TOTAL_UNAVAILABLE, 2L),
                report.countsByCode());
        assertEquals(new BigDecimal("5"), report.totals().getFirst().reportedCount());
        assertNull(report.totals().getFirst().calculatedCount());
        assertEquals("B7", diagnostic(report, Code.MISSING_CELL).address());
    }

    @Test
    void reportsNegativeFractionalMalformedNonFiniteTextAndFormulaProblems() {
        List<SourceCell> cells = List.of(number("B6", "-1"), number("B7", "1.5"),
                cell("B8", CellType.NUMERIC, "bad"), cell("B9", CellType.NUMERIC, "NaN"),
                cell("B10", CellType.TEXT, "12"), formula("B11", "1+1", null),
                formula("B12", "1/0", new CellValue(CellType.ERROR, "#DIV/0!", null, 7)));
        var report = validate(java.util.stream.IntStream.range(0, cells.size())
                .mapToObj(i -> row(6 + i, 2040 + i, cells.get(i), number("E" + (6 + i), "5")))
                .toArray(ObservationRow[]::new));
        List<Code> codes = List.of(Code.NEGATIVE_COUNT, Code.FRACTIONAL_COUNT, Code.INVALID_NUMERIC_TOKEN,
                Code.NON_FINITE_COUNT, Code.NON_NUMERIC_COUNT, Code.FORMULA_CACHE_UNAVAILABLE, Code.SPREADSHEET_ERROR);
        for (int i = 0; i < codes.size(); i++) {
            Diagnostic issue = diagnostic(report, codes.get(i));
            assertEquals("B" + (6 + i), issue.address());
            assertEquals(Species.CATTLE, issue.species());
        }
        assertEquals(7, report.summaries().get(POPULATION).incomplete());
    }

    @Test
    void reportsDuplicateDescendingMissingAndInvalidYearsWithoutReordering() {
        var first = row(6, 2043, 2);
        var second = row(7, 2040, 3);
        var duplicate = row(8, 2043, 4);
        var invalid = new ObservationRow(SHEET, 9, cell("A9", CellType.TEXT, "unknown"), OptionalInt.empty(),
                first.speciesCells(), first.reportedTotal());
        var block = block(POPULATION, first, second, duplicate, invalid);
        var report = HistoricalDataValidator.validate(workbook(block));
        assertEquals(List.of("A6", "A8"), report.diagnostics().stream()
                .filter(d -> d.code() == Code.DUPLICATE_YEAR).map(Diagnostic::address).toList());
        assertEquals("A7", diagnostic(report, Code.DECREASING_YEAR).address());
        assertEquals("A9", diagnostic(report, Code.INVALID_YEAR).address());
        assertEquals(new YearGap(POPULATION, 2041, 2042, first.yearCell()), report.yearGaps().getFirst());
        assertEquals(List.of(first, second, duplicate, invalid), block.rows());
    }

    @Test
    void keepsWorkbookFormulaAndReportsInconsistentTotalInEachBlock() {
        var total = formula("E6", "SUM(B6:D6)", number("E6", "8").originalValue());
        var source = row(6, 2040, number("B6", "2"), total);
        var report = HistoricalDataValidator.validate(workbook(block(POPULATION, source), block(BIRTHS, source),
                block(MORTALITY, source)));
        assertEquals(3L, report.countsByCode().get(Code.TOTAL_MISMATCH));
        for (ObservationType type : ObservationType.values()) {
            assertEquals(new BlockSummary(1, 1), report.summaries().get(type));
        }
        var comparison = report.totals().getFirst();
        assertSame(total, comparison.reportedCell());
        assertEquals(new BigDecimal("8"), comparison.reportedCount());
        assertEquals(new BigDecimal("7"), comparison.calculatedCount());
    }

    @Test
    void linksDocumentedFontOnlyWithSourceWarningAndReportsAmbiguity() {
        var font = new FontColour(null, null, 8, new BigDecimal("-0.249977111117893"), null);
        var base = number("B6", "2");
        var styled = new SourceCell(SHEET, "B6", base.originalValue(), null, null, new CellStyle(8, font));
        var warning = new SourceNote(SHEET, "B20", "Blue entries may be unreliable.", null);
        var block = block(POPULATION, row(6, 2040, styled, number("E6", "7")));
        assertTrue(HistoricalDataValidator.validate(workbook(block)).diagnostics().isEmpty());
        var report = HistoricalDataValidator.validate(new HerbivoreWorkbook(List.of(block), List.of(warning)));
        var issue = diagnostic(report, Code.SOURCE_RELIABILITY_WARNING);
        assertEquals(Species.CATTLE, issue.species());
        assertEquals(OptionalInt.of(2040), issue.year());
        assertEquals("B6", issue.address());
        assertSame(styled, block.rows().getFirst().speciesCells().get(Species.CATTLE));
        var unmatched = HistoricalDataValidator.validate(new HerbivoreWorkbook(List.of(), List.of(warning)));
        assertEquals("B20", diagnostic(unmatched, Code.RELIABILITY_LINK_AMBIGUOUS).address());
    }

    @Test
    void retainsOriginalEventNotesAndDatePrecisionWithoutChangingCounts() {
        var shooting = new SourceNote(SHEET, "J20", "Four deer were shot between April 2040 and 2042.",
                new ReportedDates("April 2040", "2042"));
        var relocation = new SourceNote(SHEET, "J21", "Horses were moved.", null);
        var winter = new SourceNote(SHEET, "J22", "Winter mortality was severe.", null);
        var original = row(6, 2040, 3);
        var workbook = new HerbivoreWorkbook(List.of(block(MORTALITY, original)), List.of(shooting, relocation, winter));
        var report = HistoricalDataValidator.validate(workbook);
        assertEquals(List.of(new ContextEvent(EventType.SHOOTING, shooting),
                new ContextEvent(EventType.RELOCATION, relocation), new ContextEvent(EventType.REPORTED_MORTALITY, winter)),
                report.events());
        assertSame(original, workbook.blocks().getFirst().rows().getFirst());
        assertEquals(OptionalInt.empty(), diagnostic(report, Code.MANAGEMENT_INTERVENTION).year());
    }

    @Test
    void calculatesSpeciesAndCombinedResidualUnderExplicitAlignment() {
        var workbook = workbook(block(POPULATION, row(6, 2040, 10), row(7, 2041, 15)),
                block(BIRTHS, row(6, 2040, 6)), block(MORTALITY, row(6, 2040, 2)));
        var report = HistoricalDataValidator.validate(workbook, BIRTHS_AND_DEATHS_YEAR_T_APPLY_TO_INTERVAL_T_TO_T_PLUS_1);
        assertEquals(BigDecimal.ONE, residual(report, 2040, Species.CATTLE).value());
        assertEquals(BigDecimal.ONE, residual(report, 2040, null).value());
        assertEquals(BigDecimal.ZERO, residual(report, 2040, Species.HORSES).value());
        assertEquals(2L, report.countsByCode().get(Code.DEMOGRAPHIC_DIFFERENCE));
        assertNull(residual(report, 2041, null).value());
        var defaultReport = HistoricalDataValidator.validate(workbook);
        assertNull(defaultReport.residuals().getFirst().value());
        assertTrue(defaultReport.residuals().getFirst().unavailableReason().contains("alignment"));
    }

    @Test
    void withholdsResidualsForMissingDuplicateOrUnusableInputs() {
        var pop = block(POPULATION, row(6, 2040, 10), row(7, 2041, 15));
        var deaths = block(MORTALITY, row(6, 2040, 2));
        var missing = HistoricalDataValidator.validate(workbook(pop, deaths),
                BIRTHS_AND_DEATHS_YEAR_T_APPLY_TO_INTERVAL_T_TO_T_PLUS_1);
        assertTrue(residual(missing, 2040, null).unavailableReason().contains("missing"));
        var duplicate = HistoricalDataValidator.validate(workbook(pop, deaths,
                block(BIRTHS, row(6, 2040, 6), row(7, 2040, 4))),
                BIRTHS_AND_DEATHS_YEAR_T_APPLY_TO_INTERVAL_T_TO_T_PLUS_1);
        assertTrue(residual(duplicate, 2040, Species.CATTLE).unavailableReason().contains("Duplicate"));
        var partial = HistoricalDataValidator.validate(workbook(pop, deaths,
                block(BIRTHS, row(6, 2040, cell("B6", CellType.BLANK, null), number("E6", "5")))),
                BIRTHS_AND_DEATHS_YEAR_T_APPLY_TO_INTERVAL_T_TO_T_PLUS_1);
        assertNull(residual(partial, 2040, null).value());
        assertNull(residual(partial, 2040, Species.CATTLE).value());
        assertEquals(BigDecimal.ZERO, residual(partial, 2040, Species.HORSES).value());
    }

    @Test
    void reportCollectionsAreImmutable() {
        var report = validate(row(6, 2040, 4));
        assertThrows(UnsupportedOperationException.class, () -> report.totals().clear());
        assertThrows(UnsupportedOperationException.class, () -> report.summaries().clear());
        assertThrows(UnsupportedOperationException.class, () -> report.residuals().clear());
    }

    private static Diagnostic diagnostic(ValidationReport report, Code code) {
        return report.diagnostics().stream().filter(d -> d.code() == code).findFirst().orElseThrow();
    }

    private static Residual residual(ValidationReport report, int year, Species species) {
        return report.residuals().stream().filter(r -> r.year().orElse(-1) == year && r.species() == species)
                .findFirst().orElseThrow();
    }

    private static ValidationReport validate(ObservationRow... rows) {
        return HistoricalDataValidator.validate(workbook(block(POPULATION, rows)));
    }

    private static HerbivoreWorkbook workbook(ObservationBlock... blocks) {
        return new HerbivoreWorkbook(List.of(blocks), List.of());
    }

    private static ObservationBlock block(ObservationType type, ObservationRow... rows) {
        return new ObservationBlock(type, List.of(rows));
    }

    private static ObservationRow row(int index, int year, int cattle) {
        return row(index, year, number("B" + index, Integer.toString(cattle)),
                number("E" + index, Integer.toString(cattle + 5)));
    }

    private static ObservationRow row(int index, int year, SourceCell cattle, SourceCell total) {
        return new ObservationRow(SHEET, index, number("A" + index, Integer.toString(year)), OptionalInt.of(year),
                Map.of(Species.CATTLE, cattle, Species.HORSES, number("C" + index, "2"),
                        Species.DEER, number("D" + index, "3")), total);
    }

    private static SourceCell number(String address, String raw) {
        return new SourceCell(SHEET, address, new CellValue(CellType.NUMERIC, raw, new BigDecimal(raw), null),
                null, null, null);
    }

    private static SourceCell cell(String address, CellType type, String raw) {
        return new SourceCell(SHEET, address, new CellValue(type, raw, null, null), null, null, null);
    }

    private static SourceCell formula(String address, String expression, CellValue cache) {
        return new SourceCell(SHEET, address, new CellValue(CellType.FORMULA, null, null, null),
                expression, cache, null);
    }
}
