package com.example.app.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObservedCountsTest {

    @TempDir
    Path dir;

    @Test
    void readsColumnsByHeaderName() throws IOException {
        Path file = dir.resolve("counts.csv");
        Files.writeString(file, "Year;Heck cattle;Konik horses;Red deer;Total\n"
                + "1983;30;;;30\n"
                + "1992;170;85;45;300\n");
        ObservedCounts counts = ObservedCounts.read(file);
        assertEquals(2, counts.rows().size());
        assertFalse(counts.rows().get(0).complete());
        ObservedCounts.Row row = counts.rows().get(1);
        assertTrue(row.complete());
        assertEquals(45, row.deer());
        assertEquals(170, row.cattle());
        assertEquals(85, row.horses());
    }

    @Test
    void rejectsFileWithoutHeader() throws IOException {
        Path file = dir.resolve("bad.csv");
        Files.writeString(file, "1,2,3\n");
        assertThrows(IOException.class, () -> ObservedCounts.read(file));
    }
}
