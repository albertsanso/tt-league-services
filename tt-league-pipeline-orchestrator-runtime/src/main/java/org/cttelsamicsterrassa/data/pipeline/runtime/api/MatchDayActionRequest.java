package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import jakarta.validation.constraints.Size;

/** Optional note of a close, reopen, ignore or unignore action; a blank note is treated as absent. */
public record MatchDayActionRequest(@Size(max = 2000) String note) {

    String normalizedNote() {
        return note == null || note.isBlank() ? null : note;
    }
}
