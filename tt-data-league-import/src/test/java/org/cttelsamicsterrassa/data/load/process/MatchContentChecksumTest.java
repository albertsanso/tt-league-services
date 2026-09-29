package org.cttelsamicsterrassa.data.load.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.game.model.SetScore;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchContentChecksum;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00089: the canonical content checksum. It must be stable across generated UUIDs and child list
 * order, change whenever anything an import would write changes, distinguish {@code null} from
 * {@code ""}, and carry the {@code v1:} version prefix.
 */
class MatchContentChecksumTest {

    private static final Season SEASON = Season.of(2026);
    private static final ZonedDateTime DATE = LocalDate.of(2026, 9, 27)
            .atTime(LocalTime.NOON).atZone(Match.COMPETITION_ZONE);

    private static final UUID HOME_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID AWAY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PLAYER_1 = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PLAYER_2 = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID GAME_1 = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID GAME_2 = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID SET_1 = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID SET_2 = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID PAIR_1 = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID LINEUP_1 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private final Team homeTeam = team(HOME_ID, "HOME");
    private final Team awayTeam = team(AWAY_ID, "AWAY");
    private final PlayerSeason player1 = player(PLAYER_1, "Player One");
    private final PlayerSeason player2 = player(PLAYER_2, "Player Two");

    @Test
    void sameContentWithDifferentUuidsAndShuffledListsHasTheSameChecksum() {
        String first = MatchContentChecksum.of(content(match(b -> b), false));
        String second = MatchContentChecksum.of(content(match(b -> b), true));

        assertTrue(first.startsWith(MatchContentChecksum.PREFIX));
        assertEquals(MatchContentChecksum.PREFIX.length() + 64, first.length());
        assertEquals(first, second);
    }

    @Test
    void changingAnyHeaderFieldChangesTheChecksum() {
        String base = MatchContentChecksum.of(content(match(b -> b), false));

        List<UnaryOperator<Match.MatchBuilder>> variants = List.of(
                b -> b.source(ImportSource.BCNESA),
                b -> b.sourceFixtureId("OTHER-FIXTURE"),
                b -> b.externalId("OTHER-EXTERNAL"),
                b -> b.competition("another-competition"),
                b -> b.season(Season.of(2025)),
                b -> b.groupNumber(2),
                b -> b.round(4),
                b -> b.phase("PLAYOFF"),
                b -> b.dateTime(DATE.plusDays(1)),
                b -> b.city("another city"),
                b -> b.venue("another venue"),
                b -> b.homeTeam(awayTeam),
                b -> b.awayTeam(homeTeam),
                b -> b.winnerTeam(awayTeam),
                b -> b.refereeName("another referee"),
                b -> b.refereeLicense("another license"),
                b -> b.homeGamesWon(4),
                b -> b.awayGamesWon(3),
                b -> b.homeSetsWon(10),
                b -> b.awaySetsWon(6),
                b -> b.protested(true));

        for (UnaryOperator<Match.MatchBuilder> variant : variants) {
            String changed = MatchContentChecksum.of(content(match(variant), false));
            assertNotEquals(base, changed, "header variant must change the checksum");
        }
    }

    @Test
    void changingAChildChangesTheChecksum() {
        Match match = match(b -> b);
        String base = MatchContentChecksum.of(content(match, false));

        MatchContent changedLineup = new MatchContent(match,
                List.of(lineup(match, homeTeam, player2, "A", 1, 1500f)),
                children(match).games(),
                children(match).setScores(),
                children(match).doublesPairs());
        assertNotEquals(base, MatchContentChecksum.of(changedLineup));

        List<Game> changedGames = List.of(game(match, GAME_1, 1, 0, 3), game(match, GAME_2, 2, 1, 3));
        MatchContent changedGameScore = new MatchContent(match,
                List.of(lineup(match, homeTeam, player1, "A", 1, 1500f)),
                changedGames, List.of(), List.of());
        assertNotEquals(base, MatchContentChecksum.of(changedGameScore));

        MatchContent changedSetScore = new MatchContent(match,
                List.of(lineup(match, homeTeam, player1, "A", 1, 1500f)),
                children(match).games(),
                List.of(SetScore.builder().id(SET_1).source(ImportSource.RFETM)
                        .game(game(match, GAME_1, 1, 3, 1)).setNumber(1).homePoints(11).awayPoints(7).build(),
                        SetScore.builder().id(SET_2).source(ImportSource.RFETM)
                                .game(game(match, GAME_2, 2, 1, 3)).setNumber(1).homePoints(9).awayPoints(11).build()),
                List.of());
        assertNotEquals(base, MatchContentChecksum.of(changedSetScore));

        MatchContent changedDoublesPlayer = new MatchContent(match,
                List.of(lineup(match, homeTeam, player1, "A", 1, 1500f)),
                children(match).games(),
                List.of(),
                List.of(DoublesPair.builder().id(PAIR_1).source(ImportSource.RFETM)
                        .game(game(match, GAME_1, 1, 3, 1)).side("HOME").player(player2).build()));
        assertNotEquals(base, MatchContentChecksum.of(changedDoublesPlayer));
    }

    @Test
    void nullAndEmptyStringDiffer() {
        String withEmpty = MatchContentChecksum.of(content(match(b -> b.city("")), false));
        String withNull = MatchContentChecksum.of(content(match(b -> b.city(null)), false));

        assertNotEquals(withNull, withEmpty);
    }

    // --- fixtures ----------------------------------------------------------------------------

    private MatchContent content(Match match, boolean shuffle) {
        Children children = children(match);
        List<Lineup> lineups = List.of(lineup(match, homeTeam, player1, "A", 1, 1500f));
        if (shuffle) {
            return new MatchContent(match, lineups.reversed(), children.games().reversed(),
                    children.setScores().reversed(), children.doublesPairs().reversed());
        }
        return new MatchContent(match, lineups, children.games(), children.setScores(), children.doublesPairs());
    }

    private Children children(Match match) {
        Game first = game(match, GAME_1, 1, 3, 1);
        Game second = game(match, GAME_2, 2, 1, 3);
        return new Children(
                List.of(first, second),
                List.of(SetScore.builder().id(SET_1).source(ImportSource.RFETM).game(first)
                                .setNumber(1).homePoints(11).awayPoints(5).build(),
                        SetScore.builder().id(SET_2).source(ImportSource.RFETM).game(second)
                                .setNumber(1).homePoints(9).awayPoints(11).build()),
                List.of(DoublesPair.builder().id(PAIR_1).source(ImportSource.RFETM).game(first)
                        .side("HOME").player(player1).build()));
    }

    private Match match(UnaryOperator<Match.MatchBuilder> override) {
        Match.MatchBuilder builder = Match.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.RFETM)
                .sourceFixtureId("FIXTURE-1")
                .externalId("EXTERNAL-1")
                .competition("divisio-honor")
                .season(SEASON)
                .groupNumber(1)
                .round(3)
                .phase("REGULAR")
                .dateTime(DATE)
                .city("Barcelona")
                .venue("Hall")
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .winnerTeam(homeTeam)
                .refereeName("Referee")
                .refereeLicense("LIC-1")
                .homeGamesWon(3)
                .awayGamesWon(1)
                .homeSetsWon(9)
                .awaySetsWon(5)
                .protested(false)
                .status(MatchStatus.PLAYED);
        override.apply(builder);
        return builder.createExisting();
    }

    private Game game(Match match, UUID id, int number, int homeSets, int awaySets) {
        return Game.builder()
                .id(id)
                .source(ImportSource.RFETM)
                .match(match)
                .gameNumber(number)
                .type("INDIVIDUAL")
                .crossover("")
                .homePlayer(player1)
                .awayPlayer(player2)
                .homeSetsWon(homeSets)
                .awaySetsWon(awaySets)
                .winner(homeSets > awaySets ? player1 : player2)
                .winnerSide(homeSets > awaySets ? "HOME" : "AWAY")
                .cumulativeHomeSetsWon(homeSets)
                .cumulativeAwaySetsWon(awaySets)
                .notPlayed(false)
                .reason(null)
                .createExisting();
    }

    private Lineup lineup(Match match, Team team, PlayerSeason player, String letter, int position, Float ranking) {
        return Lineup.builder()
                .id(LINEUP_1)
                .source(ImportSource.RFETM)
                .match(match)
                .team(team)
                .letter(letter)
                .position(position)
                .player(player)
                .ranking(ranking)
                .createExisting();
    }

    private static Team team(UUID id, String name) {
        return Team.createExisting(id, ImportSource.RFETM, name, SEASON, null);
    }

    private static PlayerSeason player(UUID id, String name) {
        return PlayerSeason.createExisting(id, ImportSource.RFETM, name, "LIC-" + id, null, SEASON);
    }

    private record Children(List<Game> games, List<SetScore> setScores, List<DoublesPair> doublesPairs) {
    }
}