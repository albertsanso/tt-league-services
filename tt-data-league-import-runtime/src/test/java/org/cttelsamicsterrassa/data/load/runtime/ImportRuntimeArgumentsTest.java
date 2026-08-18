package org.cttelsamicsterrassa.data.load.runtime;

import org.cttelsamicsterrassa.data.load.shared.club.ConsolidationMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportRuntimeArgumentsTest {

    @Test
    void doesNotRequestConsolidationByDefault() {
        ImportRuntimeArguments arguments = ImportRuntimeArguments.parse(
                "--source=fctt", "--base-folder=C:\\data", "--season=2023-2024");

        assertEquals("fctt", arguments.source());
        assertEquals("C:\\data", arguments.baseFolder());
        assertEquals("2023-2024", arguments.optionalSeason().orElseThrow());
        assertFalse(arguments.consolidateClubs());
        assertEquals(ConsolidationMode.WRITE, arguments.consolidationMode());
    }

    @Test
    void enablesWriteConsolidationOnlyWhenTheFlagIsPresent() {
        ImportRuntimeArguments arguments = ImportRuntimeArguments.parse(
                "--source=bcnesa", "--base-folder=C:\\data", "--consolidate-clubs");

        assertTrue(arguments.consolidateClubs());
        assertEquals(ConsolidationMode.WRITE, arguments.consolidationMode());
    }

    @Test
    void enablesADryRunReportWithoutWrites() {
        ImportRuntimeArguments arguments = ImportRuntimeArguments.parse(
                "--base-folder=C:\\data", "--consolidate-clubs=report");

        assertTrue(arguments.consolidateClubs());
        assertEquals(ConsolidationMode.REPORT, arguments.consolidationMode());
        assertEquals("rfetm", arguments.source());
    }
}
