package org.cttelsamicsterrassa.data.load.runtime;

import org.cttelsamicsterrassa.data.load.shared.club.ConsolidationMode;

import java.util.Locale;
import java.util.Optional;

/**
 * Command-line arguments for a single import run.
 */
public record ImportRuntimeArguments(
        String source,
        String baseFolder,
        String season,
        boolean consolidateClubs,
        ConsolidationMode consolidationMode
) {
    private static final String SOURCE_ARGUMENT = "--source=";
    private static final String BASE_FOLDER_ARGUMENT = "--base-folder=";
    private static final String SEASON_ARGUMENT = "--season=";
    private static final String CONSOLIDATE_CLUBS_FLAG = "--consolidate-clubs";
    private static final String CONSOLIDATE_CLUBS_ARGUMENT = "--consolidate-clubs=";

    public static ImportRuntimeArguments parse(String... args) {
        String source = valueOf(args, SOURCE_ARGUMENT);
        source = source == null ? "rfetm" : source.toLowerCase(Locale.ROOT);
        boolean consolidate = false;
        ConsolidationMode mode = ConsolidationMode.WRITE;
        for (String arg : args) {
            if (CONSOLIDATE_CLUBS_FLAG.equals(arg)) {
                consolidate = true;
            } else if (arg.startsWith(CONSOLIDATE_CLUBS_ARGUMENT)) {
                consolidate = true;
                String value = arg.substring(CONSOLIDATE_CLUBS_ARGUMENT.length()).trim().toLowerCase(Locale.ROOT);
                if ("report".equals(value)) {
                    mode = ConsolidationMode.REPORT;
                } else if (!value.isEmpty() && !"true".equals(value) && !"write".equals(value)) {
                    throw new IllegalArgumentException("Unknown " + CONSOLIDATE_CLUBS_ARGUMENT + value
                            + "; expected report or write");
                }
            }
        }
        return new ImportRuntimeArguments(
                source,
                valueOf(args, BASE_FOLDER_ARGUMENT),
                valueOf(args, SEASON_ARGUMENT),
                consolidate,
                mode);
    }

    public Optional<String> optionalSeason() {
        return Optional.ofNullable(season);
    }

    private static String valueOf(String[] args, String prefix) {
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                String value = arg.substring(prefix.length()).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }
}
