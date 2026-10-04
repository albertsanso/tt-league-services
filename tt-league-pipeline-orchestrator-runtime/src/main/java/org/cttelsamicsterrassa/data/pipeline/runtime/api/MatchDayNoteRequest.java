package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** A note on a match day, or on one of its matches when {@code matchId} is set. */
public record MatchDayNoteRequest(@NotBlank @Size(max = 2000) String text, UUID matchId) {
}
