package org.cttelsamicsterrassa.data.load.shared.classify;

import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaLineups;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaScore;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaTeam;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaTeams;

import java.util.List;
import java.util.Objects;

/**
 * Classifies an {@link Acta} into an {@link ActaCompleteness} without ever reading
 * {@link Acta#finalResult()} or {@link ActaGame#cumulativeScore()}. See
 * {@code docs/acta-model-definition.json} for the source schema.
 *
 * <p>Rules are evaluated in order and the first match wins:</p>
 * <ol>
 *   <li>{@code acta_publicada == false} → PENDING, regardless of game/lineup content.</li>
 *   <li>{@code acta_publicada == true} but missing games, results, lineups, or {@code abc_es_local}
 *       → INVALID.</li>
 *   <li>{@code acta_publicada == true} and complete → PLAYED. A game counts as complete when it has a
 *       result or is marked {@code no_disputado}.</li>
 *   <li>{@code acta_publicada} is missing (legacy) and no game has a result (this covers actas with no
 *       games at all, and reports "decided" without play) → PENDING.</li>
 *   <li>{@code acta_publicada} is missing and every game has a result or is {@code no_disputado} →
 *       PLAYED.</li>
 *   <li>Otherwise (legacy, some games complete and some neither) → PARTIAL.</li>
 * </ol>
 *
 * <p>A PENDING classification also records whether it is an unresolved pending fixture: one whose
 * {@link Acta#teams()} is null, or either side's name is null or blank. Team ids are not required,
 * because legacy BCNESA pending fixtures carry names but no ids.</p>
 *
 * <p>Stateless; safe to hold as a field. Not a Spring bean.</p>
 */
public final class ActaCompletenessClassifier {

    public ActaClassification classify(Acta acta) {
        Objects.requireNonNull(acta, "acta");
        return classify(acta, acta.games());
    }

    public ActaClassification classify(Acta acta, List<ActaGame> games) {
        Objects.requireNonNull(acta, "acta");
        Objects.requireNonNull(games, "games");

        if (Boolean.FALSE.equals(acta.published())) {
            return pending("acta_publicada is false", acta);
        }

        if (Boolean.TRUE.equals(acta.published())) {
            String invalidReason = publishedInvalidReason(acta, games);
            if (invalidReason != null) {
                return new ActaClassification(ActaCompleteness.INVALID, invalidReason, false);
            }
            return new ActaClassification(ActaCompleteness.PLAYED, "acta_publicada is true and complete", false);
        }

        long withResult = games.stream().filter(ActaCompletenessClassifier::hasResult).count();
        if (withResult == 0) {
            return pending("legacy acta with no game result", acta);
        }

        long incomplete = games.stream()
                .filter(game -> !hasResult(game) && !game.wasNotPlayed())
                .count();
        if (incomplete == 0) {
            return new ActaClassification(ActaCompleteness.PLAYED, "legacy acta with every game complete", false);
        }

        return new ActaClassification(ActaCompleteness.PARTIAL, incomplete + " game(s) with neither a result nor no_disputado", false);
    }

    private static String publishedInvalidReason(Acta acta, List<ActaGame> games) {
        if (games.isEmpty()) {
            return "acta_publicada is true but has no games";
        }
        if (games.stream().noneMatch(ActaCompletenessClassifier::hasResult)) {
            return "acta_publicada is true but no game has a result";
        }
        ActaLineups lineups = acta.lineups();
        if (lineups == null || lineups.home().isEmpty() || lineups.away().isEmpty()) {
            return "acta_publicada is true but a lineup side is empty";
        }
        if (acta.abcIsHome() == null) {
            return "acta_publicada is true but abc_es_local is null";
        }
        return null;
    }

    private static ActaClassification pending(String reason, Acta acta) {
        return new ActaClassification(ActaCompleteness.PENDING, reason, isUnresolved(acta.teams()));
    }

    private static boolean isUnresolved(ActaTeams teams) {
        if (teams == null) {
            return true;
        }
        return isBlank(teams.home()) || isBlank(teams.away());
    }

    private static boolean isBlank(ActaTeam team) {
        return team == null || team.name() == null || team.name().isBlank();
    }

    static boolean hasResult(ActaGame game) {
        if (!game.sets().isEmpty()) {
            return true;
        }
        if (game.winner() != null && !game.winner().isBlank()) {
            return true;
        }
        ActaScore setsWon = game.setsWon();
        return setsWon != null && (setsWon.home() != null || setsWon.away() != null);
    }
}
