package com.example.app.data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.regex.Pattern;

import com.example.app.data.HerbivoreWorkbook.CellType;
import com.example.app.data.HerbivoreWorkbook.CellValue;
import com.example.app.data.HerbivoreWorkbook.ObservationRow;
import com.example.app.data.HerbivoreWorkbook.ObservationType;
import com.example.app.data.HerbivoreWorkbook.SourceCell;
import com.example.app.data.HerbivoreWorkbook.SourceNote;
import com.example.app.model.Species;

/** Reports source quality and derived quantities without altering observations. */
public final class HistoricalDataValidator {
    private static final BigDecimal SOURCE_WARNING_TINT = new BigDecimal("-0.249977111117893");
    private static final Pattern NON_FINITE = Pattern.compile("[+-]?(?:nan|inf(?:inity)?|∞)",
            Pattern.CASE_INSENSITIVE);

    public enum Severity { INFO, WARNING, ERROR }

    public enum Code {
        MISSING_CELL, BLANK_CELL, FORMULA_CACHE_UNAVAILABLE, SPREADSHEET_ERROR, NON_NUMERIC_COUNT,
        INVALID_NUMERIC_TOKEN, NON_FINITE_COUNT, NEGATIVE_COUNT, FRACTIONAL_COUNT,
        INVALID_YEAR, DUPLICATE_YEAR, DECREASING_YEAR, MISSING_YEARS,
        TOTAL_UNAVAILABLE, TOTAL_MISMATCH, SOURCE_RELIABILITY_WARNING, RELIABILITY_LINK_AMBIGUOUS,
        MANAGEMENT_INTERVENTION, MORTALITY_CONTEXT, DEMOGRAPHIC_DIFFERENCE
    }

    public enum Alignment {
        UNCONFIRMED, BIRTHS_AND_DEATHS_YEAR_T_APPLY_TO_INTERVAL_T_TO_T_PLUS_1
    }

    public enum EventType { SHOOTING, RELOCATION, REPORTED_MORTALITY }

    /** Nullable coordinates indicate that the diagnostic is not tied to that dimension. */
    public record Diagnostic(Code code, Severity severity, ObservationType type, OptionalInt year,
                             Species species, String worksheet, String address,
                             String description, String consequence) {
    }

    /** Complete means a usable year and three usable species counts. Total checks are reported separately. */
    public record BlockSummary(int checked, int complete) {
        public int incomplete() {
            return checked - complete;
        }
    }

    /** Source total cell (including any formula) and independently derived species sum, when available. */
    public record TotalComparison(ObservationType type, OptionalInt year, SourceCell reportedCell,
                                  BigDecimal reportedCount, BigDecimal calculatedCount) {
        public boolean comparable() {
            return reportedCount != null && calculatedCount != null;
        }

        public boolean matches() {
            return comparable() && reportedCount.compareTo(calculatedCount) == 0;
        }
    }

    public record YearGap(ObservationType type, int firstMissing, int lastMissing, SourceCell nextYearCell) {
    }

    public record ContextEvent(EventType type, SourceNote note) {
    }

    /** Null species denotes the combined total. Null value denotes an unavailable calculation. */
    public record Residual(OptionalInt year, Species species, BigDecimal value, String unavailableReason) {
    }

    public record ValidationReport(Map<ObservationType, BlockSummary> summaries, List<Diagnostic> diagnostics,
                                   List<TotalComparison> totals, List<YearGap> yearGaps,
                                   List<ContextEvent> events, Alignment alignment, List<Residual> residuals) {
        public ValidationReport {
            summaries = Map.copyOf(summaries);
            diagnostics = List.copyOf(diagnostics);
            totals = List.copyOf(totals);
            yearGaps = List.copyOf(yearGaps);
            events = List.copyOf(events);
            residuals = List.copyOf(residuals);
        }

        public Map<Severity, Long> countsBySeverity() {
            Map<Severity, Long> counts = new EnumMap<>(Severity.class);
            for (Severity severity : Severity.values()) {
                counts.put(severity, 0L);
            }
            diagnostics.forEach(diagnostic -> counts.merge(diagnostic.severity(), 1L, Long::sum));
            return Map.copyOf(counts);
        }

        public Map<Code, Long> countsByCode() {
            Map<Code, Long> counts = new EnumMap<>(Code.class);
            diagnostics.forEach(diagnostic -> counts.merge(diagnostic.code(), 1L, Long::sum));
            return Map.copyOf(counts);
        }
    }

    private record CheckedRow(ObservationRow source, Map<Species, BigDecimal> counts, TotalComparison total) {
    }

    private HistoricalDataValidator() {
    }

    public static ValidationReport validate(HerbivoreWorkbook workbook) {
        return validate(workbook, Alignment.UNCONFIRMED);
    }

    public static ValidationReport validate(HerbivoreWorkbook workbook, Alignment alignment) {
        java.util.Objects.requireNonNull(alignment, "alignment");
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<TotalComparison> totals = new ArrayList<>();
        List<YearGap> gaps = new ArrayList<>();
        Map<ObservationType, BlockSummary> summaries = new EnumMap<>(ObservationType.class);
        Map<ObservationType, TreeMap<Integer, List<CheckedRow>>> indexed = new EnumMap<>(ObservationType.class);
        for (ObservationType type : ObservationType.values()) {
            indexed.put(type, new TreeMap<>());
            summaries.put(type, new BlockSummary(0, 0));
        }
        for (var block : workbook.blocks()) {
            int complete = 0;
            Integer previous = null;
            TreeMap<Integer, List<CheckedRow>> blockYears = new TreeMap<>();
            for (ObservationRow row : block.rows()) {
                CheckedRow checked = checkRow(block.type(), row, diagnostics);
                totals.add(checked.total());
                if (checked.total().year().isEmpty()) {
                    continue;
                }
                int year = checked.total().year().getAsInt();
                if (checked.counts().size() == Species.COUNT) {
                    complete++;
                }
                if (previous != null && year < previous) {
                    add(diagnostics, Code.DECREASING_YEAR, Severity.WARNING, block.type(), checked.total().year(),
                            null, row.yearCell(), "Year " + year + " follows " + previous + " in source order.",
                            "A chronological analysis cannot use source row order as time order.");
                }
                previous = year;
                blockYears.computeIfAbsent(year, ignored -> new ArrayList<>()).add(checked);
                indexed.get(block.type()).computeIfAbsent(year, ignored -> new ArrayList<>()).add(checked);
            }
            findGaps(block.type(), blockYears, gaps, diagnostics);
            summaries.merge(block.type(), new BlockSummary(block.rows().size(), complete),
                    (first, second) -> new BlockSummary(first.checked() + second.checked(),
                            first.complete() + second.complete()));
        }
        indexed.forEach((type, years) -> years.forEach((year, rows) -> {
            if (rows.size() > 1) {
                for (CheckedRow row : rows) {
                    add(diagnostics, Code.DUPLICATE_YEAR, Severity.ERROR, type, OptionalInt.of(year), null,
                            row.source().yearCell(), "Year " + year + " appears " + rows.size() + " times.",
                            "A unique year join cannot select one of the source rows.");
                }
            }
        }));
        List<ContextEvent> events = checkNotes(workbook, diagnostics);
        List<Residual> residuals = demographicResiduals(indexed, alignment, diagnostics);
        return new ValidationReport(summaries, diagnostics, totals, gaps, events, alignment, residuals);
    }

    private static CheckedRow checkRow(ObservationType type, ObservationRow row, List<Diagnostic> diagnostics) {
        OptionalInt year = usableYear(row);
        if (year.isEmpty()) {
            add(diagnostics, Code.INVALID_YEAR, Severity.ERROR, type, year, null, row.yearCell(),
                    "Year is missing, invalid, or does not agree with the original year cell.",
                    "This row cannot be joined to a calendar year.");
        }
        Map<Species, BigDecimal> counts = new EnumMap<>(Species.class);
        for (Species species : Species.values()) {
            BigDecimal count = checkCount(type, year, species, row.speciesCells().get(species), row, diagnostics);
            if (count != null) {
                counts.put(species, count);
            }
        }
        SourceCell totalCell = row.reportedTotal();
        BigDecimal reported = checkCount(type, year, null, totalCell, row, diagnostics);
        BigDecimal calculated = counts.size() == Species.COUNT
                ? counts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        TotalComparison total = new TotalComparison(type, year, totalCell, reported, calculated);
        if (!total.comparable()) {
            add(diagnostics, Code.TOTAL_UNAVAILABLE, Severity.INFO, type, year, null, totalCell,
                    "The reported total or one or more species counts are unavailable or unusable.",
                    "A complete reported-versus-calculated total comparison cannot be made.");
        } else if (!total.matches()) {
            add(diagnostics, Code.TOTAL_MISMATCH, Severity.WARNING, type, year, null, totalCell,
                    "Workbook total " + reported + " differs from the species sum " + calculated + ".",
                    "The conflicting totals cannot be treated as interchangeable.");
        }
        return new CheckedRow(row, counts, total);
    }

    private static OptionalInt usableYear(ObservationRow row) {
        CellValue source = effectiveValue(row.yearCell());
        if (row.year().isEmpty() || source == null || countProblem(source) != null) {
            return OptionalInt.empty();
        }
        int year = row.year().getAsInt();
        return year > 0 && source.numericValue().compareTo(BigDecimal.valueOf(year)) == 0
                ? row.year() : OptionalInt.empty();
    }

    private static CellValue effectiveValue(SourceCell cell) {
        return cell.originalValue().type() == CellType.FORMULA ? cell.cachedResult() : cell.originalValue();
    }

    private static BigDecimal checkCount(ObservationType type, OptionalInt year, Species species, SourceCell cell,
                                         ObservationRow row, List<Diagnostic> diagnostics) {
        CellValue value = cell == null ? null : effectiveValue(cell);
        Code problem;
        if (cell == null) {
            problem = Code.MISSING_CELL;
        } else if (cell.originalValue().type() == CellType.FORMULA
                && (value == null || value.type() == CellType.BLANK || value.type() == CellType.ABSENT)) {
            problem = Code.FORMULA_CACHE_UNAVAILABLE;
        } else {
            problem = countProblem(value);
        }
        if (problem == null) {
            return value.numericValue();
        }
        Severity severity = switch (problem) {
            case MISSING_CELL, BLANK_CELL, FORMULA_CACHE_UNAVAILABLE -> Severity.WARNING;
            default -> Severity.ERROR;
        };
        String explanation = switch (problem) {
            case MISSING_CELL -> "Cell is absent.";
            case BLANK_CELL -> "Cell is explicitly blank.";
            case FORMULA_CACHE_UNAVAILABLE -> "Formula has no usable cached result.";
            case SPREADSHEET_ERROR -> "Excel cell or cached formula result contains an error: " + value.rawText();
            case NON_FINITE_COUNT -> "Numerical token is non-finite: " + value.rawText();
            case INVALID_NUMERIC_TOKEN -> "Numerical cell has no readable decimal value: " + value.rawText();
            case NEGATIVE_COUNT -> "Count is negative: " + value.numericValue();
            case FRACTIONAL_COUNT -> "Count is fractional: " + value.numericValue();
            default -> "Cell or cached formula result is not numeric.";
        };
        String label = species == null ? "Reported total" : species.displayName();
        diagnostics.add(new Diagnostic(problem, severity, type, year, species,
                cell == null ? row.worksheet() : cell.worksheet(), cell == null ? null : cell.address(),
                label + ": " + explanation, "This value cannot be used as an animal count."));
        return null;
    }

    private static Code countProblem(CellValue value) {
        return switch (value.type()) {
            case ABSENT -> Code.MISSING_CELL;
            case BLANK -> Code.BLANK_CELL;
            case ERROR -> Code.SPREADSHEET_ERROR;
            case NUMERIC -> {
                String token = value.rawText();
                BigDecimal number = value.numericValue();
                if (token != null && NON_FINITE.matcher(token.strip()).matches()) {
                    yield Code.NON_FINITE_COUNT;
                }
                if (number == null) {
                    yield Code.INVALID_NUMERIC_TOKEN;
                }
                if (number.signum() < 0) {
                    yield Code.NEGATIVE_COUNT;
                }
                yield number.stripTrailingZeros().scale() > 0 ? Code.FRACTIONAL_COUNT : null;
            }
            default -> Code.NON_NUMERIC_COUNT;
        };
    }

    private static void findGaps(ObservationType type, TreeMap<Integer, List<CheckedRow>> years,
                                 List<YearGap> gaps, List<Diagnostic> diagnostics) {
        Integer previous = null;
        for (var entry : years.entrySet()) {
            int year = entry.getKey();
            if (previous != null && (long) year - previous > 1) {
                SourceCell cell = entry.getValue().getFirst().source().yearCell();
                YearGap gap = new YearGap(type, previous + 1, year - 1, cell);
                gaps.add(gap);
                String interval = gap.firstMissing() == gap.lastMissing() ? Integer.toString(gap.firstMissing())
                        : gap.firstMissing() + " through " + gap.lastMissing();
                add(diagnostics, Code.MISSING_YEARS, Severity.WARNING, type, OptionalInt.of(gap.firstMissing()),
                        null, cell, "Missing calendar coverage: " + interval + ".",
                        "A multi-year gap cannot be used as a single annual transition.");
            }
            previous = year;
        }
    }

    private static List<ContextEvent> checkNotes(HerbivoreWorkbook workbook, List<Diagnostic> diagnostics) {
        List<ContextEvent> events = new ArrayList<>();
        for (SourceNote note : workbook.notes()) {
            String text = note.text().toLowerCase(Locale.ROOT);
            if (text.contains("blue") && (text.contains("trustworthy") || text.contains("uncertain")
                    || text.contains("reliab"))) {
                checkReliability(workbook, note, diagnostics);
            }
            EventType event = null;
            if (text.contains("were shot") || text.contains("shooting")) {
                event = EventType.SHOOTING;
            } else if (text.contains("were moved") || text.contains("relocat")) {
                event = EventType.RELOCATION;
            } else if (text.contains("died") || text.contains("mortality")) {
                event = EventType.REPORTED_MORTALITY;
            }
            if (event != null) {
                events.add(new ContextEvent(event, note));
                Code code = event == EventType.REPORTED_MORTALITY ? Code.MORTALITY_CONTEXT
                        : Code.MANAGEMENT_INTERVENTION;
                diagnostics.add(new Diagnostic(code, Severity.INFO, null, OptionalInt.empty(), null,
                        note.worksheet(), note.address(), note.text(),
                        "Context for calibration without allocating event counts to calendar years."));
            }
        }
        return events;
    }

    private static void checkReliability(HerbivoreWorkbook workbook, SourceNote note, List<Diagnostic> diagnostics) {
        boolean linked = false;
        for (var block : workbook.blocks()) {
            if (block.type() != ObservationType.POPULATION) {
                continue;
            }
            for (ObservationRow row : block.rows()) {
                for (var entry : row.speciesCells().entrySet()) {
                    linked |= flagReliability(note, row, entry.getKey(), entry.getValue(), diagnostics);
                }
                linked |= flagReliability(note, row, null, row.reportedTotal(), diagnostics);
            }
        }
        if (!linked) {
            diagnostics.add(new Diagnostic(Code.RELIABILITY_LINK_AMBIGUOUS, Severity.WARNING,
                    ObservationType.POPULATION, OptionalInt.empty(), null, note.worksheet(), note.address(),
                    "The reliability note could not be linked to cells using the documented source font.",
                    "A colour convention cannot establish numerical uncertainty or justify automatic exclusion."));
        }
    }

    private static boolean flagReliability(SourceNote note, ObservationRow row, Species species, SourceCell cell,
                                            List<Diagnostic> diagnostics) {
        var colour = cell.style() == null ? null : cell.style().fontColour();
        // Match the documented font only in the scope of a source warning on the same sheet.
        if (!note.worksheet().equals(cell.worksheet()) || colour == null
                || !Integer.valueOf(8).equals(colour.theme()) || colour.tint() == null
                || colour.tint().compareTo(SOURCE_WARNING_TINT) != 0) {
            return false;
        }
        add(diagnostics, Code.SOURCE_RELIABILITY_WARNING, Severity.WARNING, ObservationType.POPULATION,
                usableYear(row), species, cell, "The cell font matches the qualitative warning at " + note.address() + ".",
                "The observation remains available. The source does not quantify its uncertainty.");
        return true;
    }

    private static List<Residual> demographicResiduals(Map<ObservationType, TreeMap<Integer, List<CheckedRow>>> index,
                                                        Alignment alignment, List<Diagnostic> diagnostics) {
        List<Residual> results = new ArrayList<>();
        if (alignment == Alignment.UNCONFIRMED) {
            return List.of(new Residual(OptionalInt.empty(), null, null,
                    "Birth and mortality reporting-period alignment was not explicitly supplied."));
        }
        var population = index.get(ObservationType.POPULATION);
        if (population.isEmpty()) {
            return List.of(new Residual(OptionalInt.empty(), null, null, "No usable population years are available."));
        }
        for (int year : population.keySet()) {
            List<CheckedRow> start = population.get(year);
            List<CheckedRow> end = year == Integer.MAX_VALUE ? null : population.get(year + 1);
            List<CheckedRow> births = index.get(ObservationType.BIRTHS).get(year);
            List<CheckedRow> deaths = index.get(ObservationType.MORTALITY).get(year);
            String reason = transitionProblem(start, end, births, deaths);
            if (reason != null) {
                for (Species species : Species.values()) {
                    results.add(new Residual(OptionalInt.of(year), species, null, reason));
                }
                results.add(new Residual(OptionalInt.of(year), null, null, reason));
                continue;
            }
            List<CheckedRow> inputs = List.of(start.getFirst(), end.getFirst(), births.getFirst(), deaths.getFirst());
            for (Species species : Species.values()) {
                results.add(calculate(year, species, inputs, diagnostics));
            }
            results.add(calculate(year, null, inputs, diagnostics));
        }
        return results;
    }

    private static String transitionProblem(List<CheckedRow> start, List<CheckedRow> end,
                                            List<CheckedRow> births, List<CheckedRow> deaths) {
        if (start.size() != 1 || end != null && end.size() != 1) {
            return "Duplicate population year prevents a unique transition.";
        }
        if (end == null) {
            return "The following January population record is missing.";
        }
        if (births == null || deaths == null) {
            return "A corresponding birth or mortality record is missing.";
        }
        if (births.size() != 1 || deaths.size() != 1) {
            return "Duplicate birth or mortality records prevent a unique transition.";
        }
        return null;
    }

    private static Residual calculate(int year, Species species, List<CheckedRow> inputs,
                                      List<Diagnostic> diagnostics) {
        OptionalInt sourceYear = OptionalInt.of(year);
        List<BigDecimal> values = inputs.stream()
                .map(row -> species == null ? (row.total().matches() ? row.total().reportedCount() : null)
                        : row.counts().get(species)).toList();
        if (values.contains(null)) {
            return new Residual(sourceYear, species, null, species == null
                    ? "The combined residual requires complete, consistent reported totals in all four source rows."
                    : "A required species count is missing or invalid.");
        }
        BigDecimal residual = values.get(1).subtract(values.get(0)).subtract(values.get(2).subtract(values.get(3)));
        if (residual.signum() != 0) {
            CheckedRow start = inputs.getFirst();
            SourceCell cell = species == null ? start.source().reportedTotal() : start.source().speciesCells().get(species);
            add(diagnostics, Code.DEMOGRAPHIC_DIFFERENCE, Severity.WARNING, ObservationType.POPULATION,
                    sourceYear, species, cell, "Unexplained demographic accounting difference: " + residual + ".",
                    "The difference is conditional on same-year reporting alignment and does not identify a cause.");
        }
        return new Residual(sourceYear, species, residual, null);
    }

    private static void add(List<Diagnostic> diagnostics, Code code, Severity severity, ObservationType type,
                            OptionalInt year, Species species, SourceCell cell, String description, String consequence) {
        diagnostics.add(new Diagnostic(code, severity, type, year, species, cell.worksheet(), cell.address(),
                description, consequence));
    }
}
