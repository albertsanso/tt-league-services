package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import java.time.Instant;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** The amended acta count of one import report. */
public record CorrectionFacts(UUID runId, PipelineSource source, Instant receivedAt, long amendedPlayed) {}
