package com.example.app.data;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

import com.example.app.model.Species;

/** Source records in workbook order. Each block has its own years and reporting period. */
public record HerbivoreWorkbook(List<ObservationBlock> blocks, List<SourceNote> notes) {

    public HerbivoreWorkbook {
        blocks = List.copyOf(blocks);
        notes = List.copyOf(notes);
    }

    public enum ObservationType {
        POPULATION, BIRTHS, MORTALITY
    }

    public record ObservationBlock(ObservationType type, List<ObservationRow> rows) {
        public ObservationBlock {
            Objects.requireNonNull(type, "type");
            rows = List.copyOf(rows);
        }
    }

    /** Species cells are supplied in worksheet column order. Row numbers are one-based. */
    public record ObservationRow(String worksheet, int rowNumber, SourceCell yearCell,
                                 OptionalInt year, Map<Species, SourceCell> speciesCells,
                                 SourceCell reportedTotal) {
        public ObservationRow {
            Objects.requireNonNull(worksheet, "worksheet");
            if (rowNumber < 1) {
                throw new IllegalArgumentException("rowNumber must be one-based");
            }
            Objects.requireNonNull(yearCell, "yearCell");
            Objects.requireNonNull(year, "year");
            Map<Species, SourceCell> orderedCells = new LinkedHashMap<>();
            speciesCells.forEach((species, cell) -> orderedCells.put(
                    Objects.requireNonNull(species, "species"), Objects.requireNonNull(cell, "cell")));
            speciesCells = Collections.unmodifiableMap(orderedCells);
            Objects.requireNonNull(reportedTotal, "reportedTotal");
        }
    }

    public enum CellType {
        ABSENT, BLANK, NUMERIC, TEXT, BOOLEAN, ERROR, FORMULA, UNKNOWN
    }

    /**
     * Raw source payload, also used for cached formula results. A null numericValue
     * indicates no decimal representation. Raw text carries the original token when
     * available, including unreadable numeric tokens. Error codes are optional.
     */
    public record CellValue(CellType type, String rawText, BigDecimal numericValue, Integer errorCode) {
        public CellValue {
            Objects.requireNonNull(type, "type");
        }
    }

    /**
     * A cell at an Excel address, including an address where no cell was stored.
     * Formula expression, cached result and style are null when unavailable.
     */
    public record SourceCell(String worksheet, String address, CellValue originalValue,
                             String formula, CellValue cachedResult, CellStyle style) {
        public SourceCell {
            Objects.requireNonNull(worksheet, "worksheet");
            Objects.requireNonNull(address, "address");
            Objects.requireNonNull(originalValue, "originalValue");
        }
    }

    /** Workbook-local style index and original font colour. Colour may be absent. */
    public record CellStyle(int styleIndex, FontColour fontColour) {
    }

    /** Source colour attributes. Each may be absent if the workbook does not supply it. */
    public record FontColour(String argb, Integer indexed, Integer theme, BigDecimal tint,
                             Boolean automatic) {
    }

    /** Verbatim note with optional source-supplied dates. */
    public record SourceNote(String worksheet, String address, String text, ReportedDates dates) {
        public SourceNote {
            Objects.requireNonNull(worksheet, "worksheet");
            Objects.requireNonNull(address, "address");
            Objects.requireNonNull(text, "text");
        }
    }

    /** Date or range endpoints as written. A null end denotes a single date. */
    public record ReportedDates(String start, String end) {
        public ReportedDates {
            Objects.requireNonNull(start, "start");
        }
    }
}
