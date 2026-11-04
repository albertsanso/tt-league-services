package org.cttelsamicsterrassa.data.load.bcnesa.traverse;

import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParticipant;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaTeam;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaTeams;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Splits one BCNESA {@link Acta}'s games into the fixtures they actually belong to.
 *
 * <p>Measured over the whole export (16,387 files, 2020-2021 to 2025-2026), every BCNESA report holds
 * exactly one fixture, so this split yields a single fixture per file. The split also accepts a
 * report that holds every fixture of a matchday one after another, reusing the same lineup letters
 * (A/B/C, X/Y/Z) for each: {@code A vs Y} is the first crossing in all 16,387 files, so a new fixture
 * starts wherever a game's {@code cruce} repeats the file's first crossing. A report with no games
 * yields one fixture named by {@code equipos} alone (FEAT-00081): that is the shape of a pending
 * acta whose scheduled fixture has no published games yet, and the match processor stores it as a
 * SCHEDULED match.</p>
 *
 * <p>The first fixture is named directly, by {@code equipos}; any later fixture's clubs are inferred
 * from its own participants' licences via a {@link BcnesaClubIndex} obtained from the supplied
 * {@link Supplier}. The supplier is consulted only once a fixture after the first is reached, so a
 * single-fixture or no-games report never builds the index; the supplier is responsible for caching,
 * so a whole group builds it at most once. A later fixture is left unresolved ({@code null} names)
 * when the index cannot resolve it - callers must treat a fixture with either name {@code null} as
 * unresolved rather than guessing.</p>
 */
public final class BcnesaMatchdaySplitter {

    /**
     * One fixture's games and its (possibly unresolved) clubs.
     */
    public record Fixture(String homeTeamName, String awayTeamName, List<ActaGame> games) {

        public Fixture {
            games = List.copyOf(games);
        }

        public boolean isResolved() {
            return homeTeamName != null && awayTeamName != null;
        }
    }

    /**
     * Splits {@code acta} using a fixed index, for callers that already have one.
     */
    public List<Fixture> split(Acta acta, BcnesaClubIndex clubIndex) {
        return split(acta, () -> clubIndex);
    }

    /**
     * Splits {@code acta}, consulting {@code clubIndex} only for the second and later fixtures so a
     * single-fixture or no-games report never triggers an index build.
     */
    public List<Fixture> split(Acta acta, Supplier<BcnesaClubIndex> clubIndex) {
        List<ActaGame> games = acta.games();
        if (games.isEmpty()) {
            return List.of(new Fixture(teamName(acta.teams(), true), teamName(acta.teams(), false),
                    List.of()));
        }

        List<List<ActaGame>> cycles = splitIntoCycles(games);
        List<Fixture> fixtures = new ArrayList<>(cycles.size());
        for (int i = 0; i < cycles.size(); i++) {
            List<ActaGame> cycleGames = cycles.get(i);
            String home;
            String away;
            if (i == 0) {
                home = teamName(acta.teams(), true);
                away = teamName(acta.teams(), false);
            } else {
                BcnesaClubIndex index = clubIndex.get();
                home = index.resolve(licensesOf(cycleGames, true)).orElse(null);
                away = index.resolve(licensesOf(cycleGames, false)).orElse(null);
            }
            fixtures.add(new Fixture(home, away, cycleGames));
        }
        return fixtures;
    }

    private static List<List<ActaGame>> splitIntoCycles(List<ActaGame> games) {
        String firstCrossover = games.get(0).crossover();
        List<List<ActaGame>> cycles = new ArrayList<>();
        List<ActaGame> current = new ArrayList<>();
        for (ActaGame game : games) {
            if (Objects.equals(game.crossover(), firstCrossover) && !current.isEmpty()) {
                cycles.add(current);
                current = new ArrayList<>();
            }
            current.add(game);
        }
        if (!current.isEmpty()) {
            cycles.add(current);
        }
        return cycles;
    }

    private static String teamName(ActaTeams teams, boolean home) {
        if (teams == null) {
            return null;
        }
        ActaTeam team = home ? teams.home() : teams.away();
        return team != null ? team.name() : null;
    }

    private static List<String> licensesOf(List<ActaGame> games, boolean home) {
        List<String> licenses = new ArrayList<>();
        for (ActaGame game : games) {
            ActaParticipant participant = home ? game.home() : game.away();
            if (participant != null && participant.license() != null) {
                licenses.add(participant.license());
            }
        }
        return licenses;
    }
}
