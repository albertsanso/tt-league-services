package org.cttelsamicsterrassa.data.load.runtime;

import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationMode;
import org.cttelsamicsterrassa.data.load.shared.match.backfill.ScheduledMatchBackfillMode;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.AmendedActaMode;

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
        boolean backfillScheduledMatches,
        ScheduledMatchBackfillMode backfillMode,
        AmendedActaMode amendedActaMode
) {
    public static ImportRuntimeArguments parse(String... args) {
        String source = valueOf(args, ImportRuntimeCliContract.SOURCE_ARGUMENT);
        source = source == null
                ? ImportRuntimeCliContract.DEFAULT_SOURCE
                : source.toLowerCase(Locale.ROOT);

        ModeSelection clubs = parseModeSelection(args, ImportRuntimeCliContract.CONSOLIDATE_CLUBS_ARGUMENT);
        ModeSelection players = parseModeSelection(args, ImportRuntimeCliContract.CONSOLIDATE_PLAYERS_ARGUMENT);
        ModeSelection backfill =
                parseModeSelection(args, ImportRuntimeCliContract.BACKFILL_SCHEDULED_MATCHES_ARGUMENT);
        ModeSelection amendedActas =
                parseModeSelection(args, ImportRuntimeCliContract.DETECT_AMENDED_ACTAS_ARGUMENT);

        return new ImportRuntimeArguments(
                source,
                valueOf(args, ImportRuntimeCliContract.ACTAS_FOLDER_ARGUMENT),
                valueOf(args, ImportRuntimeCliContract.RFETM_TEAMS_FOLDER_ARGUMENT),
                valueOf(args, ImportRuntimeCliContract.SEASON_ARGUMENT),
                clubs.enabled(),
                toConsolidationMode(ImportRuntimeCliContract.CONSOLIDATE_CLUBS_ARGUMENT, clubs.rawMode()),
                players.enabled(),
                toConsolidationMode(ImportRuntimeCliContract.CONSOLIDATE_PLAYERS_ARGUMENT, players.rawMode()),
                backfill.enabled(),
                toBackfillMode(ImportRuntimeCliContract.BACKFILL_SCHEDULED_MATCHES_ARGUMENT, backfill.rawMode()),
                amendedActas.enabled()
                        ? toAmendedActaMode(ImportRuntimeCliContract.DETECT_AMENDED_ACTAS_ARGUMENT,
                                amendedActas.rawMode())
                        : null);
    }

    public Optional<String> optionalSeason() {
        return Optional.ofNullable(season);
    }

    /**
     * Parses whether {@code optionName} is present and, when it is, which raw mode value it carries:
     * bare (no value), {@code =write}, or {@code =report}. Shared by every write/report-mode flag
     * (club and player consolidation, and the scheduled-match backfill), which each map the raw value
     * to their own mode enum so an invalid value still names the right flag in its error message.
     */
    private static ModeSelection parseModeSelection(String[] args, String optionName) {
        boolean enabled = false;
        String rawMode = "";
        for (String arg : args) {
            if (arg.equals(optionName)) {
                enabled = true;
                rawMode = "";
                continue;
            }
            String withEquals = optionName + "=";
            if (arg.startsWith(withEquals)) {
                enabled = true;
                rawMode = arg.substring(withEquals.length()).trim().toLowerCase(Locale.ROOT);
            }
        }
        return new ModeSelection(enabled, rawMode);
    }

    private static ConsolidationMode toConsolidationMode(String optionName, String rawValue) {
        return switch (rawValue) {
            case "", "true", "write" -> ConsolidationMode.WRITE;
            case "report" -> ConsolidationMode.REPORT;
            default -> throw new IllegalArgumentException(
                    "Unsupported consolidation mode: " + optionName + "=" + rawValue);
        };
    }

    private static ScheduledMatchBackfillMode toBackfillMode(String optionName, String rawValue) {
        return switch (rawValue) {
            case "", "true", "write" -> ScheduledMatchBackfillMode.WRITE;
            case "report" -> ScheduledMatchBackfillMode.REPORT;
            default -> throw new IllegalArgumentException(
                    "Unsupported backfill mode: " + optionName + "=" + rawValue);
        };
    }

    private static AmendedActaMode toAmendedActaMode(String optionName, String rawValue) {
        return switch (rawValue) {
            case "", "true", "write" -> AmendedActaMode.WRITE;
            case "report" -> AmendedActaMode.REPORT;
            default -> throw new IllegalArgumentException(
                    "Unsupported amended-acta detection mode: " + optionName + "=" + rawValue);
        };
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

    private record ModeSelection(boolean enabled, String rawMode) {
    }
}
