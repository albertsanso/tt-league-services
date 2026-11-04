package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import java.time.Instant;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus;

/** A terminal run unit, as the statistics read it; {@code startedAt} is null for a skipped unit. */
public record UnitFacts(
        UUID runId,
        PipelineSource source,
        String unitKey,
        String label,
        UnitStatus status,
        Instant startedAt,
        Instant finishedAt) {}
