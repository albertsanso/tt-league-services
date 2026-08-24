package org.cttelsamicsterrassa.data.load.bcnesa.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.game.repository.DoublesPairRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.GameRepository;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaLineupPlayer;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParticipant;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@Order(BcnesaMatchImportProcessor.ORDER)
public class BcnesaMatchImportProcessor implements BcnesaMatchReportProcessor {

    /** Matches come last: they reference clubs and players. */
    public static final int ORDER = 30;

    private static final Logger LOGGER = LoggerFactory.getLogger(BcnesaMatchImportProcessor.class);

    private static final String GAME_TYPE_INDIVIDUAL = "INDIVIDUAL";
    private static final String GAME_TYPE_DOUBLES = "DOUBLES";
    private static final String SIDE_HOME = "HOME";
    private static final String SIDE_AWAY = "AWAY";

    /** Lineup letter to the position it stands for within a team. */
    private static final Map<String, Integer> POSITION_BY_LETTER = Map.of(
            "A", 1, "B", 2, "C", 3,
            "X", 1, "Y", 2, "Z", 3);

    private final TeamRepository teamRepository;
    private final PlayerSeasonRepository playerSeasonRepository;
    private final MatchRepository matchRepository;
    private final LineupRepository lineupRepository;
    private final GameRepository gameRepository;
    private final DoublesPairRepository doublesPairRepository;

    public BcnesaMatchImportProcessor(TeamRepository teamRepository,
                                      PlayerSeasonRepository playerSeasonRepository,
                                      MatchRepository matchRepository,
                                      LineupRepository lineupRepository,
                                      GameRepository gameRepository,
                                      DoublesPairRepository doublesPairRepository) {
        this.teamRepository = teamRepository;
        this.playerSeasonRepository = playerSeasonRepository;
        this.matchRepository = matchRepository;
        this.lineupRepository = lineupRepository;
        this.gameRepository = gameRepository;
        this.doublesPairRepository = doublesPairRepository;
    }

    @Override
    public void process(BcnesaMatchReportContext context) {
        Season season = context.toSeason();
        Optional<Team> homeTeam = resolveTeam(context.homeTeamName(), season, context);
        Optional<Team> awayTeam = resolveTeam(context.awayTeamName(), season, context);
        if (homeTeam.isEmpty() || awayTeam.isEmpty()) {
            return;
        }

        String competition = context.competition();
        int groupNumber = context.groupNumber();
        int round = context.round();

        if (matchRepository.findMatchByNaturalKey(competition, season, groupNumber, round,
                homeTeam.get().getId(), awayTeam.get().getId()).isPresent()) {
            LOGGER.debug("Fixture already stored for {} #{}; skipping", context.matchReportFile(), context.fixtureIndex());
            return;
        }

        Match match = buildMatch(context, season, competition, groupNumber, round, homeTeam.get(), awayTeam.get());
        matchRepository.saveMatch(match);

        SideLineup home = resolveLineup(context.games(), true, season, context);
        SideLineup away = resolveLineup(context.games(), false, season, context);

        lineupRepository.saveLineups(buildLineups(match, homeTeam.get(), home, awayTeam.get(), away));
        storeGames(context, match, home, away);
    }

    // --- match -----------------------------------------------------------------------------

    private Match buildMatch(BcnesaMatchReportContext context,
                             Season season,
                             String competition,
                             int groupNumber,
                             int round,
                             Team homeTeam,
                             Team awayTeam) {
        Acta acta = context.acta();
        int homeGamesWon = context.homeGamesWon();
        int awayGamesWon = context.awayGamesWon();

        return Match.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.BCNESA)
                .competition(competition)
                .season(season)
                .groupNumber(groupNumber)
                .round(round)
                .dateTime(toDateTime(acta))
                .city(acta.venue() != null ? acta.venue().city() : null)
                .venue(acta.venue() != null ? acta.venue().venue() : null)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .winnerTeam(resolveWinnerTeam(homeGamesWon, awayGamesWon, homeTeam, awayTeam))
                .refereeName(refereeName(acta))
                .homeGamesWon(homeGamesWon)
                .awayGamesWon(awayGamesWon)
                .homeSetsWon(context.homeSetsWon())
                .awaySetsWon(context.awaySetsWon())
                .protested(acta.wasProtested())
                .createNew();
    }

    private static ZonedDateTime toDateTime(Acta acta) {
        if (acta.date() == null) {
            return null;
        }
        LocalTime time = acta.time() != null ? acta.time() : LocalTime.MIDNIGHT;
        return acta.date().atTime(time).atZone(Match.COMPETITION_ZONE);
    }

    private static String refereeName(Acta acta) {
        if (acta.officials() == null || acta.officials().head() == null) {
            return null;
        }
        return acta.officials().head().name();
    }

    /**
     * The fixture's own {@code resultado_final} is not available - the payload's field aggregates
     * every fixture of the matchday, not this one - so the winner is always the games-won comparison,
     * with a draw left unset.
     */
    private static Team resolveWinnerTeam(int homeGamesWon, int awayGamesWon,
                                                Team homeTeam, Team awayTeam) {
        if (homeGamesWon > awayGamesWon) {
            return homeTeam;
        }
        return awayGamesWon > homeGamesWon ? awayTeam : null;
    }

    // --- lineups ---------------------------------------------------------------------------

    /**
     * Builds one side's lineup from this fixture's own singles games, keyed by lineup letter for
     * position resolution and by name for doubles-pair resolution. Unlike the RFETM importer, this
     * does not read {@code alineaciones}: it only ever covers the file's first fixture, while every
     * singles game in every fixture carries its own participant licence.
     */
    private SideLineup resolveLineup(List<ActaGame> games, boolean home, Season season,
                                     BcnesaMatchReportContext context) {
        Map<String, PlayerSeason> byLetter = new LinkedHashMap<>();
        Map<String, PlayerSeason> byName = new LinkedHashMap<>();

        for (ActaGame game : games) {
            if (game.isDoubles()) {
                continue;
            }
            ActaParticipant participant = home ? game.home() : game.away();
            if (participant == null || participant.letter() == null || participant.license() == null) {
                continue;
            }
            Optional<PlayerSeason> playerSeason = playerSeasonRepository.findPlayerSeasonBySourceLicenseAndSeason(
                    ImportSource.BCNESA, participant.license(), season);
            if (playerSeason.isEmpty()) {
                LOGGER.warn("No player registered for licence {} in {}; lineup letter {} left out",
                        participant.license(), season, participant.letter());
                continue;
            }
            byLetter.putIfAbsent(participant.letter(), playerSeason.get());
            if (participant.name() != null) {
                byName.putIfAbsent(participant.name(), playerSeason.get());
            }
        }

        return new SideLineup(byLetter, byName);
    }

    private List<Lineup> buildLineups(Match match, Team homeTeam, SideLineup home,
                                      Team awayTeam, SideLineup away) {
        List<Lineup> lineups = new ArrayList<>();
        addLineups(lineups, match, homeTeam, home);
        addLineups(lineups, match, awayTeam, away);
        return lineups;
    }

    private void addLineups(List<Lineup> lineups, Match match, Team team, SideLineup side) {
        side.byLetter().forEach((letter, player) -> {
            Integer position = POSITION_BY_LETTER.get(letter);
            if (position == null) {
                LOGGER.warn("Unknown lineup letter {} in match {}; entry left out", letter, match.getId());
                return;
            }
            lineups.add(Lineup.builder()
                    .id(UUID.randomUUID())
                    .match(match)
                    .team(team)
                    .letter(letter)
                    .position(position)
                    .player(player)
                    .ranking(null)
                    .createNew());
        });
    }

    // --- games -----------------------------------------------------------------------------

    private void storeGames(BcnesaMatchReportContext context, Match match, SideLineup home, SideLineup away) {
        List<Game> games = new ArrayList<>();
        List<DoublesPair> doublesPairs = new ArrayList<>();

        for (ActaGame actaGame : context.games()) {
            if (actaGame.number() == null) {
                LOGGER.warn("Game without a number in {}; left out", context.matchReportFile());
                continue;
            }
            Game game = buildGame(actaGame, match, home, away);
            games.add(game);
            if (actaGame.isDoubles()) {
                doublesPairs.addAll(buildDoublesPairs(actaGame, game, home, away, context));
            }
        }

        gameRepository.saveGames(games);
        doublesPairRepository.saveDoublesPairs(doublesPairs);
    }

    private Game buildGame(ActaGame actaGame, Match match, SideLineup home, SideLineup away) {
        boolean doubles = actaGame.isDoubles();
        ActaScore setsWon = actaGame.setsWon();
        ActaScore cumulative = actaGame.cumulativeScore();
        String winnerSide = toSide(actaGame.winner());

        PlayerSeason homePlayer = doubles ? null : playerOf(actaGame.home(), home);
        PlayerSeason awayPlayer = doubles ? null : playerOf(actaGame.away(), away);

        return Game.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.BCNESA)
                .match(match)
                .gameNumber(actaGame.number())
                .type(doubles ? GAME_TYPE_DOUBLES : GAME_TYPE_INDIVIDUAL)
                .crossover(actaGame.crossover() != null ? actaGame.crossover() : "")
                .homePlayer(homePlayer)
                .awayPlayer(awayPlayer)
                .homeSetsWon(setsWon != null ? setsWon.home() : null)
                .awaySetsWon(setsWon != null ? setsWon.away() : null)
                .winner(winnerOf(winnerSide, homePlayer, awayPlayer))
                .winnerSide(winnerSide)
                .cumulativeHomeSetsWon(cumulative != null && cumulative.home() != null ? cumulative.home() : 0)
                .cumulativeAwaySetsWon(cumulative != null && cumulative.away() != null ? cumulative.away() : 0)
                .notPlayed(actaGame.wasNotPlayed())
                .reason(actaGame.reason())
                .createNew();
    }

    private static String toSide(String actaWinner) {
        if (ActaGame.WINNER_HOME.equals(actaWinner)) {
            return SIDE_HOME;
        }
        return ActaGame.WINNER_AWAY.equals(actaWinner) ? SIDE_AWAY : null;
    }

    private static PlayerSeason winnerOf(String winnerSide, PlayerSeason homePlayer, PlayerSeason awayPlayer) {
        if (SIDE_HOME.equals(winnerSide)) {
            return homePlayer;
        }
        return SIDE_AWAY.equals(winnerSide) ? awayPlayer : null;
    }

    private static PlayerSeason playerOf(ActaParticipant participant, SideLineup side) {
        if (participant == null || participant.letter() == null) {
            return null;
        }
        return side.byLetter().get(participant.letter());
    }

    private PlayerSeason playerOf(ActaLineupPlayer participant, SideLineup side, Season season) {
        if (participant == null || participant.name() == null || participant.license() == null) {
            return null;
        }
        PlayerSeason player = side.byName().get(participant.name());
        if (player != null && participant.license().equals(player.getLicense())) {
            return player;
        }
        return playerSeasonRepository.findPlayerSeasonBySourceLicenseAndSeason(ImportSource.BCNESA, participant.license(), season)
                .orElse(null);
    }

    private List<DoublesPair> buildDoublesPairs(ActaGame actaGame, Game game, SideLineup home, SideLineup away,
                                                BcnesaMatchReportContext context) {
        List<DoublesPair> pairs = new ArrayList<>();
        addDoublesPair(pairs, game, SIDE_HOME, actaGame.home(), home, context);
        addDoublesPair(pairs, game, SIDE_AWAY, actaGame.away(), away, context);
        return pairs;
    }

    private void addDoublesPair(List<DoublesPair> pairs, Game game, String side, ActaParticipant participant,
                                SideLineup lineup, BcnesaMatchReportContext context) {
        if (participant == null) {
            return;
        }
        // Every doubles entry in the export names the same player twice; dedup so it produces one
        // row, not a duplicate, until the source data captures the second player.
        Set<ActaLineupPlayer> distinctPlayers = new LinkedHashSet<>(participant.doublesPlayers());
        for (ActaLineupPlayer doublesPlayer : distinctPlayers) {
            PlayerSeason player = playerOf(doublesPlayer, lineup, context.toSeason());
            if (player == null) {
                LOGGER.warn("Doubles player \"{}\" with licence {} is unavailable in fixture {} of {}; pair member left out",
                        doublesPlayer.name(), doublesPlayer.license(), side, context.fixtureIndex(), context.matchReportFile());
                continue;
            }
            pairs.add(DoublesPair.builder()
                    .id(UUID.randomUUID())
                    .game(game)
                    .side(side)
                    .player(player)
                    .build());
        }
    }

    private Optional<Team> resolveTeam(String rawTeamName, Season season,
                                                    BcnesaMatchReportContext context) {
        String name = BcnesaTeamNames.normalize(rawTeamName);
        if (name == null) {
            return Optional.empty();
        }

        Optional<Team> team = teamRepository.findTeamByNameAndSeasonAndSource(name, season, ImportSource.BCNESA);
        if (team.isEmpty()) {
            LOGGER.warn("BCNESA club {} has no entry for season {}; {} not stored", name, season, context.matchReportFile());
        }
        return team;
    }

    /**
     * One side's lineup, resolved to stored players: by lineup letter for singles and by both name
     * and licence for doubles pairs.
     */
    private record SideLineup(Map<String, PlayerSeason> byLetter, Map<String, PlayerSeason> byName) {
    }
}
