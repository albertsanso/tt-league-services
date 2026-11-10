package org.cttelsamicsterrassa.data.load.shared.fcttweb.traverse;

import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaTeam;

import java.util.List;

/**
 * Hook that splits one parsed report into the fixtures it holds.
 *
 * <p>Every file of the ingest layout holds exactly one fixture, so {@link #wholeFile()} applies.
 * Legacy BCNESA files may hold a whole matchday back to back; their splitter infers the clubs of the
 * later fixtures from licences (see {@code BcnesaMatchdaySplitter#asFixtureSplitter}). A fixture with
 * a {@code null} club is unresolved and must be skipped, never guessed at.</p>
 */
@FunctionalInterface
public interface FixtureSplitter {

    /** One fixture's clubs (possibly unresolved) and its own games. */
    record Fixture(String homeTeamName, String awayTeamName, List<ActaGame> games) {

        public Fixture {
            games = List.copyOf(games);
        }

        public boolean isResolved() {
            return homeTeamName != null && awayTeamName != null;
        }
    }

    List<Fixture> split(Acta acta);

    /** The whole report is one fixture, named by {@code equipos} and holding every game. */
    static FixtureSplitter wholeFile() {
        return acta -> List.of(new Fixture(teamName(acta, true), teamName(acta, false), acta.games()));
    }

    private static String teamName(Acta acta, boolean home) {
        if (acta.teams() == null) {
            return null;
        }
        ActaTeam team = home ? acta.teams().home() : acta.teams().away();
        return team == null ? null : team.name();
    }
}
