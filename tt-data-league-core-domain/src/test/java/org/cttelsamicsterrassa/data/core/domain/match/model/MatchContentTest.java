package org.cttelsamicsterrassa.data.core.domain.match.model;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.game.model.SetScore;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchContentTest {

    private static final Season SEASON = Season.of(2025);
    private final UUID matchId = UUID.randomUUID();
    private final Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Home", SEASON, null);
    private final Team awayTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Away", SEASON, null);
    private final PlayerSeason player = PlayerSeason.createExisting(
            UUID.randomUUID(), ImportSource.RFETM, "Anna", "1", null, SEASON);

    @Test
    void acceptsConsistentContent() {
        Match match = playedMatch(matchId);
        Game game = game(match, 1);
        MatchContent content = new MatchContent(match,
                List.of(lineup(match, homeTeam)), List.of(game),
                List.of(setScore(game, 1)), List.of(doublesPair(game)));

        assertEquals(matchId, content.match().getId());
        assertEquals(1, content.games().size());
    }

    @Test
    void rejectsAScheduledMatch() {
        Match scheduled = Match.builder().id(matchId).source(ImportSource.RFETM).competition("preferent")
                .season(SEASON).round(1).homeTeam(homeTeam).awayTeam(awayTeam)
                .status(MatchStatus.SCHEDULED).createExisting();

        assertThrows(IllegalArgumentException.class,
                () -> new MatchContent(scheduled, List.of(), List.of(), List.of(), List.of()));
    }

    @Test
    void rejectsAChildOfAnotherMatch() {
        Match match = playedMatch(matchId);
        Match other = playedMatch(UUID.randomUUID());

        IllegalArgumentException lineupFailure = assertThrows(IllegalArgumentException.class,
                () -> new MatchContent(match, List.of(lineup(other, homeTeam)),
                        List.of(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchContent(match, List.of(), List.of(game(other, 1)),
                        List.of(), List.of()));
        assertEquals(true, lineupFailure.getMessage().contains("lineup"));
    }

    @Test
    void rejectsAChildWhoseGameIsNotPartOfTheContent() {
        Match match = playedMatch(matchId);
        Game inside = game(match, 1);
        Game outside = game(match, 2);

        assertThrows(IllegalArgumentException.class,
                () -> new MatchContent(match, List.of(), List.of(inside),
                        List.of(setScore(outside, 1)), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchContent(match, List.of(), List.of(inside),
                        List.of(), List.of(doublesPair(outside))));
    }

    @Test
    void rejectsNullListsAndExposesUnmodifiableCopies() {
        Match match = playedMatch(matchId);

        assertThrows(NullPointerException.class,
                () -> new MatchContent(match, null, List.of(), List.of(), List.of()));
        assertThrows(NullPointerException.class,
                () -> new MatchContent(null, List.of(), List.of(), List.of(), List.of()));

        MatchContent content = new MatchContent(match, List.of(), List.of(), List.of(), List.of());
        assertThrows(UnsupportedOperationException.class, () -> content.lineups().add(lineup(match, homeTeam)));
    }

    private Match playedMatch(UUID id) {
        return Match.builder().id(id).source(ImportSource.RFETM).competition("preferent")
                .season(SEASON).round(1).homeTeam(homeTeam).awayTeam(awayTeam)
                .homeGamesWon(5).awayGamesWon(2).winnerTeam(homeTeam)
                .status(MatchStatus.PLAYED).createExisting();
    }

    private Lineup lineup(Match match, Team team) {
        return Lineup.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).match(match)
                .team(team).letter("A").position(1).player(player).createExisting();
    }

    private Game game(Match match, int number) {
        return Game.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).match(match)
                .gameNumber(number).type("INDIVIDUAL").crossover("").winnerSide("HOME")
                .homeSetsWon(3).awaySetsWon(1).notPlayed(false).createExisting();
    }

    private SetScore setScore(Game game, int setNumber) {
        return SetScore.builder().id(UUID.randomUUID()).game(game).setNumber(setNumber)
                .homePoints(11).awayPoints(9).build();
    }

    private DoublesPair doublesPair(Game game) {
        return DoublesPair.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .game(game).side("HOME").player(player).build();
    }
}
