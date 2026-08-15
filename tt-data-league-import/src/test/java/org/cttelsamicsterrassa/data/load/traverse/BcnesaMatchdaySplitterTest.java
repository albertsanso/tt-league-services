package org.cttelsamicsterrassa.data.load.traverse;

import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaClubIndex;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaMatchdaySplitter;
import org.cttelsamicsterrassa.data.load.shared.parse.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaLineups;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaParticipant;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaTeam;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaTeams;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BcnesaMatchdaySplitterTest {

    private final BcnesaMatchdaySplitter splitter = new BcnesaMatchdaySplitter();

    @Test
    void splitsAThreeFixtureMatchdayOnTheCrossoverRestart() {
        Acta acta = acta(
                game("10", "20"),
                game("30", "40"),
                game("50", "60"));

        List<BcnesaMatchdaySplitter.Fixture> fixtures = splitter.split(acta, BcnesaClubIndex.of(Map.of()));

        assertEquals(3, fixtures.size());
        fixtures.forEach(fixture -> assertEquals(1, fixture.games().size()));
    }

    @Test
    void takesTheFirstFixturesClubsFromEquipos() {
        Acta acta = acta(game("10", "20"));

        BcnesaMatchdaySplitter.Fixture fixture = splitter.split(acta, BcnesaClubIndex.of(Map.of())).get(0);

        assertEquals("HOME CLUB", fixture.homeClubName());
        assertEquals("AWAY CLUB", fixture.awayClubName());
        assertTrue(fixture.isResolved());
    }

    @Test
    void attributesALaterFixtureFromTheClubIndex() {
        Acta acta = acta(game("10", "20"), game("30", "40"));

        BcnesaClubIndex index = BcnesaClubIndex.of(Map.of("30", "SECOND HOME", "40", "SECOND AWAY"));

        BcnesaMatchdaySplitter.Fixture second = splitter.split(acta, index).get(1);

        assertEquals("SECOND HOME", second.homeClubName());
        assertEquals("SECOND AWAY", second.awayClubName());
        assertTrue(second.isResolved());
    }

    @Test
    void leavesAFixtureUnresolvedRatherThanGuessing() {
        Acta acta = acta(game("10", "20"), game("99", "98"));

        BcnesaMatchdaySplitter.Fixture second = splitter.split(acta, BcnesaClubIndex.of(Map.of())).get(1);

        assertNull(second.homeClubName());
        assertNull(second.awayClubName());
        assertFalse(second.isResolved());
    }

    private static Acta acta(ActaGame... games) {
        ActaTeams teams = new ActaTeams(
                new ActaTeam(null, "HOME CLUB", null, null),
                new ActaTeam(null, "AWAY CLUB", null, null));
        ActaLineups lineups = new ActaLineups(Map.of(), Map.of());
        return new Acta("Federació Catalana de Tennis Taula", "2023/2024", "Preferent", 1, 1,
                null, null, null, teams, true, null, lineups, null, List.of(games), null, false);
    }

    private static ActaGame game(String homeLicense, String awayLicense) {
        return new ActaGame(1, "individual", "A vs Y",
                new ActaParticipant("A", "HOME PLAYER", homeLicense, null, null, List.of()),
                new ActaParticipant("Y", "AWAY PLAYER", awayLicense, null, null, List.of()),
                List.of(), null, null, null, null, null);
    }
}
