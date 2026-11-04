package org.cttelsamicsterrassa.data.load.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchFixtureIdentityGuard;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchFixtureIdentityGuard.IncomingFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rule-table tests for the shared {@link MatchFixtureIdentityGuard} (FEAT-00085) against the
 * in-memory match repository: {@code id_partido} is only a consistency check over the natural key.
 */
class MatchFixtureIdentityGuardTest {

    private static final Season SEASON = Season.of(2026);
    private static final String COMPETITION = "divisio-honor-masculino";

    private InMemoryRepositories.Matches matches;
    private MatchFixtureIdentityGuard guard;
    private Team homeTeam;
    private Team awayTeam;

    @BeforeEach
    void setUp() {
        matches = new InMemoryRepositories.Matches();
        guard = new MatchFixtureIdentityGuard(matches);
        homeTeam = Team.createNew(ImportSource.RFETM, "HOME", SEASON, null);
        awayTeam = Team.createNew(ImportSource.RFETM, "AWAY", SEASON, null);
    }

    @Test
    void nullIncomingFixtureIdIsNoConflictAndSkipsTheLookup() {
        store("X", 3);

        assertTrue(guard.conflict(incoming(null, 4), Optional.empty()).isEmpty());
    }

    @Test
    void sameMatchFoundByBothLookupsIsNoConflict() {
        UUID storedId = store("X", 3);
        Optional<Match> naturalKey = naturalKey(3);

        assertTrue(guard.conflict(incoming("X", 3), naturalKey).isEmpty());
        assertEquals(storedId, naturalKey.orElseThrow().getId());
    }

    @Test
    void naturalKeyMatchWithNullStoredFixtureIdIsNoConflict() {
        store(null, 3);

        assertTrue(guard.conflict(incoming("X", 3), naturalKey(3)).isEmpty());
        assertNull(matches.saved.getFirst().getSourceFixtureId());
    }

    @Test
    void nothingStoredIsNoConflict() {
        assertTrue(guard.conflict(incoming("X", 3), Optional.empty()).isEmpty());
    }

    @Test
    void storedByFixtureIdWithEmptyNaturalKeyConflictsAndNamesBothRounds() {
        UUID storedId = store("X", 3);

        Optional<String> conflict = guard.conflict(incoming("X", 4), naturalKey(4));

        String reason = conflict.orElseThrow();
        assertTrue(reason.contains("X"), reason);
        assertTrue(reason.contains(storedId.toString()), reason);
        assertTrue(reason.contains("round 3"), reason);
        assertTrue(reason.contains("round 4"), reason);
        assertTrue(reason.endsWith("not stored to avoid a duplicate fixture"), reason);
    }

    @Test
    void twoDifferentStoredMatchesDisagreeAndBothIdsAreReported() {
        UUID byFixtureId = store("X", 3);
        UUID byNaturalKey = store(null, 4);

        Optional<String> conflict = guard.conflict(incoming("X", 4), naturalKey(4));

        String reason = conflict.orElseThrow();
        assertTrue(reason.contains(byFixtureId.toString()), reason);
        assertTrue(reason.contains(byNaturalKey.toString()), reason);
        assertTrue(reason.endsWith("not stored to avoid a duplicate fixture"), reason);
    }

    @Test
    void naturalKeyMatchWithDifferentStoredFixtureIdConflictsAndIsNeverRewritten() {
        UUID storedId = store("Y", 3);

        Optional<String> conflict = guard.conflict(incoming("X", 3), naturalKey(3));

        String reason = conflict.orElseThrow();
        assertTrue(reason.contains("X"), reason);
        assertTrue(reason.contains("Y"), reason);
        assertTrue(reason.contains(storedId.toString()), reason);
        assertEquals("Y", matches.saved.getFirst().getSourceFixtureId());
        assertEquals(1, matches.saved.size());
    }

    @Test
    void sameFixtureIdUnderAnotherSourceIsNoConflict() {
        store("X", 3);
        IncomingFixture otherSource = new IncomingFixture(ImportSource.FCTT, "X", COMPETITION, SEASON, 0, 4, null);

        assertTrue(guard.conflict(otherSource, Optional.empty()).isEmpty());
    }

    @Test
    void incomingFixtureRequiresSourceCompetitionAndSeason() {
        assertThrows(NullPointerException.class,
                () -> new IncomingFixture(null, "X", COMPETITION, SEASON, 0, 3, null));
        assertThrows(NullPointerException.class,
                () -> new IncomingFixture(ImportSource.RFETM, "X", null, SEASON, 0, 3, null));
        assertThrows(NullPointerException.class,
                () -> new IncomingFixture(ImportSource.RFETM, "X", COMPETITION, null, 0, 3, null));
    }

    // --- helpers -------------------------------------------------------------------------------

    private IncomingFixture incoming(String sourceFixtureId, int round) {
        return new IncomingFixture(ImportSource.RFETM, sourceFixtureId, COMPETITION, SEASON, 0, round, null);
    }

    private Optional<Match> naturalKey(int round) {
        return matches.findMatchByNaturalKey(COMPETITION, SEASON, 0, round, null,
                homeTeam.getId(), awayTeam.getId());
    }

    private UUID store(String sourceFixtureId, int round) {
        UUID id = UUID.randomUUID();
        matches.saveMatch(Match.builder()
                .id(id)
                .source(ImportSource.RFETM)
                .sourceFixtureId(sourceFixtureId)
                .competition(COMPETITION)
                .season(SEASON)
                .groupNumber(0)
                .round(round)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .status(MatchStatus.SCHEDULED)
                .createNew());
        return id;
    }
}
