package org.cttelsamicsterrassa.data.pipeline.core.execution;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportCounters;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportJobState;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportSeasonState;
import org.cttelsamicsterrassa.data.pipeline.core.run.ImportReport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Derives the stored report of a unit from its finished platform import job. */
public final class ImportReportMapper {

    private ImportReportMapper() {
    }

    public static ImportReport toReport(UUID runId, UUID unitId, ImportJobState job, Instant receivedAt) {
        long[] sum = new long[11];
        List<String> issues = new ArrayList<>();
        addIfPresent(issues, job.errorDetail(), null);
        for (ImportSeasonState season : job.seasons()) {
            addIfPresent(issues, season.errorDetail(), season.season());
        }
        for (ImportSeasonState season : job.seasons()) {
            for (String issue : season.executionIssues()) {
                addIfPresent(issues, issue, season.season());
            }
            ImportCounters c = season.counters();
            if (c == null) {
                continue;
            }
            sum[0] += c.filesSeen();
            sum[1] += c.itemsPersisted();
            sum[2] += c.skipped();
            sum[3] += c.processorFailures();
            sum[4] += c.scheduledCreated();
            sum[5] += c.upgradedToPlayed();
            sum[6] += c.rescheduled();
            sum[7] += c.partialActas();
            sum[8] += c.invalidActas();
            sum[9] += c.unresolvedPendingFixtures();
            sum[10] += c.amendedPlayed();
        }
        return new ImportReport(runId, unitId, job.importJobId(), job.status(), sum[0], sum[1], sum[2], sum[3], sum[4],
                sum[5], sum[6], sum[7], sum[8], sum[9], sum[10], issues, job.rawJson(), receivedAt);
    }

    private static void addIfPresent(List<String> issues, String text, String season) {
        if (text == null || text.isBlank()) {
            return;
        }
        issues.add(season == null ? text : season + ": " + text);
    }
}
