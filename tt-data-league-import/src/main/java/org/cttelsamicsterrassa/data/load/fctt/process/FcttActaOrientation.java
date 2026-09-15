package org.cttelsamicsterrassa.data.load.fctt.process;

import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaDoubles;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaLineups;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaScore;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaSet;

import java.util.Objects;

/**
 * Older FCTT extracts key lineups, doubles and games by the A/B/C column instead of home/away while
 * {@code equipos} and {@code resultado_final} use the real sides; a last running score that mirrors
 * the final score reveals that A/B/C was the away team.
 */
final class FcttActaOrientation {

    private FcttActaOrientation() {
    }

    static Acta toHomeAway(Acta acta) {
        if (!isMirrored(acta)) {
            return acta;
        }
        return new Acta(acta.federation(), acta.season(), acta.competition(), acta.group(), acta.round(),
                acta.date(), acta.time(), acta.venue(), acta.teams(), false, acta.officials(),
                swapLineups(acta.lineups()), swapDoubles(acta.doubles()),
                acta.games().stream().map(FcttActaOrientation::swapGame).toList(),
                acta.finalResult(), acta.protested());
    }

    static boolean isMirrored(Acta acta) {
        ActaScore finalScore = acta.finalResult() == null ? null : acta.finalResult().gamesWon();
        ActaScore running = acta.games().isEmpty() ? null : acta.games().getLast().cumulativeScore();
        if (finalScore == null || running == null || finalScore.home() == null || finalScore.away() == null
                || finalScore.home().equals(finalScore.away())) {
            return false;
        }
        return Objects.equals(finalScore.home(), running.away()) && Objects.equals(finalScore.away(), running.home());
    }

    private static ActaLineups swapLineups(ActaLineups lineups) {
        return lineups == null ? null : new ActaLineups(lineups.away(), lineups.home());
    }

    private static ActaDoubles swapDoubles(ActaDoubles doubles) {
        return doubles == null ? null : new ActaDoubles(doubles.away(), doubles.home());
    }

    private static ActaGame swapGame(ActaGame game) {
        return new ActaGame(game.number(), game.type(), game.crossover(), game.away(), game.home(),
                game.sets().stream().map(set -> new ActaSet(set.number(), set.awayPoints(), set.homePoints())).toList(),
                swapScore(game.setsWon()), swapWinner(game.winner()), swapScore(game.cumulativeScore()),
                game.notPlayed(), game.reason());
    }

    private static ActaScore swapScore(ActaScore score) {
        return score == null ? null : new ActaScore(score.away(), score.home());
    }

    private static String swapWinner(String winner) {
        return ActaGame.WINNER_HOME.equals(winner) ? ActaGame.WINNER_AWAY
                : ActaGame.WINNER_AWAY.equals(winner) ? ActaGame.WINNER_HOME : winner;
    }
}
