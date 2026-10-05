package org.cttelsamicsterrassa.data.core.repository.jpa.load.mapper;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImportProcessResultJsonCodecTest {
    private final ImportProcessResultJsonCodec codec = new ImportProcessResultJsonCodec();

    @Test
    void amendedPlayedRoundTrips() {
        ImportProcessResult result = new ImportProcessResult(ImportProcessStatus.SUCCESS, List.of(), List.of(),
                1, 1, 0, 0, 10, 1, List.of(), List.of(), new ImportLifecycleCounters(1, 2, 3, 4, 5, 6, 7),
                List.of());

        assertEquals(7, codec.fromJson(codec.toJson(result)).lifecycle().amendedPlayed());
    }

    @Test
    void storedResultsWrittenBeforeAmendedPlayedDecodeToZero() {
        String legacy = "{\"status\":\"SUCCESS\",\"findings\":[],\"processingErrors\":[],\"filesSeen\":4,"
                + "\"itemsPersisted\":3,\"skipped\":1,\"processorFailures\":0,\"elapsedMillis\":100,"
                + "\"persistenceWrites\":3,\"executionIssues\":[],\"postProcessingOutcomes\":[],"
                + "\"lifecycle\":{\"scheduledCreated\":2,\"upgradedToPlayed\":1,\"rescheduled\":0,"
                + "\"partialActas\":0,\"invalidActas\":0,\"unresolvedPendingFixtures\":0},\"roundProgress\":[]}";

        ImportLifecycleCounters lifecycle = codec.fromJson(legacy).lifecycle();

        assertEquals(new ImportLifecycleCounters(2, 1, 0, 0, 0, 0, 0), lifecycle);
    }
}
