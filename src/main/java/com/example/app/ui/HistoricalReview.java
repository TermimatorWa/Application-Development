package com.example.app.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.example.app.data.HerbivoreWorkbook;
import com.example.app.data.HerbivoreWorkbook.ObservationRow;
import com.example.app.data.HerbivoreWorkbook.ObservationType;
import com.example.app.data.HerbivoreWorkbook.SourceCell;
import com.example.app.data.HerbivoreWorkbook.SourceNote;
import com.example.app.data.HerbivoreWorkbookReader;
import com.example.app.data.HistoricalDataValidator;
import com.example.app.data.HistoricalDataValidator.Severity;
import com.example.app.data.HistoricalDataValidator.ValidationReport;

/** Workbook and default validation report for one selected Excel source. */
record HistoricalReview(HerbivoreWorkbook workbook, ValidationReport report) {
    static HistoricalReview load(Path path) throws IOException {
        HerbivoreWorkbook workbook = HerbivoreWorkbookReader.read(path);
        return new HistoricalReview(workbook, HistoricalDataValidator.validate(workbook));
    }

    List<ObservationRow> population() {
        return workbook.blocks().stream()
                .filter(block -> block.type() == ObservationType.POPULATION)
                .flatMap(block -> block.rows().stream()).toList();
    }

    String summary() {
        var population = report.summaries().get(ObservationType.POPULATION);
        var severity = report.countsBySeverity();
        return "Population %d (%d complete, %d incomplete) · Mortality %d · Births %d%n"
                .concat("Diagnostics %d · %d informational · %d warnings · %d errors")
                .formatted(population.checked(), population.complete(), population.incomplete(),
                        report.summaries().get(ObservationType.MORTALITY).checked(),
                        report.summaries().get(ObservationType.BIRTHS).checked(),
                        report.diagnostics().size(), severity.get(Severity.INFO), severity.get(Severity.WARNING),
                        severity.get(Severity.ERROR));
    }

    String reviewStatus() {
        if (report.summaries().get(ObservationType.POPULATION).complete() == 0) {
            return "No complete usable population records. Inspect the diagnostics and check the source data.";
        }
        if (report.countsBySeverity().get(Severity.ERROR) > 0) {
            return "Loaded for inspection. Review the errors before using the data in scientific calculations.";
        }
        if (report.countsBySeverity().get(Severity.WARNING) > 0) {
            return "Ready for scientific review with warnings.";
        }
        return "Ready for scientific review.";
    }

    static String cellText(SourceCell cell) {
        if (cell == null) {
            return "Missing";
        }
        boolean formula = cell.originalValue().type() == HerbivoreWorkbook.CellType.FORMULA;
        var value = formula ? cell.cachedResult() : cell.originalValue();
        if (value == null) {
            return "Formula result unavailable";
        }
        return switch (value.type()) {
            case ABSENT -> "Missing";
            case BLANK -> "Blank";
            case ERROR -> value.rawText() == null ? "Spreadsheet error" : value.rawText();
            default -> value.rawText() != null ? value.rawText()
                    : value.numericValue() != null ? value.numericValue().toString() : "Unavailable";
        };
    }

    static String location(String worksheet, String address) {
        if (worksheet == null) {
            return "";
        }
        return address == null ? worksheet : worksheet + "!" + address;
    }

    static String dates(SourceNote note) {
        var dates = note.dates();
        if (dates == null) {
            return "";
        }
        return dates.end() == null ? dates.start() : dates.start() + " – " + dates.end();
    }
}
