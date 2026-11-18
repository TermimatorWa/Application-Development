package com.example.app.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.example.app.model.PopulationState;

/**
 * Annual herbivore counts on 1 January, read from a CSV export of the teaching dataset
 * "Number of large herbivores in the Oostvaardersplassen 1983-2025".
 * <p>
 * Expected columns (any order, matched by header name): Year, Heck cattle, Konik horses, Red deer.
 * Commas or semicolons are accepted as separators; empty cells mean "not counted".
 * The dataset is for educational use only, so it is not stored in the repository.
 */
public record ObservedCounts(List<Row> rows) {

    /** One year of counts. A missing count is stored as NaN. */
    public record Row(int year, double deer, double cattle, double horses) {

        public boolean complete() {
            return !Double.isNaN(deer) && !Double.isNaN(cattle) && !Double.isNaN(horses);
        }

        public PopulationState toState() {
            return new PopulationState(deer, cattle, horses, 0);
        }
    }

    public ObservedCounts {
        rows = List.copyOf(rows);
    }

    public static ObservedCounts read(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        int yearCol = -1;
        int deerCol = -1;
        int cattleCol = -1;
        int horsesCol = -1;
        List<Row> rows = new ArrayList<>();
        for (String line : lines) {
            String[] cells = line.replace("﻿", "").split("[;,]", -1);
            if (yearCol < 0) {
                for (int i = 0; i < cells.length; i++) {
                    String header = cells[i].trim().toLowerCase(Locale.ROOT);
                    if (header.equals("year")) {
                        yearCol = i;
                    } else if (header.contains("deer")) {
                        deerCol = i;
                    } else if (header.contains("cattle")) {
                        cattleCol = i;
                    } else if (header.contains("horse")) {
                        horsesCol = i;
                    }
                }
                if (yearCol >= 0 && (deerCol < 0 || cattleCol < 0 || horsesCol < 0)) {
                    throw new IOException("Header needs Year, Red deer, Heck cattle and Konik horses columns");
                }
                continue;
            }
            Integer year = parseYear(cell(cells, yearCol));
            if (year == null) {
                continue;
            }
            rows.add(new Row(year, number(cell(cells, deerCol)), number(cell(cells, cattleCol)),
                    number(cell(cells, horsesCol))));
        }
        if (yearCol < 0) {
            throw new IOException("No header row with a 'Year' column was found");
        }
        if (rows.isEmpty()) {
            throw new IOException("No yearly counts were found");
        }
        return new ObservedCounts(rows);
    }

    private static String cell(String[] cells, int index) {
        return index < cells.length ? cells[index].trim().replace("\"", "") : "";
    }

    private static Integer parseYear(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double number(String text) {
        if (text.isEmpty()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
