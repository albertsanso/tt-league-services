package org.cttelsamicsterrassa.data.load.shared.classify;

import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaFinalResult;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaLineupPlayer;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaLineups;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParser;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaScore;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaTeam;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaTeams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rule-by-rule coverage of {@link ActaCompletenessClassifier}, plus a fixture sweep over every
 * anonymised acta in {@code src/test/resources/actas/}.
 */
class ActaCompletenessClassifierTest {

    private final ActaCompletenessClassifier classifier = new ActaCompletenessClassifier();
    private final ActaParser parser = new ActaParser();

    // --- Rule 1: acta_publicada is false ---------------------------------------------------

    @Test
    void unpublishedIsPendingEvenWithPlayedGamesFullLineupsAndAWinner() {
        ActaFinalResult finalResult = new ActaFinalResult("local", new ActaScore(3, 0), new ActaScore(9, 2));
        Acta acta = new Acta(null, false, null, null, null, null, null, null, null, null, null, null,
                teams("Home", "Away"), true, null, lineups(3, 3), null,
                List.of(game(List.of(new org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaSet(1, 11, 5)), null, null)),
                finalResult, null);

        ActaClassification classification = classifier.classify(acta);

        assertEquals(ActaCompleteness.PENDING, classification.completeness());
    }

    @Test
    void resultadoFinalIsNeverReadForPublishedOrLegacyActas() {
        Acta publishedConsistent = acta(true, true, lineups(3, 3), teams("Home", "Away"),
                List.of(gameWithWinner("local")));
        Acta publishedContradicting = withFinalResult(publishedConsistent,
                new ActaFinalResult("visitante", new ActaScore(0, 3), new ActaScore(2, 9)));
        assertEquals(classifier.classify(publishedConsistent).completeness(),
                classifier.classify(publishedContradicting).completeness());

        Acta legacyConsistent = acta(null, null, lineups(0, 0), null, List.of(gameWithWinner("local")));
        Acta legacyNullResult = withFinalResult(legacyConsistent, null);
        assertEquals(classifier.classify(legacyConsistent).completeness(),
                classifier.classify(legacyNullResult).completeness());
    }

    // --- Rule 2: acta_publicada is true but incomplete -> INVALID --------------------------

    @Test
    void publishedWithNoGamesIsInvalid() {
        Acta acta = acta(true, true, lineups(3, 3), teams("Home", "Away"), List.of());

        ActaClassification classification = classifier.classify(acta);

        assertEquals(ActaCompleteness.INVALID, classification.completeness());
        assertTrue(classification.reason().contains("no games"));
    }

    @Test
    void publishedWithAllGamesNoDisputadoIsInvalid() {
        Acta acta = acta(true, true, lineups(3, 3), teams("Home", "Away"), List.of(notPlayedGame(), notPlayedGame()));

        ActaClassification classification = classifier.classify(acta);

        assertEquals(ActaCompleteness.INVALID, classification.completeness());
        assertTrue(classification.reason().contains("no game has a result"));
    }

    @Test
    void publishedWithEmptyHomeLineupIsInvalid() {
        Acta acta = acta(true, true, lineups(0, 3), teams("Home", "Away"), List.of(gameWithWinner("local")));

        assertEquals(ActaCompleteness.INVALID, classifier.classify(acta).completeness());
    }

    @Test
    void publishedWithEmptyAwayLineupIsInvalid() {
        Acta acta = acta(true, true, lineups(3, 0), teams("Home", "Away"), List.of(gameWithWinner("local")));

        assertEquals(ActaCompleteness.INVALID, classifier.classify(acta).completeness());
    }

    @Test
    void publishedWithNullLineupsIsInvalid() {
        Acta acta = acta(true, true, null, teams("Home", "Away"), List.of(gameWithWinner("local")));

        assertEquals(ActaCompleteness.INVALID, classifier.classify(acta).completeness());
    }

    @Test
    void publishedWithNullAbcIsHomeIsInvalid() {
        Acta acta = acta(true, null, lineups(3, 3), teams("Home", "Away"), List.of(gameWithWinner("local")));

        ActaClassification classification = classifier.classify(acta);

        assertEquals(ActaCompleteness.INVALID, classification.completeness());
        assertTrue(classification.reason().contains("abc_es_local"));
    }

    // --- Rule 3: acta_publicada is true and complete -> PLAYED ------------------------------

    @Test
    void publishedWithNoDisputadoMixedWithPlayedGamesIsPlayed() {
        Acta acta = acta(true, true, lineups(3, 3), teams("Home", "Away"),
                List.of(gameWithWinner("local"), notPlayedGame()));

        assertEquals(ActaCompleteness.PLAYED, classifier.classify(acta).completeness());
    }

    @Test
    void publishedWithTwoPlayersPerSideIsPlayed() {
        Acta acta = acta(true, true, lineups(2, 2), teams("Home", "Away"), List.of(gameWithWinner("local")));

        assertEquals(ActaCompleteness.PLAYED, classifier.classify(acta).completeness());
    }

    // --- Rule 4/5/6: legacy (acta_publicada missing) ----------------------------------------

    @Test
    void legacyWithEveryGameNoDisputadoIsPending() {
        Acta acta = acta(null, null, lineups(0, 0), null, List.of(notPlayedGame(), notPlayedGame()));

        assertEquals(ActaCompleteness.PENDING, classifier.classify(acta).completeness());
    }

    @Test
    void legacyWithNoGamesIsPending() {
        Acta acta = acta(null, null, null, null, List.of());

        assertEquals(ActaCompleteness.PENDING, classifier.classify(acta).completeness());
    }

    @Test
    void legacyWithPlayedAndNoDisputadoGamesIsPlayed() {
        Acta acta = acta(null, null, lineups(3, 3), teams("Home", "Away"),
                List.of(gameWithWinner("local"), notPlayedGame()));

        assertEquals(ActaCompleteness.PLAYED, classifier.classify(acta).completeness());
    }

    @Test
    void legacyWithPlayedAndNeitherGamesIsPartial() {
        Acta acta = acta(null, null, lineups(3, 3), teams("Home", "Away"),
                List.of(gameWithWinner("local"), noResultGame()));

        ActaClassification classification = classifier.classify(acta);

        assertEquals(ActaCompleteness.PARTIAL, classification.completeness());
        assertTrue(classification.reason().contains("1"));
    }

    // --- hasResult -----------------------------------------------------------------------

    @Test
    void hasResultRecognisesSetsOnly() {
        ActaGame game = game(List.of(new org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaSet(1, 11, 5)), null, null);
        assertTrue(ActaCompletenessClassifier.hasResult(game));
    }

    @Test
    void hasResultRecognisesWinnerOnly() {
        assertTrue(ActaCompletenessClassifier.hasResult(gameWithWinner("local")));
    }

    @Test
    void hasResultRecognisesSetsWonOnly() {
        ActaGame game = game(List.of(), new ActaScore(3, 0), null);
        assertTrue(ActaCompletenessClassifier.hasResult(game));
    }

    @Test
    void hasResultRejectsSetsWonWithBothSidesNull() {
        ActaGame game = game(List.of(), new ActaScore(null, null), null);
        assertFalse(ActaCompletenessClassifier.hasResult(game));
    }

    @Test
    void hasResultRejectsBlankWinner() {
        assertFalse(ActaCompletenessClassifier.hasResult(gameWithWinner("  ")));
    }

    @Test
    void hasResultRejectsNoDisputadoAlone() {
        assertFalse(ActaCompletenessClassifier.hasResult(notPlayedGame()));
    }

    // --- Unresolved pending fixtures -------------------------------------------------------

    @Test
    void pendingWithNullTeamsIsUnresolved() {
        Acta acta = acta(false, null, null, null, List.of());

        assertTrue(classifier.classify(acta).unresolvedPendingFixture());
    }

    @Test
    void pendingWithNullHomeNameIsUnresolved() {
        Acta acta = acta(false, null, null, teams(null, "Away"), List.of());

        assertTrue(classifier.classify(acta).unresolvedPendingFixture());
    }

    @Test
    void pendingWithBlankAwayNameIsUnresolved() {
        Acta acta = acta(false, null, null, teams("Home", "  "), List.of());

        assertTrue(classifier.classify(acta).unresolvedPendingFixture());
    }

    @Test
    void pendingWithBothNamesAndNoIdsIsResolved() {
        Acta acta = acta(false, null, null, new ActaTeams(new ActaTeam(null, "Home", null, null),
                new ActaTeam(null, "Away", null, null)), List.of());

        assertFalse(classifier.classify(acta).unresolvedPendingFixture());
    }

    @Test
    void playedWithNullTeamNamesIsNotUnresolved() {
        Acta acta = acta(true, true, lineups(3, 3), null, List.of(gameWithWinner("local")));

        ActaClassification classification = classifier.classify(acta);

        assertEquals(ActaCompleteness.PLAYED, classification.completeness());
        assertFalse(classification.unresolvedPendingFixture());
    }

    // --- classify(acta, games) overload -----------------------------------------------------

    @Test
    void classifyWithExplicitGamesIgnoresActaGames() {
        Acta acta = acta(true, true, lineups(3, 3), teams("Home", "Away"), List.of());

        ActaClassification classification = classifier.classify(acta, List.of(gameWithWinner("local")));

        assertEquals(ActaCompleteness.PLAYED, classification.completeness());
    }

    // --- ActaClassification invariant -------------------------------------------------------

    @Test
    void classificationRejectsUnresolvedOnNonPendingClass() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActaClassification(ActaCompleteness.PLAYED, "reason", true));
    }

    // --- Fixture sweep -----------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "acta_rfetm_2026_published.json, PLAYED, false",
            "acta_rfetm_2026_unpublished.json, PENDING, false",
            "acta_rfetm_2025_decided_0_0.json, PENDING, false",
            "acta_rfetm_legacy_empty.json, PENDING, false",
            "acta_bcnesa_2026_unpublished.json, PENDING, false",
            "acta_bcnesa_2026_placeholder_4_4.json, PENDING, false",
            "acta_fctt_2026_published.json, PLAYED, false",
            "acta_fctt_unpublished.json, PENDING, false",
            "acta_fctt_2026_no_team_placeholder.json, PENDING, true",
            "acta_fctt_2025_placeholder_6_0.json, PENDING, false",
            "acta_fctt_female_groupless.json, PLAYED, false",
            "acta_singles.json, PLAYED, false",
            "acta_doubles.json, PLAYED, false",
            "acta_bcnesa_xyz_home.json, PLAYED, false",
            "acta_fctt_abc_away.json, PLAYED, false",
            "acta_matchday.json, PLAYED, false",
            "acta_doubles_matchday.json, PLAYED, false"
    })
    void fixturesClassifyAsExpected(String fixtureName, ActaCompleteness expected, boolean expectedUnresolved) throws Exception {
        Acta acta = parser.parse(fixture(fixtureName));

        ActaClassification classification = classifier.classify(acta);

        assertEquals(expected, classification.completeness(), fixtureName);
        assertEquals(expectedUnresolved, classification.unresolvedPendingFixture(), fixtureName);
    }

    @Test
    void fcttSixZeroBcnesaFourFourAndRfetmDecidedZeroZeroAreAllPending() throws Exception {
        for (String fixtureName : List.of("acta_fctt_2025_placeholder_6_0.json",
                "acta_bcnesa_2026_placeholder_4_4.json", "acta_rfetm_2025_decided_0_0.json")) {
            Acta acta = parser.parse(fixture(fixtureName));
            assertEquals(ActaCompleteness.PENDING, classifier.classify(acta).completeness(), fixtureName);
        }
    }

    @Test
    void fcttNoTeamPlaceholderIsPendingAndUnresolved() throws Exception {
        Acta acta = parser.parse(fixture("acta_fctt_2026_no_team_placeholder.json"));

        ActaClassification classification = classifier.classify(acta);

        assertEquals(ActaCompleteness.PENDING, classification.completeness());
        assertTrue(classification.unresolvedPendingFixture());
    }

    // --- Test helpers ------------------------------------------------------------------------

    private static Acta acta(Boolean published, Boolean abcIsHome, ActaLineups lineups, ActaTeams teams,
            List<ActaGame> games) {
        return new Acta(null, published, null, null, null, null, null, null, null, null, null, null,
                teams, abcIsHome, null, lineups, null, games, null, null);
    }

    private static Acta withFinalResult(Acta base, ActaFinalResult finalResult) {
        return new Acta(base.matchId(), base.published(), base.federation(), base.season(), base.competition(),
                base.group(), base.round(), base.phase(), base.gender(), base.date(), base.time(), base.venue(),
                base.teams(), base.abcIsHome(), base.officials(), base.lineups(), base.doubles(), base.games(),
                finalResult, base.protested());
    }

    private static ActaTeams teams(String homeName, String awayName) {
        return new ActaTeams(new ActaTeam(null, homeName, null, null), new ActaTeam(null, awayName, null, null));
    }

    private static ActaLineups lineups(int homeCount, int awayCount) {
        return new ActaLineups(players(homeCount), players(awayCount));
    }

    private static Map<String, ActaLineupPlayer> players(int count) {
        Map<String, ActaLineupPlayer> players = new LinkedHashMap<>();
        String letters = "ABCXYZ";
        for (int i = 0; i < count; i++) {
            players.put(String.valueOf(letters.charAt(i)), new ActaLineupPlayer(null, "Player " + i, null, null));
        }
        return players;
    }

    private static ActaGame game(List<org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaSet> sets,
            ActaScore setsWon, String winner) {
        return new ActaGame(null, null, null, null, null, sets, setsWon, winner, null, null, null);
    }

    private static ActaGame gameWithWinner(String winner) {
        return game(List.of(), null, winner);
    }

    private static ActaGame noResultGame() {
        return game(List.of(), null, null);
    }

    private static ActaGame notPlayedGame() {
        return new ActaGame(null, null, null, null, null, List.of(), null, null, null, true, null);
    }

    private static Path fixture(String name) throws URISyntaxException, IOException {
        URL resource = ActaCompletenessClassifierTest.class.getResource("/actas/" + name);
        assertTrue(resource != null, "Missing test fixture " + name);
        return Path.of(resource.toURI());
    }
}
