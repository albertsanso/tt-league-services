package org.cttelsamicsterrassa.data.core.domain.load.service;

import java.util.List;

/**
 * Raised when a snapshot upload holds fewer published actas than the season folder already stored
 * and no explicit override was given. It extends {@link IllegalArgumentException} so every existing
 * handler keeps its behavior; the REST layer maps it to {@code 409 Conflict}.
 */
public class SnapshotShrinkException extends IllegalArgumentException {

    private final List<SeasonShrink> seasonShrinks;

    public SnapshotShrinkException(List<SeasonShrink> seasonShrinks) {
        super(buildMessage(seasonShrinks));
        this.seasonShrinks = List.copyOf(seasonShrinks);
    }

    public List<SeasonShrink> getSeasonShrinks() {
        return seasonShrinks;
    }

    private static String buildMessage(List<SeasonShrink> seasonShrinks) {
        StringBuilder message = new StringBuilder("Upload rejected:");
        for (SeasonShrink shrink : seasonShrinks) {
            message.append(System.lineSeparator())
                    .append("- ").append(shrink.source()).append(' ').append(shrink.season())
                    .append(" ACTAS has ").append(shrink.incoming()).append(" published actas, fewer than the ")
                    .append(shrink.stored()).append(" already stored. Upload a complete season snapshot, or retry")
                    .append(" with allowPublishedShrink=true to replace the stored season anyway.");
        }
        return message.toString();
    }

    public record SeasonShrink(String source, String season, int stored, int incoming) {
    }
}
