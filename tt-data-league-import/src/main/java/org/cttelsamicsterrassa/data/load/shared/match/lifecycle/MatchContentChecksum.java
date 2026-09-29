package org.cttelsamicsterrassa.data.load.shared.match.lifecycle;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.game.model.SetScore;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * The content checksum of one {@link MatchContent} (FEAT-00089): a versioned ({@code v1:}) SHA-256
 * over a canonical UTF-8 rendering of exactly what an import would write. It is deliberately a
 * <em>content</em> checksum, not a raw-file checksum: BCNESA splits one matchday file into several
 * fixtures and file formatting or renames must never trigger a rewrite, while any change a re-apply
 * would write must change the value.
 *
 * <p>The canonical form is independent of generated UUIDs (match id, child ids) and of child list
 * order: header fields are written in a fixed order, children are sorted by their natural ordering,
 * and every value is length-prefixed with a dedicated {@code null} token so adjacent values cannot
 * collide. The match id is never part of the checksum.</p>
 */
public final class MatchContentChecksum {

    /** Versions the canonical form; a stored value with another prefix is never an amendment. */
    public static final String PREFIX = "v1:";

    private MatchContentChecksum() {
    }

    public static String of(MatchContent content) {
        Objects.requireNonNull(content, "content");
        StringBuilder canonical = new StringBuilder();
        appendHeader(canonical, content.match());
        appendLineups(canonical, content.lineups());
        appendGames(canonical, content.games());
        appendSetScores(canonical, content.setScores());
        appendDoublesPairs(canonical, content.doublesPairs());
        byte[] digest = sha256(canonical.toString());
        return PREFIX + HexFormat.of().formatHex(digest);
    }

    private static void appendHeader(StringBuilder canonical, Match match) {
        field(canonical, match.getSource() == null ? null : match.getSource().name());
        field(canonical, match.getSourceFixtureId());
        field(canonical, match.getExternalId());
        field(canonical, match.getCompetition());
        field(canonical, match.getSeason() == null ? null : match.getSeason().toString());
        field(canonical, integer(match.getGroupNumber()));
        field(canonical, Integer.toString(match.getRound()));
        field(canonical, match.getPhase());
        field(canonical, match.getDateTime() == null ? null : match.getDateTime().toInstant().toString());
        field(canonical, match.getCity());
        field(canonical, match.getVenue());
        field(canonical, teamId(match.getHomeTeam()));
        field(canonical, teamId(match.getAwayTeam()));
        field(canonical, teamId(match.getWinnerTeam()));
        field(canonical, match.getRefereeName());
        field(canonical, match.getRefereeLicense());
        field(canonical, integer(match.getHomeGamesWon()));
        field(canonical, integer(match.getAwayGamesWon()));
        field(canonical, integer(match.getHomeSetsWon()));
        field(canonical, integer(match.getAwaySetsWon()));
        field(canonical, Boolean.toString(match.isProtested()));
        field(canonical, match.getStatus() == null ? null : match.getStatus().name());
        section(canonical);
    }

    private static void appendLineups(StringBuilder canonical, List<Lineup> lineups) {
        lineups.stream()
                .sorted(Comparator
                        .comparing((Lineup lineup) -> teamId(lineup.getTeam()), Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(Lineup::getLetter, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparingInt(Lineup::getPosition))
                .forEach(lineup -> {
                    field(canonical, teamId(lineup.getTeam()));
                    field(canonical, lineup.getLetter());
                    field(canonical, Integer.toString(lineup.getPosition()));
                    field(canonical, playerId(lineup.getPlayer()));
                    field(canonical, lineup.getRanking() == null ? null : lineup.getRanking().toString());
                    section(canonical);
                });
    }

    private static void appendGames(StringBuilder canonical, List<Game> games) {
        games.stream()
                .sorted(Comparator.comparingInt(Game::getGameNumber))
                .forEach(game -> {
                    field(canonical, game.getSource() == null ? null : game.getSource().name());
                    field(canonical, Integer.toString(game.getGameNumber()));
                    field(canonical, game.getType());
                    field(canonical, game.getCrossover());
                    field(canonical, playerId(game.getHomePlayer()));
                    field(canonical, playerId(game.getAwayPlayer()));
                    field(canonical, integer(game.getHomeSetsWon()));
                    field(canonical, integer(game.getAwaySetsWon()));
                    field(canonical, playerId(game.getWinner()));
                    field(canonical, game.getWinnerSide());
                    field(canonical, Integer.toString(game.getCumulativeHomeSetsWon()));
                    field(canonical, Integer.toString(game.getCumulativeAwaySetsWon()));
                    field(canonical, Boolean.toString(game.isNotPlayed()));
                    field(canonical, game.getReason());
                    section(canonical);
                });
    }

    private static void appendSetScores(StringBuilder canonical, List<SetScore> setScores) {
        setScores.stream()
                .sorted(Comparator
                        .comparingInt((SetScore setScore) -> gameNumber(setScore.getGame()))
                        .thenComparingInt(SetScore::getSetNumber))
                .forEach(setScore -> {
                    field(canonical, setScore.getSource() == null ? null : setScore.getSource().name());
                    field(canonical, Integer.toString(gameNumber(setScore.getGame())));
                    field(canonical, Integer.toString(setScore.getSetNumber()));
                    field(canonical, Integer.toString(setScore.getHomePoints()));
                    field(canonical, Integer.toString(setScore.getAwayPoints()));
                    section(canonical);
                });
    }

    private static void appendDoublesPairs(StringBuilder canonical, List<DoublesPair> doublesPairs) {
        doublesPairs.stream()
                .sorted(Comparator
                        .comparingInt((DoublesPair pair) -> gameNumber(pair.getGame()))
                        .thenComparing(DoublesPair::getSide, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(pair -> playerId(pair.getPlayer()), Comparator.nullsFirst(Comparator.naturalOrder())))
                .forEach(pair -> {
                    field(canonical, pair.getSource() == null ? null : pair.getSource().name());
                    field(canonical, Integer.toString(gameNumber(pair.getGame())));
                    field(canonical, pair.getSide());
                    field(canonical, playerId(pair.getPlayer()));
                    section(canonical);
                });
    }

    private static int gameNumber(Game game) {
        return game == null ? Integer.MIN_VALUE : game.getGameNumber();
    }

    private static String integer(Integer value) {
        return value == null ? null : value.toString();
    }

    private static String teamId(Team team) {
        return team == null || team.getId() == null ? null : team.getId().toString();
    }

    private static String playerId(PlayerSeason player) {
        return player == null || player.getId() == null ? null : player.getId().toString();
    }

    /**
     * Appends one value: {@code N;} for {@code null}, otherwise its decimal length, a colon, the
     * value and a {@code ;}. The length prefix makes adjacent values unambiguous, so no two different
     * sequences can ever render the same canonical text.
     */
    private static void field(StringBuilder canonical, String value) {
        if (value == null) {
            canonical.append("N;");
            return;
        }
        canonical.append(value.length()).append(':').append(value).append(';');
    }

    private static void section(StringBuilder canonical) {
        canonical.append('|');
    }

    private static byte[] sha256(String canonical) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}