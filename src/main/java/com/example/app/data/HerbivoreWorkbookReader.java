package com.example.app.data;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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

/** Reads the three independent tables on the historical herbivore worksheet. */
public final class HerbivoreWorkbookReader {
    public static final Path DEFAULT_PATH = Path.of("data", "raw", "herbivores.xlsx");
    private static final String SHEET = "2025";
    private static final Pattern NOTE_MARKER = Pattern.compile("\\d+\\)");
    private static final String DATE = "(?:(?:January|February|March|April|May|June|July|August|"
            + "September|October|November|December)\\s+)?\\d{4}";
    private static final Pattern DATE_RANGE = Pattern.compile(
            "\\b(" + DATE + ")\\s*(?:[-–]|and\\b|to\\b)\\s*(" + DATE + ")\\b",
            Pattern.CASE_INSENSITIVE);

    private HerbivoreWorkbookReader() {
    }

    public static HerbivoreWorkbook read() throws IOException {
        return read(DEFAULT_PATH);
    }

    public static HerbivoreWorkbook read(Path path) throws IOException {
        if (Files.notExists(path)) {
            throw new IOException("Herbivore workbook does not exist: " + path);
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IOException("Herbivore workbook is not a readable file: " + path);
        }
        try (OPCPackage source = OPCPackage.open(path.toFile(), PackageAccess.READ);
             XSSFWorkbook workbook = new XSSFWorkbook(source)) {
            XSSFSheet sheet = workbook.getSheet(SHEET);
            if (sheet == null) {
                throw new IOException("Missing worksheet '" + SHEET + "'");
            }
            Set<String> tableCells = new HashSet<>();
            List<ObservationBlock> blocks = new ArrayList<>();
            for (Table table : findTables(sheet, tableCells)) {
                blocks.add(readBlock(sheet, table, tableCells));
            }
            return new HerbivoreWorkbook(blocks, readNotes(sheet, tableCells));
        } catch (Exception e) {
            throw new IOException("Cannot read herbivore .xlsx workbook " + path + ": " + e.getMessage(), e);
        }
    }

    private record Table(ObservationType type, int headerRow, int yearColumn, int totalColumn,
                         Map<Species, Integer> speciesColumns) {
    }

    private static List<Table> findTables(XSSFSheet sheet, Set<String> tableCells) throws IOException {
        List<Table> tables = new ArrayList<>();
        Set<ObservationType> found = new HashSet<>();
        for (Row row : sheet) {
            for (var cell : row) {
                ObservationType type = switch (label((XSSFCell) cell)) {
                    case "total number of animals on january 1st" -> ObservationType.POPULATION;
                    case "total births" -> ObservationType.BIRTHS;
                    case "total mortality" -> ObservationType.MORTALITY;
                    default -> null;
                };
                if (type == null) {
                    continue;
                }
                if (!found.add(type)) {
                    throw layoutError("Ambiguous " + type + " section at " + cell.getAddress());
                }
                tableCells.add(cell.getAddress().formatAsString());
                Table table = findHeaders(sheet, (XSSFCell) cell, type, tableCells);
                if (tables.stream().anyMatch(other -> other.headerRow() == table.headerRow()
                        && other.yearColumn() == table.yearColumn())) {
                    throw layoutError("Multiple sections claim the same headers near " + cell.getAddress());
                }
                tables.add(table);
            }
        }
        for (ObservationType type : ObservationType.values()) {
            if (!found.contains(type)) {
                throw layoutError("Missing required " + type + " section heading");
            }
        }
        return tables;
    }

    private static Table findHeaders(XSSFSheet sheet, XSSFCell heading, ObservationType type,
                                     Set<String> tableCells) throws IOException {
        int anchor = heading.getColumnIndex();
        // A section heading sits above its own contiguous run of column labels.
        for (int index = heading.getRowIndex() + 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null || label((XSSFCell) row.getCell(anchor)).isEmpty()) {
                continue;
            }
            int first = anchor;
            while (first > 0 && !label((XSSFCell) row.getCell(first - 1)).isEmpty()) {
                first--;
            }
            Set<String> labels = new HashSet<>();
            Map<Species, Integer> species = new LinkedHashMap<>();
            int year = -1;
            int total = -1;
            for (int column = first; column < row.getLastCellNum(); column++) {
                XSSFCell cell = (XSSFCell) row.getCell(column);
                String label = label(cell);
                if (label.isEmpty()) {
                    break;
                }
                if (!labels.add(label)) {
                    throw layoutError("Ambiguous " + type + " header at " + cell.getAddress());
                }
                tableCells.add(cell.getAddress().formatAsString());
                if (label.equals("year")) {
                    year = column;
                } else if (label.equals("total")) {
                    total = column;
                }
                for (Species candidate : Species.values()) {
                    if (label.equals(candidate.displayName().toLowerCase(Locale.ROOT))) {
                        species.put(candidate, column);
                    }
                }
            }
            if (year >= 0 && total >= 0 && species.size() == Species.COUNT) {
                return new Table(type, index, year, total, species);
            }
            break;
        }
        throw layoutError("Missing required headers below " + type + " heading at " + heading.getAddress()
                + " (expected Year, Heck cattle, Konik horses, Red deer and Total)");
    }

    private static ObservationBlock readBlock(XSSFSheet sheet, Table table, Set<String> tableCells)
            throws IOException {
        List<ObservationRow> rows = new ArrayList<>();
        int lastYearRow = table.headerRow();
        for (Row row : sheet) {
            if (row.getRowNum() > table.headerRow() && hasContent((XSSFCell) row.getCell(table.yearColumn()))) {
                lastYearRow = row.getRowNum();
            }
        }
        for (Row row : sheet) {
            if (row.getRowNum() <= table.headerRow()) {
                continue;
            }
            XSSFCell yearCell = (XSSFCell) row.getCell(table.yearColumn());
            boolean hasYear = hasContent(yearCell);
            boolean withinYearRows = row.getRowNum() <= lastYearRow;
            boolean hasData = table.speciesColumns().values().stream()
                    .map(column -> (XSSFCell) row.getCell(column)).anyMatch(cell -> isTableData(cell, withinYearRows))
                    || isTableData((XSSFCell) row.getCell(table.totalColumn()), withinYearRows);
            if (!hasYear && !hasData) {
                continue;
            }
            String worksheet = sheet.getSheetName();
            SourceCell year = readCell(worksheet, row, table.yearColumn(), tableCells);
            Map<Species, SourceCell> species = new LinkedHashMap<>();
            for (var entry : table.speciesColumns().entrySet()) {
                species.put(entry.getKey(), readCell(worksheet, row, entry.getValue(), tableCells));
            }
            SourceCell total = readCell(worksheet, row, table.totalColumn(), tableCells);
            rows.add(new ObservationRow(worksheet, row.getRowNum() + 1, year, interpretYear(year), species, total));
        }
        return new ObservationBlock(table.type(), rows);
    }

    private static boolean hasContent(XSSFCell cell) {
        return cell != null && cell.getCellType() != org.apache.poi.ss.usermodel.CellType.BLANK;
    }

    private static boolean isTableData(XSSFCell cell, boolean withinYearRows) {
        // The source uses textual footnote markers and note prose below the tables.
        return hasContent(cell) && (withinYearRows || cell.getCellType() != org.apache.poi.ss.usermodel.CellType.STRING);
    }

    private static OptionalInt interpretYear(SourceCell source) {
        CellValue value = source.cachedResult() != null ? source.cachedResult() : source.originalValue();
        if (value.numericValue() == null) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(value.numericValue().intValueExact());
        } catch (ArithmeticException e) {
            return OptionalInt.empty();
        }
    }

    private static SourceCell readCell(String worksheet, Row row, int column, Set<String> tableCells)
            throws IOException {
        String address = new CellReference(row.getRowNum(), column).formatAsString();
        tableCells.add(address);
        XSSFCell cell = (XSSFCell) row.getCell(column, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK);
        if (cell == null) {
            return new SourceCell(worksheet, address, new CellValue(CellType.ABSENT, null, null, null),
                    null, null, null);
        }
        try {
            if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA) {
                CellValue cached = cell.getRawValue() == null ? null
                        : readValue(cell, cell.getCachedFormulaResultType());
                return new SourceCell(worksheet, address, new CellValue(CellType.FORMULA, null, null, null),
                        cell.getCellFormula(), cached, readStyle(cell));
            }
            return new SourceCell(worksheet, address, readValue(cell, cell.getCellType()),
                    null, null, readStyle(cell));
        } catch (RuntimeException e) {
            throw new IOException("Unsupported cell representation at " + worksheet + "!" + address, e);
        }
    }

    private static CellValue readValue(XSSFCell cell, org.apache.poi.ss.usermodel.CellType type) {
        String raw = cell.getRawValue();
        return switch (type) {
            case BLANK -> new CellValue(CellType.BLANK, raw, null, null);
            case NUMERIC -> new CellValue(CellType.NUMERIC, raw, decimal(raw), null);
            case STRING -> new CellValue(CellType.TEXT, cell.getStringCellValue(), null, null);
            case BOOLEAN -> new CellValue(CellType.BOOLEAN, raw, null, null);
            case ERROR -> new CellValue(CellType.ERROR, raw, null, Byte.toUnsignedInt(cell.getErrorCellValue()));
            default -> throw new IllegalArgumentException("Unsupported cell type " + type);
        };
    }

    private static BigDecimal decimal(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static CellStyle readStyle(XSSFCell cell) {
        var style = cell.getCellStyle();
        var font = style.getFont();
        var colours = font.getCTFont().getColorList();
        if (colours.isEmpty()) {
            return new CellStyle(style.getIndex(), null);
        }
        // Read the stored colour rather than resolving it through the workbook theme.
        var colour = colours.getFirst();
        return new CellStyle(style.getIndex(), new FontColour(
                colour.isSetRgb() ? java.util.HexFormat.of().withUpperCase().formatHex(colour.getRgb()) : null,
                colour.isSetIndexed() ? (int) colour.getIndexed() : null,
                colour.isSetTheme() ? (int) colour.getTheme() : null,
                colour.isSetTint() ? BigDecimal.valueOf(colour.getTint()) : null,
                colour.isSetAuto() ? colour.getAuto() : null));
    }

    private static List<SourceNote> readNotes(XSSFSheet sheet, Set<String> tableCells) {
        List<SourceNote> notes = new ArrayList<>();
        for (Row row : sheet) {
            for (var cell : row) {
                String address = cell.getAddress().formatAsString();
                if (cell.getCellType() != org.apache.poi.ss.usermodel.CellType.STRING || tableCells.contains(address)) {
                    continue;
                }
                String text = cell.getStringCellValue();
                if (text.isBlank() || NOTE_MARKER.matcher(text.strip()).matches()) {
                    continue;
                }
                var range = DATE_RANGE.matcher(text);
                ReportedDates dates = range.find() ? new ReportedDates(range.group(1), range.group(2)) : null;
                notes.add(new SourceNote(sheet.getSheetName(), address, text, dates));
            }
        }
        return notes;
    }

    private static String label(XSSFCell cell) {
        return cell != null && cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
                ? cell.getStringCellValue().strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ") : "";
    }

    private static IOException layoutError(String message) {
        return new IOException("Worksheet '" + SHEET + "': " + message);
    }
}
