package org.cttelsamicsterrassa.data.load.parse;

import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parses the anonymised 2026-2027 example actas that exercise the incremental-import lifecycle
 * gaps (G14, G15, G17, G18): unpublished placeholders, decided-without-play results, legacy actas
 * without {@code acta_publicada}, and a published/unpublished pair sharing the same {@code id_partido}.
 */
class IncrementalActaFixturesTest {

    private final ActaParser parser = new ActaParser();

    @ParameterizedTest
    @ValueSource(strings = {
            "acta_rfetm_2026_published.json",
            "acta_rfetm_2026_unpublished.json",
            "acta_rfetm_2025_decided_0_0.json",
            "acta_rfetm_legacy_empty.json",
            "acta_bcnesa_2026_unpublished.json",
            "acta_bcnesa_2026_placeholder_4_4.json",
            "acta_fctt_2026_published.json",
            "acta_fctt_unpublished.json",
            "acta_fctt_2026_no_team_placeholder.json",
            "acta_fctt_2025_placeholder_6_0.json",
            "acta_fctt_female_groupless.json"
    })
    void everyFixtureParses(String fixtureName) throws Exception {
        assertNotNull(parser.parse(fixture(fixtureName)));
    }

    @Test
    void rfetmPublishedHasGamesAndAMatchId() throws Exception {
        Acta acta = parser.parse(fixture("acta_rfetm_2026_published.json"));

        assertTrue(acta.isPublished());
        assertNotNull(acta.matchId());
        assertFalse(acta.games().isEmpty());
    }

    @Test
    void rfetmUnpublishedHasNoGamesOrLineupsButKeepsTheReferee() throws Exception {
        Acta acta = parser.parse(fixture("acta_rfetm_2026_unpublished.json"));

        assertFalse(acta.isPublished());
        assertTrue(acta.games().isEmpty());
        assertTrue(acta.lineups().home().isEmpty());
        assertTrue(acta.lineups().away().isEmpty());
        assertNull(acta.abcIsHome());
        assertNotNull(acta.officials().head());
        assertNotNull(acta.officials().head().name());
    }

    @Test
    void rfetmDecidedZeroZeroHasEveryGameNotPlayedAndNoSetScores() throws Exception {
        Acta acta = parser.parse(fixture("acta_rfetm_2025_decided_0_0.json"));

        assertNull(acta.published());
        assertTrue(acta.games().stream().allMatch(ActaGame::wasNotPlayed));
        assertTrue(acta.games().stream().allMatch(game -> game.sets().isEmpty()));
    }

    @Test
    void legacyEmptyActaHasNoGamesOrLineups() throws Exception {
        Acta acta = parser.parse(fixture("acta_rfetm_legacy_empty.json"));

        assertNull(acta.published());
        assertTrue(acta.games().isEmpty());
        assertTrue(acta.lineups().home().isEmpty());
        assertTrue(acta.lineups().away().isEmpty());
    }

    @Test
    void bcnesaUnpublishedHasAPhaseAndBothTeamIds() throws Exception {
        Acta acta = parser.parse(fixture("acta_bcnesa_2026_unpublished.json"));

        assertFalse(acta.isPublished());
        assertNotNull(acta.phase());
        assertNotNull(acta.teams().home().rfetmId());
        assertNotNull(acta.teams().away().rfetmId());
    }

    @Test
    void bcnesaFourFourPlaceholderHasNoWinner() throws Exception {
        Acta acta = parser.parse(fixture("acta_bcnesa_2026_placeholder_4_4.json"));

        assertFalse(acta.isPublished());
        assertEquals(4, acta.finalResult().gamesWon().home());
        assertEquals(4, acta.finalResult().gamesWon().away());
        assertNull(acta.finalResult().winnerName());
    }

    @Test
    void fcttPublishedAndUnpublishedFixturesShareTheirMatchId() throws Exception {
        Acta published = parser.parse(fixture("acta_fctt_2026_published.json"));
        Acta unpublished = parser.parse(fixture("acta_fctt_unpublished.json"));

        assertEquals(published.matchId(), unpublished.matchId());
        assertTrue(published.isPublished());
        assertFalse(unpublished.isPublished());
    }

    @Test
    void fcttNoTeamPlaceholderHasNoTeamsAndGroupZero() throws Exception {
        Acta acta = parser.parse(fixture("acta_fctt_2026_no_team_placeholder.json"));

        assertFalse(acta.isPublished());
        assertNull(acta.teams().home().rfetmId());
        assertNull(acta.teams().home().name());
        assertNull(acta.teams().away().rfetmId());
        assertNull(acta.teams().away().name());
        assertEquals(0, acta.group());
    }

    @Test
    void fcttSixZeroPlaceholderNamesAWinner() throws Exception {
        Acta acta = parser.parse(fixture("acta_fctt_2025_placeholder_6_0.json"));

        assertFalse(acta.isPublished());
        assertNotNull(acta.finalResult().winnerName());
        assertEquals(6, acta.finalResult().gamesWon().home());
        assertEquals(0, acta.finalResult().gamesWon().away());
    }

    private static Path fixture(String name) throws URISyntaxException, IOException {
        URL resource = IncrementalActaFixturesTest.class.getResource("/actas/" + name);
        assertNotNull(resource, () -> "Missing test fixture " + name);
        return Path.of(resource.toURI());
    }
}
