package org.cttelsamicsterrassa.data.core.domain.load.service;

import org.cttelsamicsterrassa.data.core.domain.resource.model.UploadMode;

import java.util.List;
import java.util.Objects;

/**
 * Raised when an upload holds fewer published actas than the season folder already stored and no
 * explicit override was given. It extends {@link IllegalArgumentException} so every existing handler
 * keeps its behavior; the REST layer maps it to {@code 409 Conflict}. The message depends on the
 * {@link UploadMode}: a snapshot replaces the stored season, a delta merges into it.
 */
public class SnapshotShrinkException extends IllegalArgumentException {

    private final UploadMode mode;
    private final List<SeasonShrink> seasonShrinks;

    public SnapshotShrinkException(UploadMode mode, List<SeasonShrink> seasonShrinks) {
        super(buildMessage(mode, seasonShrinks));
        this.mode = Objects.requireNonNull(mode, "mode");
        this.seasonShrinks = List.copyOf(seasonShrinks);
    }

    public UploadMode getMode() {
        return mode;
    }

    public List<SeasonShrink> getSeasonShrinks() {
        return seasonShrinks;
    }

    private static String buildMessage(UploadMode mode, List<SeasonShrink> seasonShrinks) {
        StringBuilder message = new StringBuilder("Upload rejected:");
        for (SeasonShrink shrink : seasonShrinks) {
            message.append(System.lineSeparator())
                    .append("- ").append(shrink.source()).append(' ').append(shrink.season())
                    .append(" ACTAS ");
            if (mode == UploadMode.DELTA) {
                message.append("would have ").append(shrink.incoming())
                        .append(" published actas after merging, fewer than the ")
                        .append(shrink.stored()).append(" already stored (the delta overwrites published")
                        .append(" actas with unpublished or invalid copies). Remove those files from the ZIP, or")
                        .append(" retry with allowPublishedShrink=true to merge anyway.");
            } else {
                message.append("has ").append(shrink.incoming()).append(" published actas, fewer than the ")
                        .append(shrink.stored()).append(" already stored. Upload a complete season snapshot, or retry")
                        .append(" with allowPublishedShrink=true to replace the stored season anyway.");
            }
        }
        return message.toString();
    }

    public record SeasonShrink(String source, String season, int stored, int incoming) {
    }
}