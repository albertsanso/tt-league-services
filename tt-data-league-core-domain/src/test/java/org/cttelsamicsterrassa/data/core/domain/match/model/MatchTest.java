package org.cttelsamicsterrassa.data.core.domain.match.model;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchTest {

    private static final Season SEASON = Season.of(2025);
    private final Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Home", SEASON, null);
    private final Team awayTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Away", SEASON, null);

    @Test
    void builderDefaultsStatusToPlayed() {
        Match match = builder().createNew();

        assertEquals(MatchStatus.PLAYED, match.getStatus());
    }

    @Test
    void scheduledMatchWithNoResultsBuildsViaCreateNewAndCreateExisting() {
        Match created = builder().status(MatchStatus.SCHEDULED).createNew();
        Match existing = builder().status(MatchStatus.SCHEDULED).createExisting();

        assertEquals(MatchStatus.SCHEDULED, created.getStatus());
        assertEquals(MatchStatus.SCHEDULED, existing.getStatus());
    }

    @Test
    void nullStatusIsRejected() {
        assertThrows(NullPointerException.class, () -> builder().status(null).createNew());
    }

    @Test
    void scheduledMatchWithAWinnerIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> builder().status(MatchStatus.SCHEDULED).winnerTeam(homeTeam).createNew());
    }

    @Test
    void scheduledMatchWithAnyNonNullGamesOrSetsWonIsRejectedIncludingZero() {
        assertThrows(IllegalArgumentException.class,
                () -> builder().status(MatchStatus.SCHEDULED).homeGamesWon(0).createNew());
        assertThrows(IllegalArgumentException.class,
                () -> builder().status(MatchStatus.SCHEDULED).awayGamesWon(0).createNew());
        assertThrows(IllegalArgumentException.class,
                () -> builder().status(MatchStatus.SCHEDULED).homeSetsWon(0).createNew());
        assertThrows(IllegalArgumentException.class,
                () -> builder().status(MatchStatus.SCHEDULED).awaySetsWon(0).createNew());
    }

    @Test
    void playedMatchWithANullWinnerIsStillAcceptedAsATie() {
        Match tie = builder().status(MatchStatus.PLAYED).winnerTeam(null)
                .homeGamesWon(3).awayGamesWon(3).createNew();

        assertEquals(MatchStatus.PLAYED, tie.getStatus());
    }

    private Match.MatchBuilder builder() {
        return Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("preferent")
                .season(SEASON).round(1).homeTeam(homeTeam).awayTeam(awayTeam);
    }
}
