package org.cttelsamicsterrassa.data.load.runtime;

import java.util.List;

final class ImportRuntimeCliContract {
    static final String SOURCE_ARGUMENT = "--source=";
    static final String ACTAS_FOLDER_ARGUMENT = "--actas-folder=";
    static final String RFETM_TEAMS_FOLDER_ARGUMENT = "--rfetm-teams-folder=";
    static final String SEASON_ARGUMENT = "--season=";

    static final String SOURCE_RFETM = "rfetm";
    static final String SOURCE_BCNESA = "bcnesa";
    static final String SOURCE_FCTT = "fctt";
    static final String DEFAULT_SOURCE = SOURCE_RFETM;

    static final List<String> SUPPORTED_SOURCES = List.of(
            SOURCE_RFETM,
            SOURCE_BCNESA,
            SOURCE_FCTT);

    private ImportRuntimeCliContract() {
    }

    static String usage() {
        return SOURCE_ARGUMENT + String.join("|", SUPPORTED_SOURCES)
                + " " + ACTAS_FOLDER_ARGUMENT + "<path>"
                + " " + RFETM_TEAMS_FOLDER_ARGUMENT + "<path>"
                + " [" + SEASON_ARGUMENT + "<YYYY-YYYY>]";
    }

    static String supportedSourcesForMessage() {
        return String.join(", ", SUPPORTED_SOURCES.stream()
                .map(source -> "\"" + source + "\"")
                .toList());
    }
}
