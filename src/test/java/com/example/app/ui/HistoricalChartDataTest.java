package com.example.app.ui;

import static com.example.app.data.HerbivoreWorkbook.ObservationType.BIRTHS;
import static com.example.app.data.HerbivoreWorkbook.ObservationType.POPULATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import com.example.app.data.HerbivoreWorkbook;
import com.example.app.data.HerbivoreWorkbook.CellStyle;
import com.example.app.data.HerbivoreWorkbook.CellType;
import com.example.app.data.HerbivoreWorkbook.CellValue;
import com.example.app.data.HerbivoreWorkbook.FontColour;
import com.example.app.data.HerbivoreWorkbook.ObservationBlock;
import com.example.app.data.HerbivoreWorkbook.ObservationRow;
import com.example.app.data.HerbivoreWorkbook.SourceCell;
import com.example.app.data.HerbivoreWorkbook.SourceNote;
import com.example.app.data.HistoricalDataValidator;
import com.example.app.model.Species;

class HistoricalChartDataTest {
    private static final String SHEET = "Synthetic";

    @Test
    void mapsOnlyRecordedPopulationCountsWithOriginalProvenance() {
        var first = row(6, 2040, number("B6", "0"));
        var second = row(7, 2041, number("B7", "18"));
        var workbook = new HerbivoreWorkbook(List.of(new ObservationBlock(POPULATION, List.of(first, second)),
                new ObservationBlock(BIRTHS, List.of(row(8, 2042, number("B8", "25"))))), List.of());
        var data = HistoricalChartData.from(review(workbook));

        assertNull(data.unavailableReason());
        assertEquals(List.of(Species.CATTLE, Species.HORSES, Species.DEER),
                data.segments().stream().map(HistoricalChartData.Segment::species).toList());
        assertEquals(List.of(List.of("0", "18"), List.of("20", "20"), List.of("30", "30")),
                data.segments().stream().map(segment -> segment.points().stream()
                        .map(point -> point.count().toPlainString()).toList()).toList());
        for (var segment : data.segments()) {
            var point = segment.points().getFirst();
            assertEquals(2040, point.year());
            assertEquals(SHEET, point.source().worksheet());
            assertSame(first.speciesCells().get(segment.species()), point.source());
        }
        assertEquals("B6", data.segments().getFirst().points().getFirst().source().address());
        assertThrows(UnsupportedOperationException.class, () -> data.segments().getFirst().points().clear());
        assertEquals(List.of(first, second), workbook.blocks().getFirst().rows());
    }

    @Test
    void splitsLinesAtMissingInvalidAndUnrecordedYearsAndRetainsSingletons() {
        var blank = new SourceCell(SHEET, "B7", new CellValue(CellType.BLANK, null, null, null), null, null, null);
        var text = new SourceCell(SHEET, "B10", new CellValue(CellType.TEXT, "99", null, null), null, null, null);
        var data = HistoricalChartData.from(review(workbook(
                row(6, 2040, number("B6", "1")), row(7, 2041, blank),
                row(8, 2042, number("B8", "2")), row(9, 2043, number("B9", "3")),
                row(10, 2044, text), row(11, 2045, number("B11", "-1")),
                row(12, 2046, number("B12", "1.5")), row(13, 2047, number("B13", "5")),
                row(14, 2049, number("B14", "6")))));
        assertEquals(List.of(List.of(2040), List.of(2042, 2043), List.of(2047), List.of(2049)),
                data.segments().stream().filter(segment -> segment.species() == Species.CATTLE)
                        .map(segment -> segment.points().stream().map(HistoricalChartData.Point::year).toList()).toList());
    }

    @Test
    void withholdsChartsWithDuplicateDecreasingOrUninterpretableYears() {
        var first = row(6, 2040, number("B6", "5"));
        var invalid = new ObservationRow(SHEET, 7, number("A7", "2040.5"), OptionalInt.empty(),
                first.speciesCells(), first.reportedTotal());
        for (var second : List.of(row(7, 2040, number("B7", "9")),
                row(7, 2039, number("B7", "9")), invalid)) {
            var result = HistoricalChartData.from(review(workbook(first, second)));
            assertTrue(result.segments().isEmpty());
            assertTrue(result.unavailableReason().contains("Diagnostics"));
        }
    }

    @Test
    void plotsQualifiedValuesAndAssociatesTheValidatorWarningWithTheSourceCell() {
        var font = new FontColour(null, null, 8, new BigDecimal("-0.249977111117893"), null);
        var original = number("B6", "12");
        var styled = new SourceCell(SHEET, "B6", original.originalValue(), null, null, new CellStyle(8, font));
        var row = row(6, 2040, styled);
        var note = new SourceNote(SHEET, "B20", "Blue entries may be unreliable.", null);
        var workbook = new HerbivoreWorkbook(List.of(new ObservationBlock(POPULATION, List.of(row))), List.of(note));
        var result = HistoricalChartData.from(review(workbook));
        var point = result.segments().getFirst().points().getFirst();

        assertEquals(new BigDecimal("12"), point.count());
        assertSame(styled, point.source());
        assertTrue(point.reliabilityWarning().contains("B20"));
        assertNull(HistoricalChartData.from(review(workbook(row))).segments().getFirst().points().getFirst()
                .reliabilityWarning());
    }

    @Test
    void preparesReplacementIndependentlyAndHandlesUnavailableReviews() {
        var first = HistoricalChartData.from(review(workbook(row(6, 2040, number("B6", "5")))));
        var replacement = HistoricalChartData.from(review(workbook(row(9, 2070, number("B9", "10")))));
        assertEquals(2040, first.segments().getFirst().points().getFirst().year());
        assertEquals(2070, replacement.segments().getFirst().points().getFirst().year());
        assertTrue(HistoricalChartData.from(null).segments().isEmpty());
        assertTrue(HistoricalChartData.from(review(workbook())).unavailableReason().contains("No usable"));
    }

    private static HistoricalReview review(HerbivoreWorkbook workbook) {
        return new HistoricalReview(workbook, HistoricalDataValidator.validate(workbook));
    }

    private static HerbivoreWorkbook workbook(ObservationRow... rows) {
        return new HerbivoreWorkbook(List.of(new ObservationBlock(POPULATION, List.of(rows))), List.of());
    }

    private static ObservationRow row(int index, int year, SourceCell cattle) {
        return new ObservationRow(SHEET, index, number("A" + index, Integer.toString(year)), OptionalInt.of(year),
                Map.of(Species.CATTLE, cattle, Species.HORSES, number("C" + index, "20"),
                        Species.DEER, number("D" + index, "30")), number("E" + index, "50"));
    }

    private static SourceCell number(String address, String value) {
        return new SourceCell(SHEET, address, new CellValue(CellType.NUMERIC, value, new BigDecimal(value), null),
                null, null, null);
    }
}
