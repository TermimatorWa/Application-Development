package com.example.app.ui;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.app.data.HerbivoreWorkbook.CellType;
import com.example.app.data.HerbivoreWorkbook.ObservationType;
import com.example.app.data.HerbivoreWorkbook.SourceCell;
import com.example.app.data.HistoricalDataValidator.Code;
import com.example.app.data.HistoricalDataValidator.Severity;
import com.example.app.model.Species;

/** Observed population points grouped into separate, consecutive-year lines. */
record HistoricalChartData(List<Segment> segments, String unavailableReason) {
    static final List<Species> SPECIES_ORDER = List.of(Species.CATTLE, Species.HORSES, Species.DEER);

    HistoricalChartData {
        segments = List.copyOf(segments);
    }

    record Point(int year, BigDecimal count, SourceCell source, String reliabilityWarning) {
    }

    record Segment(Species species, List<Point> points) {
        Segment {
            points = List.copyOf(points);
        }
    }

    static HistoricalChartData from(HistoricalReview review) {
        if (review == null) {
            return unavailable("Select an Excel workbook to view its population chart.");
        }
        var rows = review.population();
        Set<String> invalidCells = new HashSet<>();
        Map<String, String> warnings = new HashMap<>();
        for (var diagnostic : review.report().diagnostics()) {
            if (diagnostic.type() != ObservationType.POPULATION) {
                continue;
            }
            if (diagnostic.code() == Code.DUPLICATE_YEAR || diagnostic.code() == Code.DECREASING_YEAR
                    || diagnostic.code() == Code.INVALID_YEAR) {
                return unavailable("Chart unavailable. Population years are missing, invalid, duplicated, "
                        + "or out of order. See Diagnostics for source locations.");
            }
            if (diagnostic.species() != null) {
                String location = HistoricalReview.location(diagnostic.worksheet(), diagnostic.address());
                if (diagnostic.severity() == Severity.ERROR) {
                    invalidCells.add(location);
                } else if (diagnostic.code() == Code.SOURCE_RELIABILITY_WARNING) {
                    warnings.put(location, diagnostic.description());
                }
            }
        }
        // Also check order across blocks, in case a review contains more than one population block.
        int previous = 0;
        for (var row : rows) {
            int year = row.year().orElse(0);
            if (year <= previous) {
                return unavailable("Chart unavailable. Population years must be present and strictly increasing "
                        + "in workbook order. Review the original observations.");
            }
            previous = year;
        }
        List<Segment> segments = new ArrayList<>();
        for (Species species : SPECIES_ORDER) {
            List<Point> points = new ArrayList<>();
            for (var row : rows) {
                int year = row.year().getAsInt();
                SourceCell cell = row.speciesCells().get(species);
                BigDecimal count = recordedCount(cell);
                String location = cell == null ? "" : HistoricalReview.location(cell.worksheet(), cell.address());
                boolean usable = count != null && !invalidCells.contains(location);
                if (!points.isEmpty() && (!usable || (long) year - points.getLast().year() != 1)) {
                    segments.add(new Segment(species, points));
                    points.clear();
                }
                if (usable) {
                    points.add(new Point(year, count, cell, warnings.get(location)));
                }
            }
            if (!points.isEmpty()) {
                segments.add(new Segment(species, points));
            }
        }
        return segments.isEmpty() ? unavailable("No usable population observations to plot.")
                : new HistoricalChartData(segments, null);
    }

    private static BigDecimal recordedCount(SourceCell cell) {
        if (cell == null) {
            return null;
        }
        var value = cell.originalValue().type() == CellType.FORMULA ? cell.cachedResult() : cell.originalValue();
        if (value == null || value.type() != CellType.NUMERIC || value.numericValue() == null
                || !Double.isFinite(value.numericValue().doubleValue())) {
            return null;
        }
        return value.numericValue();
    }

    private static HistoricalChartData unavailable(String reason) {
        return new HistoricalChartData(List.of(), reason);
    }
}
