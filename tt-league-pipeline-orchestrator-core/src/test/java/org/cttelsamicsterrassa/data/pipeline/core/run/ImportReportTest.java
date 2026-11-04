package org.cttelsamicsterrassa.data.pipeline.core.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportReportTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");

    private static ImportReport report(String status, long filesSeen, List<String> issues, String raw) {
        return new ImportReport(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), status, filesSeen, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
                issues, raw, NOW);
    }

    @Test
    void copiesIssues() {
        List<String> issues = new ArrayList<>(List.of("a"));
        ImportReport report = report("PARTIAL", 1, issues, "{}");
        issues.add("b");
        assertThat(report.issues()).containsExactly("a");
    }

    @Test
    void rejectsNegativeCounterBlankRawAndUnknownStatus() {
        assertThatThrownBy(() -> report("PARTIAL", -1, List.of(), "{}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> report("PARTIAL", 1, List.of(), " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> report("RUNNING", 1, List.of(), "{}")).isInstanceOf(IllegalArgumentException.class);
    }
}
