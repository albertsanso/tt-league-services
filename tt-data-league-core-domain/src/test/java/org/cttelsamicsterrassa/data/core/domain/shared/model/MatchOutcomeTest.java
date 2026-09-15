package org.cttelsamicsterrassa.data.core.domain.shared.model;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchOutcomeTest {

    private static final Season SEASON = Season.of(2025);
    private final Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Home", SEASON, null);
    private final Team awayTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Away", SEASON, null);

    @Test
    void teamOutcomeIsWinForTheWinningTeamAndLossForTheOther() {
        Match match = matchWithWinner("super-divisio-masculino", homeTeam);

        assertEquals(Optional.of(MatchOutcome.WIN), MatchOutcome.teamOutcome(match, homeTeam.getId()));
        assertEquals(Optional.of(MatchOutcome.LOSS), MatchOutcome.teamOutcome(match, awayTeam.getId()));
    }

    @Test
    void teamOutcomeIsDrawForATieInATieEligibleCompetition() {
        Match tie = matchWithWinner("super-divisio-femenino", null);

        assertEquals(Optional.of(MatchOutcome.DRAW), MatchOutcome.teamOutcome(tie, homeTeam.getId()));
        assertEquals(Optional.of(MatchOutcome.DRAW), MatchOutcome.teamOutcome(tie, awayTeam.getId()));
    }

    @Test
    void teamOutcomeIsEmptyForATieOutsideTheTieEligibleCompetitions() {
        Match tie = matchWithWinner("preferent", null);

        assertTrue(MatchOutcome.teamOutcome(tie, homeTeam.getId()).isEmpty());
    }

    @Test
    void teamOutcomeIsEmptyWhenThePerspectiveTeamIsUnknown() {
        Match match = matchWithWinner("super-divisio-masculino", homeTeam);

        assertTrue(MatchOutcome.teamOutcome(match, null).isEmpty());
    }

    @Test
    void playerOutcomeIsWinOrLossJustLikeTeamOutcomeWhenThereIsAWinner() {
        Match match = matchWithWinner("preferent", awayTeam);

        assertEquals(Optional.of(MatchOutcome.LOSS), MatchOutcome.playerOutcome(match, homeTeam.getId()));
        assertEquals(Optional.of(MatchOutcome.WIN), MatchOutcome.playerOutcome(match, awayTeam.getId()));
    }

    @Test
    void playerOutcomeIsAlwaysEmptyForATieEvenInATieEligibleCompetition() {
        Match tie = matchWithWinner("super-divisio-masculino", null);

        assertTrue(MatchOutcome.playerOutcome(tie, homeTeam.getId()).isEmpty());
        assertTrue(MatchOutcome.playerOutcome(tie, awayTeam.getId()).isEmpty());
    }

    private Match matchWithWinner(String competition, Team winner) {
        return Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition(competition)
                .season(SEASON).round(1).homeTeam(homeTeam).awayTeam(awayTeam).winnerTeam(winner)
                .homeGamesWon(3).awayGamesWon(3).createExisting();
    }
}
