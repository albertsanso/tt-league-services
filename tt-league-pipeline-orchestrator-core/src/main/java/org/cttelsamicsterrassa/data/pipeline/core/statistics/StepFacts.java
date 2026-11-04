package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import java.time.Instant;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.IngestHealth;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepStatus;

/**
 * A finished step attempt, as the statistics read it; {@code unitKey} is the key of the unit it ran for and
 * {@code health} is null when unknown or not INGEST.
 */
public record StepFacts(
        UUID runId,
        String unitKey,
        PipelineSource source,
        StepKind kind,
        StepStatus status,
        String outcome,
        Instant startedAt,
        Instant finishedAt,
        IngestHealth health) {}
