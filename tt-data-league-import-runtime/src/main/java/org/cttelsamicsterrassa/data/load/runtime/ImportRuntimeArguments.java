package org.cttelsamicsterrassa.data.load.runtime;

import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationMode;

import java.util.Locale;
import java.util.Optional;

/**
 * Command-line arguments for a single import run.
 */
public record ImportRuntimeArguments(
        String source,
        String actasFolder,
        String season,
        boolean consolidateClubs,
        ConsolidationMode consolidationMode,
        boolean consolidatePlayers,
        ConsolidationMode playerConsolidationMode
) {
    private static final String SOURCE_ARGUMENT = "--source=";
    private static final String ACTAS_FOLDER_ARGUMENT = "--actas-folder=";
    private static final String SEASON_ARGUMENT = "--season=";
    private static final String CONSOLIDATE_CLUBS_FLAG = "--consolidate-clubs";
    private static final String CONSOLIDATE_CLUBS_ARGUMENT = "--consolidate-clubs=";
    private static final String CONSOLIDATE_PLAYERS_FLAG = "--consolidate-players";
    private static final String CONSOLIDATE_PLAYERS_ARGUMENT = "--consolidate-players=";

    public ImportRuntimeArguments(String source, String actasFolder, String season,
                                  boolean consolidateClubs, ConsolidationMode consolidationMode) {
        this(source, actasFolder, season, consolidateClubs, consolidationMode, false, ConsolidationMode.WRITE);
    }

    public static ImportRuntimeArguments parse(String... args) {
        String source = valueOf(args, SOURCE_ARGUMENT);
        source = source == null ? "rfetm" : source.toLowerCase(Locale.ROOT);
        boolean consolidate = false;
        ConsolidationMode mode = ConsolidationMode.WRITE;
        boolean consolidatePlayers = false;
        ConsolidationMode playerMode = ConsolidationMode.WRITE;
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
            } else if (CONSOLIDATE_PLAYERS_FLAG.equals(arg)) {
                consolidatePlayers = true;
            } else if (arg.startsWith(CONSOLIDATE_PLAYERS_ARGUMENT)) {
                consolidatePlayers = true;
                String value = arg.substring(CONSOLIDATE_PLAYERS_ARGUMENT.length()).trim().toLowerCase(Locale.ROOT);
                if ("report".equals(value)) {
                    playerMode = ConsolidationMode.REPORT;
                } else if (!value.isEmpty() && !"true".equals(value) && !"write".equals(value)) {
                    throw new IllegalArgumentException("Unknown " + CONSOLIDATE_PLAYERS_ARGUMENT + value
                            + "; expected report or write");
                }
            }
        }
        return new ImportRuntimeArguments(
                source,
                valueOf(args, ACTAS_FOLDER_ARGUMENT),
                valueOf(args, SEASON_ARGUMENT),
                consolidate,
                mode,
                consolidatePlayers,
                playerMode);
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
