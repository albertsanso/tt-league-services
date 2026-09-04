package org.cttelsamicsterrassa.data.load.shared.preview;

import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaLineupPlayer;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParticipant;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class ActaPreviewValidationSupport {

    private ActaPreviewValidationSupport() {
    }

    public static String location(Path path) {
        return path == null ? null : path.toString();
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static void validateLineupPlayers(Map<String, ActaLineupPlayer> players,
                                             ImportPreviewCollector collector,
                                             Path reportFile,
                                             String source) {
        players.forEach((letter, player) -> {
            if (player == null || isBlank(player.name()) || isBlank(player.licenseId())) {
                collector.warning("%s lineup letter %s has no player name or licence; it will be skipped."
                                .formatted(source, letter),
                        location(reportFile));
            }
        });
    }

    public static void validateDoublesPlayers(List<ActaLineupPlayer> players,
                                              ImportPreviewCollector collector,
                                              Path reportFile,
                                              String source) {
        for (ActaLineupPlayer player : players) {
            if (player == null || isBlank(player.name()) || isBlank(player.licenseId())) {
                collector.warning("%s doubles player has no name or licence; it will be skipped."
                                .formatted(source),
                        location(reportFile));
            }
        }
    }

    public static void validateGameParticipants(List<ActaGame> games,
                                                ImportPreviewCollector collector,
                                                Path reportFile,
                                                String source) {
        for (ActaGame game : games) {
            if (game.number() == null) {
                collector.warning("%s game without a number will be left out.".formatted(source),
                        location(reportFile));
            }
            if (game.isDoubles()) {
                validateDoublesParticipant(game.home(), collector, reportFile, source);
                validateDoublesParticipant(game.away(), collector, reportFile, source);
            }
        }
    }

    private static void validateDoublesParticipant(ActaParticipant participant,
                                                   ImportPreviewCollector collector,
                                                   Path reportFile,
                                                   String source) {
        if (participant == null) {
            return;
        }
        validateDoublesPlayers(participant.doublesPlayers(), collector, reportFile, source);
    }
}
