package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import java.time.Instant;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;

/** A terminal run, as the statistics read it. */
public record RunFacts(UUID runId, PipelineSource source, RunStatus status, Instant startedAt, Instant finishedAt) {}
