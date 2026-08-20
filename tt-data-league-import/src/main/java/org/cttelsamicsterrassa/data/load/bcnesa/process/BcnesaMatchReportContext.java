package org.cttelsamicsterrassa.data.load.bcnesa.process;

import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaMatchdaySplitter;
import org.cttelsamicsterrassa.data.load.shared.parse.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaScore;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Everything a {@link BcnesaMatchReportProcessor} needs about one BCNESA fixture.
 *
 * <p>A BCNESA match report covers a whole matchday of one group, not one match: it holds
 * every fixture played that day back to back, reusing the same lineup letters (A/B/C, X/Y/Z) for
 * each. {@link BcnesaActasDirectoryNavigator} splits a file into fixtures via
 * {@link BcnesaMatchdaySplitter} and dispatches one
 * context per fixture, so {@link #acta()} is the whole file's parsed payload (shared by every
 * fixture in it) while {@link #games()} is this fixture's own slice.</p>
 *
 * <p>{@code homeTeamName}/{@code awayTeamName} come from {@code equipos} for the file's first fixture
 * and are inferred from the fixture's own players for every other one; they are never guessed, so a
 * fixture whose clubs could not be attributed carries {@code null} here and must be skipped by
 * processors rather than stored under a wrong club.</p>
 *
 * <p>The report file name is opaque and is never parsed: {@code round} comes from the payload's
 * {@code jornada}.</p>
 *
 * @param season            season folder, in {@code YYYY-YYYY} form (for example {@code 2020-2021})
 * @param leagueCompetition league or competition folder (for example {@code Preferent})
 * @param group             group folder (for example {@code G1})
 * @param phase             phase folder (for example {@code 1a Fase})
 * @param round             match day, from the payload's {@code jornada}. Every fixture split from
 *                          one file shares it, which is correct: a round holds every match played on
 *                          one matchday, and the {@code MATCH} natural key's two club columns are
 *                          what tells them apart
 * @param fixtureIndex      0-based position of this fixture within the file
 * @param homeTeamName      home club name, or {@code null} if it could not be attributed
 * @param awayTeamName      away club name, or {@code null} if it could not be attributed
 * @param matchReportFile   the report file itself (shared by every fixture split from it)
 * @param acta              the parsed report payload (shared by every fixture split from it)
 * @param games             this fixture's own games, a sub-list of {@code acta.games()}
 */
public record BcnesaMatchReportContext(
        String season,
        String leagueCompetition,
        String group,
        String phase,
        int round,
        int fixtureIndex,
        String homeTeamName,
        String awayTeamName,
        Path matchReportFile,
        Acta acta,
        List<ActaGame> games) {

    public BcnesaMatchReportContext {
        Objects.requireNonNull(season, "season");
        Objects.requireNonNull(leagueCompetition, "leagueCompetition");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(matchReportFile, "matchReportFile");
        games = games == null ? List.of() : List.copyOf(games);
    }

    /**
     * Whether both clubs of this fixture were attributed. Processors must skip an unresolved fixture
     * rather than store it under a guessed club.
     */
    public boolean isResolved() {
        return homeTeamName != null && awayTeamName != null;
    }

    /**
     * The season folder as a domain {@link Season}. This is the single place where the folder form
     * {@code 2020-2021} is turned into a season value.
     */
    public Season toSeason() {
        return Season.fromFormatted(season);
    }

    /**
     * The group folder's number (for example {@code 1} for {@code G1}).
     */
    public int groupNumber() {
        return Integer.parseInt(group.substring(1));
    }

    /**
     * Competition identity, taken from the path verbatim - unlike RFETM, BCNESA's {@code competicion}
     * payload field is not generic, but the folder is the authoritative source for the same reason:
     * it is present and consistent for every file, while payload fields occasionally drift.
     */
    public String competition() {
        return leagueCompetition;
    }

    /**
     * Games this fixture's home side won, derived from each game's {@code ganador} rather than from
     * the file-level {@code resultado_final}, which aggregates every fixture in the file.
     */
    public int homeGamesWon() {
        return (int) games.stream().filter(g -> ActaGame.WINNER_HOME.equals(g.winner())).count();
    }

    /**
     * Games this fixture's away side won. See {@link #homeGamesWon()}.
     */
    public int awayGamesWon() {
        return (int) games.stream().filter(g -> ActaGame.WINNER_AWAY.equals(g.winner())).count();
    }

    /**
     * Sets this fixture's home side won, summed from each game's own {@code resultado_juegos}. BCNESA
     * carries no set-by-set score ({@code sets} is empty throughout the export), so this is the finest
     * granularity available.
     */
    public int homeSetsWon() {
        return games.stream().mapToInt(g -> setsWon(g, true)).sum();
    }

    /**
     * Sets this fixture's away side won. See {@link #homeSetsWon()}.
     */
    public int awaySetsWon() {
        return games.stream().mapToInt(g -> setsWon(g, false)).sum();
    }

    private static int setsWon(ActaGame game, boolean home) {
        ActaScore score = game.setsWon();
        if (score == null) {
            return 0;
        }
        Integer value = home ? score.home() : score.away();
        return value != null ? value : 0;
    }
}
