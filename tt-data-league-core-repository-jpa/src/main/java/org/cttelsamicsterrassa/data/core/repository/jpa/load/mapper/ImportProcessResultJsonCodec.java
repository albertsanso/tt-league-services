package org.cttelsamicsterrassa.data.core.repository.jpa.load.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewFinding;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewProcessingError;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converts an {@link ImportProcessResult} to and from the JSON stored in {@code import_job_season.result_json}.
 * The stored shape is defined by the private records below, not by the domain records, so a domain refactoring
 * does not silently change what is persisted.
 */
@Component
public class ImportProcessResultJsonCodec {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String toJson(ImportProcessResult result) {
        try {
            return objectMapper.writeValueAsString(ResultJson.from(result));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize an import process result", exception);
        }
    }

    /**
     * @throws IllegalStateException when the stored value cannot be read
     */
    public ImportProcessResult fromJson(String json) {
        try {
            return objectMapper.readValue(json, ResultJson.class).toDomain();
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to read a stored import process result", exception);
        }
    }

    private record ResultJson(String status, List<FindingJson> findings, List<ErrorJson> processingErrors,
                              long filesSeen, long itemsPersisted, long skipped, long processorFailures,
                              long elapsedMillis, long persistenceWrites, List<String> executionIssues,
                              List<String> postProcessingOutcomes, LifecycleJson lifecycle,
                              List<RoundJson> roundProgress) {

        static ResultJson from(ImportProcessResult result) {
            ImportLifecycleCounters lifecycle = result.lifecycle();
            return new ResultJson(result.status().name(),
                    result.findings().stream().map(f -> new FindingJson(f.severity(), f.message(), f.location()))
                            .toList(),
                    result.processingErrors().stream().map(e -> new ErrorJson(e.message(), e.location())).toList(),
                    result.filesSeen(), result.itemsPersisted(), result.skipped(), result.processorFailures(),
                    result.elapsedMillis(), result.persistenceWrites(), result.executionIssues(),
                    result.postProcessingOutcomes(),
                    new LifecycleJson(lifecycle.scheduledCreated(), lifecycle.upgradedToPlayed(),
                            lifecycle.rescheduled(), lifecycle.partialActas(), lifecycle.invalidActas(),
                            lifecycle.unresolvedPendingFixtures(), lifecycle.amendedPlayed()),
                    result.roundProgress().stream().map(RoundJson::from).toList());
        }

        ImportProcessResult toDomain() {
            return new ImportProcessResult(ImportProcessStatus.valueOf(status),
                    findings.stream().map(f -> new ImportPreviewFinding(f.severity(), f.message(), f.location()))
                            .toList(),
                    processingErrors.stream().map(e -> new ImportPreviewProcessingError(e.message(), e.location()))
                            .toList(),
                    filesSeen, itemsPersisted, skipped, processorFailures, elapsedMillis, persistenceWrites,
                    executionIssues, postProcessingOutcomes,
                    new ImportLifecycleCounters(lifecycle.scheduledCreated(), lifecycle.upgradedToPlayed(),
                            lifecycle.rescheduled(), lifecycle.partialActas(), lifecycle.invalidActas(),
                            lifecycle.unresolvedPendingFixtures(), lifecycle.amendedPlayed()),
                    roundProgress.stream().map(RoundJson::toDomain).toList());
        }
    }

    private record FindingJson(String severity, String message, String location) {
    }

    private record ErrorJson(String message, String location) {
    }

    private record LifecycleJson(long scheduledCreated, long upgradedToPlayed, long rescheduled, long partialActas,
                                 long invalidActas, long unresolvedPendingFixtures, long amendedPlayed) {
    }

    private record RoundJson(String source, String season, String competition, Integer groupNumber, String phase,
                             Integer currentRound, Integer lastCompleteRound, long scheduledMatches,
                             long playedMatches) {

        static RoundJson from(RoundProgress round) {
            return new RoundJson(round.source().name(), round.season().toString(), round.competition(),
                    round.groupNumber(), round.phase(), round.currentRound(), round.lastCompleteRound(),
                    round.scheduledMatches(), round.playedMatches());
        }

        RoundProgress toDomain() {
            return new RoundProgress(ImportSource.valueOf(source), Season.fromFormatted(season), competition,
                    groupNumber, phase, currentRound, lastCompleteRound, scheduledMatches, playedMatches);
        }
    }
}
