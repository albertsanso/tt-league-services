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
        String rfetmTeamsFolder,
        String season,
        boolean consolidateClubs,
        ConsolidationMode consolidationMode,
        boolean consolidatePlayers,
        ConsolidationMode playerConsolidationMode,
        boolean consolidateRfetmClubs,
        ConsolidationMode rfetmClubConsolidationMode
) {
    public ImportRuntimeArguments(String source, String actasFolder, String rfetmTeamsFolder, String season,
                                  boolean consolidateClubs, ConsolidationMode consolidationMode) {
        this(source, actasFolder, rfetmTeamsFolder, season,
                consolidateClubs, consolidationMode,
                false, ConsolidationMode.WRITE,
                false, ConsolidationMode.WRITE);
    }

    public static ImportRuntimeArguments parse(String... args) {
        String source = valueOf(args, ImportRuntimeCliContract.SOURCE_ARGUMENT);
        source = source == null
                ? ImportRuntimeCliContract.DEFAULT_SOURCE
                : source.toLowerCase(Locale.ROOT);

        ConsolidationOption clubs = ConsolidationOption.writeDefault();
        ConsolidationOption players = ConsolidationOption.writeDefault();
        ConsolidationOption rfetmClubs = ConsolidationOption.writeDefault();

        for (String arg : args) {
            clubs = parseConsolidationOption(
                    arg,
                    ImportRuntimeCliContract.CONSOLIDATE_CLUBS_FLAG,
                    ImportRuntimeCliContract.CONSOLIDATE_CLUBS_ARGUMENT,
                    clubs);
            players = parseConsolidationOption(
                    arg,
                    ImportRuntimeCliContract.CONSOLIDATE_PLAYERS_FLAG,
                    ImportRuntimeCliContract.CONSOLIDATE_PLAYERS_ARGUMENT,
                    players);
            rfetmClubs = parseConsolidationOption(
                    arg,
                    ImportRuntimeCliContract.CONSOLIDATE_RFETM_CLUBS_FLAG,
                    ImportRuntimeCliContract.CONSOLIDATE_RFETM_CLUBS_ARGUMENT,
                    rfetmClubs
            );
        }
        return new ImportRuntimeArguments(
                source,
                valueOf(args, ImportRuntimeCliContract.ACTAS_FOLDER_ARGUMENT),
                valueOf(args, ImportRuntimeCliContract.RFETM_TEAMS_FOLDER_ARGUMENT),
                valueOf(args, ImportRuntimeCliContract.SEASON_ARGUMENT),
                clubs.enabled(),
                clubs.mode(),
                players.enabled(),
                players.mode(),
                rfetmClubs.enabled(),
                rfetmClubs.mode());
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

    private static ConsolidationOption parseConsolidationOption(
            String arg,
            String flag,
            String argument,
            ConsolidationOption current) {
        if (flag.equals(arg)) {
            return current.withEnabled();
        }
        if (!arg.startsWith(argument)) {
            return current;
        }

        String value = arg.substring(argument.length()).trim().toLowerCase(Locale.ROOT);
        if (ImportRuntimeCliContract.MODE_REPORT.equals(value)) {
            return new ConsolidationOption(true, ConsolidationMode.REPORT);
        }
        if (!value.isEmpty()
                && !ImportRuntimeCliContract.MODE_TRUE.equals(value)
                && !ImportRuntimeCliContract.MODE_WRITE.equals(value)) {
            throw new IllegalArgumentException("Unknown " + argument + value
                    + "; expected report or write");
        }
        return current.withEnabled();
    }

    private record ConsolidationOption(boolean enabled, ConsolidationMode mode) {
        private static ConsolidationOption writeDefault() {
            return new ConsolidationOption(false, ConsolidationMode.WRITE);
        }

        private ConsolidationOption withEnabled() {
            return new ConsolidationOption(true, mode);
        }
    }
}
