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
    private final Team awayTeam2 = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Away Two", SEASON, null);

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

    @Test
    void isPlayedIsTrueOnlyForPlayedStatus() {
        assertEquals(true, builder().createNew().isPlayed());
        assertEquals(false, builder().status(MatchStatus.SCHEDULED).createNew().isPlayed());
    }

    @Test
    void sameNaturalKeyIgnoresResultsAndSchedule() {
        Match stored = builder().externalId("E1").homeGamesWon(5).awayGamesWon(2).winnerTeam(homeTeam)
                .createExisting();
        Match replacement = Match.builder().id(stored.getId()).source(ImportSource.RFETM).competition("preferent")
                .season(SEASON).round(1).homeTeam(homeTeam).awayTeam(awayTeam)
                .homeGamesWon(2).awayGamesWon(5).winnerTeam(awayTeam)
                .status(MatchStatus.PLAYED).createExisting();

        assertEquals(true, stored.hasSameNaturalKeyAs(replacement));
    }

    @Test
    void naturalKeyDiffersWhenAnyKeyComponentDiffers() {
        Match stored = builder().groupNumber(2).phase("REGULAR").createExisting();

        assertEquals(false, stored.hasSameNaturalKeyAs(null));
        assertEquals(false, stored.hasSameNaturalKeyAs(
                copyBuilder(stored).competition("super-divisio").createExisting()));
        assertEquals(false, stored.hasSameNaturalKeyAs(copyBuilder(stored).round(2).createExisting()));
        assertEquals(false, stored.hasSameNaturalKeyAs(copyBuilder(stored).groupNumber(3).createExisting()));
        assertEquals(false, stored.hasSameNaturalKeyAs(copyBuilder(stored).phase("PLAYOFF").createExisting()));
        assertEquals(false, stored.hasSameNaturalKeyAs(copyBuilder(stored).awayTeam(awayTeam2).createExisting()));
        assertEquals(false, stored.hasSameNaturalKeyAs(copyBuilder(stored).source(ImportSource.BCNESA).createExisting()));
        // A null group is part of the key: null vs 1 must not match.
        assertEquals(false, stored.hasSameNaturalKeyAs(copyBuilder(stored).groupNumber(null).createExisting()));
        assertEquals(true, stored.hasSameNaturalKeyAs(copyBuilder(stored).groupNumber(2).phase("REGULAR").createExisting()));
    }

    private Match.MatchBuilder copyBuilder(Match match) {
        return Match.builder().id(match.getId()).source(match.getSource()).externalId(match.getExternalId())
                .competition(match.getCompetition()).season(match.getSeason())
                .groupNumber(match.getGroupNumber()).round(match.getRound()).phase(match.getPhase())
                .dateTime(match.getDateTime()).city(match.getCity()).venue(match.getVenue())
                .homeTeam(match.getHomeTeam()).awayTeam(match.getAwayTeam())
                .refereeName(match.getRefereeName()).refereeLicense(match.getRefereeLicense())
                .protested(match.isProtested()).homeGamesWon(match.getHomeGamesWon())
                .awayGamesWon(match.getAwayGamesWon()).homeSetsWon(match.getHomeSetsWon())
                .awaySetsWon(match.getAwaySetsWon()).winnerTeam(match.getWinnerTeam())
                .status(match.getStatus());
    }

    private Match.MatchBuilder builder() {
        return Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("preferent")
                .season(SEASON).round(1).homeTeam(homeTeam).awayTeam(awayTeam);
    }
}
