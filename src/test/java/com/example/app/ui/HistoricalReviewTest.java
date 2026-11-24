package com.example.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.app.data.HerbivoreWorkbook;
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
import com.example.app.data.HistoricalDataValidator;
import com.example.app.data.HistoricalDataValidator.Alignment;
import com.example.app.data.HistoricalDataValidator.Code;
import com.example.app.model.Species;

class HistoricalReviewTest {
    @TempDir
    Path directory;

    @Test
    void presentsSummaryAndOriginalPopulationRows() {
        var year = numeric("A6", "2040");
        var zero = numeric("B6", "0");
        var blank = new SourceCell("Review", "D6", new CellValue(CellType.BLANK, null, null, null), null, null, null);
        var total = new SourceCell("Review", "E6", new CellValue(CellType.FORMULA, null, null, null),
                "SUM(B6:D6)", numeric("E6", "0").originalValue(), null);
        var row = new ObservationRow("Review", 6, year, OptionalInt.of(2040),
                Map.of(Species.CATTLE, zero, Species.DEER, blank), total);
        var workbook = new HerbivoreWorkbook(List.of(new ObservationBlock(ObservationType.POPULATION, List.of(row))),
                List.of());
        var review = new HistoricalReview(workbook, HistoricalDataValidator.validate(workbook));

        assertSame(row, review.population().getFirst());
        assertTrue(review.summary().contains("Population 1 (0 complete, 1 incomplete) · Mortality 0 · Births 0"));
        assertTrue(review.summary().contains("Diagnostics 3 · 1 informational · 2 warnings · 0 errors"));
        assertTrue(review.reviewStatus().contains("No complete usable population"));
        assertEquals("0", HistoricalReview.cellText(zero));
        assertEquals("0", HistoricalReview.cellText(total));
        assertEquals("Blank", HistoricalReview.cellText(blank));
        assertEquals("Missing", HistoricalReview.cellText(row.speciesCells().get(Species.HORSES)));
        assertEquals("Review!D6", HistoricalReview.location(blank.worksheet(), blank.address()));
        assertSame(total, row.reportedTotal());
    }

    @Test
    void exposesReliabilityDiagnosticsAndManagementEventsWithSourceReferences() {
        var font = new FontColour(null, null, 8, new BigDecimal("-0.249977111117893"), null);
        var cattle = new SourceCell("Review", "B6", numeric("B6", "2").originalValue(), null, null,
                new CellStyle(8, font));
        var row = new ObservationRow("Review", 6, numeric("A6", "2040"), OptionalInt.of(2040),
                Map.of(Species.CATTLE, cattle, Species.HORSES, numeric("C6", "3"), Species.DEER, numeric("D6", "4")),
                numeric("E6", "9"));
        var warning = new SourceNote("Review", "B20", "Blue entries may be unreliable.", null);
        var event = new SourceNote("Review", "J20", "Horses were relocated.", new ReportedDates("April 2040", "2042"));
        var workbook = new HerbivoreWorkbook(List.of(new ObservationBlock(ObservationType.POPULATION, List.of(row))),
                List.of(warning, event));
        var review = new HistoricalReview(workbook, HistoricalDataValidator.validate(workbook));
        var diagnostic = review.report().diagnostics().stream()
                .filter(item -> item.code() == Code.SOURCE_RELIABILITY_WARNING).findFirst().orElseThrow();

        assertEquals("Review!B6", HistoricalReview.location(diagnostic.worksheet(), diagnostic.address()));
        assertEquals(OptionalInt.of(2040), diagnostic.year());
        assertEquals(Species.CATTLE, diagnostic.species());
        assertSame(font, review.population().getFirst().speciesCells().get(Species.CATTLE).style().fontColour());
        assertSame(event, review.report().events().getFirst().note());
        assertEquals("April 2040 – 2042", HistoricalReview.dates(event));
        assertEquals(Alignment.UNCONFIRMED, review.report().alignment());
        assertEquals("Ready for scientific review with warnings.", review.reviewStatus());
    }

    @Test
    void reportsUnavailableAndInvalidInputsWithoutSubstitutingData() throws IOException {
        assertThrows(IOException.class, () -> HistoricalReview.load(directory.resolve("missing.xlsx")));
        var invalid = Files.writeString(directory.resolve("invalid.xlsx"), "not an Excel file");
        assertThrows(IOException.class, () -> HistoricalReview.load(invalid));
        var raw = new SourceCell("Review", "B9", new CellValue(CellType.NUMERIC, "NaN", null, null),
                null, null, null);
        var formula = new SourceCell("Review", "E9", new CellValue(CellType.FORMULA, null, null, null),
                "B9", null, null);
        assertEquals("NaN", HistoricalReview.cellText(raw));
        assertEquals("Formula result unavailable", HistoricalReview.cellText(formula));
    }

    private static SourceCell numeric(String address, String raw) {
        return new SourceCell("Review", address, new CellValue(CellType.NUMERIC, raw, new BigDecimal(raw), null),
                null, null, null);
    }
}
