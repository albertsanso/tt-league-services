package org.cttelsamicsterrassa.data.load.runtime;

import java.util.Locale;
import java.util.Optional;

/**
 * Command-line arguments for a single import run.
 */
public record ImportRuntimeArguments(
        String source,
        String actasFolder,
        String rfetmTeamsFolder,
        String season
) {
    public static ImportRuntimeArguments parse(String... args) {
        String source = valueOf(args, ImportRuntimeCliContract.SOURCE_ARGUMENT);
        source = source == null
                ? ImportRuntimeCliContract.DEFAULT_SOURCE
                : source.toLowerCase(Locale.ROOT);

        return new ImportRuntimeArguments(
                source,
                valueOf(args, ImportRuntimeCliContract.ACTAS_FOLDER_ARGUMENT),
                valueOf(args, ImportRuntimeCliContract.RFETM_TEAMS_FOLDER_ARGUMENT),
                valueOf(args, ImportRuntimeCliContract.SEASON_ARGUMENT));
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
