package org.cttelsamicsterrassa.data.pipeline.runtime.api;

/** Body of the match-day refresh; {@code force} defaults to false. */
public record MatchDayRefreshRequest(boolean force) {
}
