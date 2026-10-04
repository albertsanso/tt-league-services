package org.cttelsamicsterrassa.data.pipeline.runtime.api;

/** Latest attempt of a step kind, shown in run lists. */
public record StepStatusDto(String kind, String status, int attempt) {
}
