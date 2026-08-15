package org.cttelsamicsterrassa.data.load.bcnesa.traverse;

import org.cttelsamicsterrassa.data.load.shared.parse.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaParticipant;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaTeam;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaTeams;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Splits one BCNESA {@link Acta}'s games into the fixtures they actually belong to.
 *
 * <p>A BCNESA report holds every fixture of a matchday, one after another, reusing the same lineup
 * letters (A/B/C, X/Y/Z) for each. Measured over the whole export, {@code A vs Y} is the first
 * crossing in all 2,996 files, so a new fixture starts wherever a game's {@code cruce} repeats the
 * file's first crossing.</p>
 *
 * <p>Only the first fixture is named directly, by {@code equipos}; every later fixture's clubs are
 * inferred from its own participants' licences via {@link BcnesaClubIndex}, and are left {@code null}
 * when the index cannot resolve them - callers must treat a fixture with either name {@code null} as
 * unresolved rather than guessing.</p>
 */
public final class BcnesaMatchdaySplitter {

    /**
     * One fixture's games and its (possibly unresolved) clubs.
     */
    public record Fixture(String homeClubName, String awayClubName, List<ActaGame> games) {

        public Fixture {
            games = List.copyOf(games);
        }

        public boolean isResolved() {
            return homeClubName != null && awayClubName != null;
        }
    }

    public List<Fixture> split(Acta acta, BcnesaClubIndex clubIndex) {
        List<ActaGame> games = acta.games();
        if (games.isEmpty()) {
            return List.of();
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
                home = clubIndex.resolve(licensesOf(cycleGames, true)).orElse(null);
                away = clubIndex.resolve(licensesOf(cycleGames, false)).orElse(null);
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
