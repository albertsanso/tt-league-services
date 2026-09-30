package org.cttelsamicsterrassa.data.load.rfetm.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.domain.game.model.SetScore;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.DoublesPairRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.GameRepository;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.SetScoreRepository;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaClassification;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaCompletenessClassifier;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchFixtureIdentityGuard;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchFixtureIdentityGuard.IncomingFixture;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleAction;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleOutcome;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecyclePlan;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecyclePlanner;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleSource;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleWriter;
import org.cttelsamicsterrassa.data.load.shared.preview.FixturePreview;
import org.cttelsamicsterrassa.data.load.shared.preview.PreviewChange;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaLineupPlayer;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParticipant;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaScore;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaSet;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaTeam;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@Order(RfetmMatchImportProcessor.ORDER)
public class RfetmMatchImportProcessor implements MatchContextProcessor {

    /** Matches come last: they reference clubs and players. */
    public static final int ORDER = 30;

    private static final Logger LOGGER = LoggerFactory.getLogger(RfetmMatchImportProcessor.class);

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
    private final SetScoreRepository setScoreRepository;
    private final DoublesPairRepository doublesPairRepository;
    private final ActaCompletenessClassifier classifier = new ActaCompletenessClassifier();
    private final MatchLifecycleWriter lifecycleWriter;
    private final MatchFixtureIdentityGuard identityGuard;
    private final MatchLifecyclePlanner planner = new MatchLifecyclePlanner();

    public RfetmMatchImportProcessor(TeamRepository teamRepository,
                                PlayerSeasonRepository playerSeasonRepository,
                                MatchRepository matchRepository,
                                LineupRepository lineupRepository,
                                GameRepository gameRepository,
                                SetScoreRepository setScoreRepository,
                                DoublesPairRepository doublesPairRepository) {
        this.teamRepository = teamRepository;
        this.playerSeasonRepository = playerSeasonRepository;
        this.matchRepository = matchRepository;
        this.lineupRepository = lineupRepository;
        this.gameRepository = gameRepository;
        this.setScoreRepository = setScoreRepository;
        this.doublesPairRepository = doublesPairRepository;
        this.lifecycleWriter = new MatchLifecycleWriter(matchRepository, lineupRepository, gameRepository,
                setScoreRepository, doublesPairRepository);
        this.identityGuard = new MatchFixtureIdentityGuard(matchRepository);
    }

    @Override
    public void process(MatchReportContext context) {
        ResolvedFixture resolved = resolve(context);
        if (resolved.noPayload()) {
            LOGGER.warn("No payload for {}; nothing to store", context.matchReportFile());
            return;
        }
        if (resolved.homeTeam().isEmpty()) {
            LOGGER.warn("Club {} has no entry for season {}; {} not stored",
                    context.homeTeam(), resolved.season(), context.matchReportFile());
        }
        if (resolved.awayTeam().isEmpty()) {
            LOGGER.warn("Club {} has no entry for season {}; {} not stored",
                    context.awayTeam(), resolved.season(), context.matchReportFile());
        }
        // FEAT-00086: the fixture is "seen" even when a team is unregistered or the identity guard
        // later rejects it, so snapshot reconciliation cannot report it as absent.
        context.runContext().recordSnapshotFixture(resolved.competition(), resolved.groupNumber(), null,
                resolved.round(), resolved.sourceFixtureId(),
                resolved.homeTeam().map(Team::getId).orElse(null),
                resolved.awayTeam().map(Team::getId).orElse(null));
        if (resolved.homeTeam().isEmpty() || resolved.awayTeam().isEmpty()) {
            return;
        }
        if (resolved.conflict().isPresent()) {
            recordOutcome(context, MatchLifecycleOutcome.FIXTURE_IDENTITY_CONFLICT, resolved.conflict().get());
            return;
        }
        MatchLifecycleOutcome outcome = lifecycleWriter.apply(resolved.classification(), resolved.existing(),
                lifecycleSource(context, resolved), context.runContext().amendedActaMode(),
                context.matchReportFile());
        recordOutcome(context, outcome, resolved.classification().reason());
    }

    /**
     * FEAT-00088: the read-only projection of {@link #process} for the import preview. It shares the
     * resolution step (so the natural key and fixture id cannot diverge) and the lifecycle planner,
     * but never writes, never records a snapshot fixture and never records a match outcome.
     */
    public FixturePreview preview(MatchReportContext context) {
        ResolvedFixture resolved = resolve(context);
        if (resolved.noPayload()) {
            return new FixturePreview(ImportSource.RFETM, null, null, null, 0, null, null, null, null,
                    PreviewChange.NOT_STORED, null, false, "No payload", context.matchReportFile());
        }
        if (resolved.homeTeam().isEmpty() || resolved.awayTeam().isEmpty()) {
            MatchLifecyclePlan plan = MatchLifecyclePlanner.planCreation(resolved.classification());
            return fixturePreview(resolved, PreviewChange.of(plan), null, true,
                    resolved.classification().reason());
        }
        if (resolved.conflict().isPresent()) {
            return fixturePreview(resolved, PreviewChange.IDENTITY_CONFLICT, null, false,
                    resolved.conflict().get());
        }
        MatchLifecyclePlan plan = planner.plan(resolved.classification(), resolved.existing(),
                lifecycleSource(context, resolved));
        Integer existingRound = plan.action() == MatchLifecycleAction.UPGRADE_TO_PLAYED
                ? resolved.existing().get().getRound()
                : null;
        return fixturePreview(resolved, PreviewChange.of(plan), existingRound, false,
                resolved.classification().reason());
    }

    /**
     * The shared read-only resolution of one report: classification, scope, round (recording the
     * day-folder fallback on the run context when the payload has no {@code jornada}), both team
     * lookups, the natural-key match and the identity guard. {@code process} and {@code preview} both
     * build on it so the key and fixture-id rules cannot diverge.
     */
    private ResolvedFixture resolve(MatchReportContext context) {
        Acta acta = context.acta();
        if (acta == null) {
            return ResolvedFixture.noPayloadFixture();
        }
        ActaClassification classification = classifier.classify(acta);
        Season season = context.toSeason();
        String competition = context.competition();
        int groupNumber = acta.group() != null ? acta.group() : 0;
        int round = resolveRound(acta, context);
        String homeTeamName = teamName(context.homeTeam(), homeTeam(context));
        String awayTeamName = teamName(context.awayTeam(), awayTeam(context));
        Optional<Team> homeTeam = teamRepository.findTeamByNameAndSeasonAndSource(homeTeamName, season,
                ImportSource.RFETM);
        Optional<Team> awayTeam = teamRepository.findTeamByNameAndSeasonAndSource(awayTeamName, season,
                ImportSource.RFETM);
        Optional<Match> existing = Optional.empty();
        Optional<String> conflict = Optional.empty();
        if (homeTeam.isPresent() && awayTeam.isPresent()) {
            existing = matchRepository.findMatchByNaturalKey(competition, season, groupNumber, round, null,
                    homeTeam.get().getId(), awayTeam.get().getId());
            conflict = identityGuard.conflict(
                    new IncomingFixture(ImportSource.RFETM, acta.matchId(), competition, season, groupNumber,
                            round, null),
                    existing);
        }
        return new ResolvedFixture(acta, classification, season, competition, groupNumber, round,
                acta.matchId(), homeTeamName, awayTeamName, homeTeam, awayTeam, existing, conflict,
                context.matchReportFile(), false);
    }

    private RfetmLifecycleSource lifecycleSource(MatchReportContext context, ResolvedFixture resolved) {
        return new RfetmLifecycleSource(context, resolved.acta(), resolved.season(), resolved.competition(),
                resolved.groupNumber(), resolved.round(), resolved.homeTeam().orElseThrow(),
                resolved.awayTeam().orElseThrow());
    }

    private FixturePreview fixturePreview(ResolvedFixture resolved, PreviewChange change, Integer existingRound,
                                          boolean teamsPendingRegistration, String reason) {
        return new FixturePreview(ImportSource.RFETM, resolved.competition(), resolved.groupNumber(), null,
                resolved.round(), resolved.sourceFixtureId(), resolved.homeTeamName(), resolved.awayTeamName(),
                resolved.classification(), change, existingRound, teamsPendingRegistration, reason,
                resolved.location());
    }

    private void recordOutcome(MatchReportContext context, MatchLifecycleOutcome outcome,
                               ActaClassification classification) {
        recordOutcome(context, outcome, classification.reason());
    }

    private void recordOutcome(MatchReportContext context, MatchLifecycleOutcome outcome, String reason) {
        String effectiveReason = outcome.amendmentReason() != null ? outcome.amendmentReason() : reason;
        context.runContext().recordMatchOutcome(outcome, getClass().getSimpleName(),
                context.matchReportFile(), effectiveReason);
        if (outcome.isReportable()) {
            LOGGER.warn("Match lifecycle {} for {}: {}", outcome, context.matchReportFile(), effectiveReason);
        }
    }

    /** The read-only result of resolving one RFETM report, shared by {@code process} and {@code preview}. */
    private record ResolvedFixture(
            Acta acta,
            ActaClassification classification,
            Season season,
            String competition,
            int groupNumber,
            int round,
            String sourceFixtureId,
            String homeTeamName,
            String awayTeamName,
            Optional<Team> homeTeam,
            Optional<Team> awayTeam,
            Optional<Match> existing,
            Optional<String> conflict,
            java.nio.file.Path location,
            boolean noPayload) {

        private static ResolvedFixture noPayloadFixture() {
            return new ResolvedFixture(null, null, null, null, 0, 0, null, null, null,
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), null, true);
        }
    }

    /**
     * The RFETM callbacks of the shared match lifecycle: the scheduled header never reads
     * {@code resultado_final}, so an unpublished acta can only ever produce a SCHEDULED match.
     */
    private final class RfetmLifecycleSource implements MatchLifecycleSource {

        private final MatchReportContext context;
        private final Acta acta;
        private final Season season;
        private final String competition;
        private final int groupNumber;
        private final int round;
        private final Team homeTeam;
        private final Team awayTeam;

        private RfetmLifecycleSource(MatchReportContext context,
                                     Acta acta,
                                     Season season,
                                     String competition,
                                     int groupNumber,
                                     int round,
                                     Team homeTeam,
                                     Team awayTeam) {
            this.context = context;
            this.acta = acta;
            this.season = season;
            this.competition = competition;
            this.groupNumber = groupNumber;
            this.round = round;
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
        }

        @Override
        public Match buildScheduledMatch(UUID id) {
            return Match.builder()
                    .id(id)
                    .source(ImportSource.RFETM)
                    .sourceFixtureId(acta.matchId())
                    .competition(competition)
                    .season(season)
                    .groupNumber(groupNumber)
                    .round(round)
                    .dateTime(toDateTime(acta))
                    .city(acta.venue() != null ? acta.venue().city() : null)
                    .venue(acta.venue() != null ? acta.venue().venue() : null)
                    .homeTeam(homeTeam)
                    .awayTeam(awayTeam)
                    .refereeName(refereeName(acta))
                    .protested(acta.wasProtested())
                    .status(MatchStatus.SCHEDULED)
                    .createNew();
        }

        @Override
        public MatchContent buildPlayedContent(UUID id, boolean existing) {
            Match match = buildPlayedMatch(id, existing);
            SideLineup home = resolveLineup(acta.lineups() != null ? acta.lineups().home() : Map.of(),
                    season, context);
            SideLineup away = resolveLineup(acta.lineups() != null ? acta.lineups().away() : Map.of(),
                    season, context);
            List<Lineup> lineups = buildLineups(match, homeTeam, home, awayTeam, away);
            BuiltGames built = buildGames(context, acta, match, home, away);
            return new MatchContent(match, lineups, built.games(), built.setScores(), built.doublesPairs());
        }

        private Match buildPlayedMatch(UUID id, boolean existing) {
            ActaScore gamesWon = acta.finalResult() != null ? acta.finalResult().gamesWon() : null;
            ActaScore setsWon = acta.finalResult() != null ? acta.finalResult().setsWon() : null;

            Match.MatchBuilder builder = Match.builder()
                    .id(id)
                    .source(ImportSource.RFETM)
                    .sourceFixtureId(acta.matchId())
                    .competition(competition)
                    .season(season)
                    .groupNumber(groupNumber)
                    .round(round)
                    .dateTime(toDateTime(acta))
                    .city(acta.venue() != null ? acta.venue().city() : null)
                    .venue(acta.venue() != null ? acta.venue().venue() : null)
                    .homeTeam(homeTeam)
                    .awayTeam(awayTeam)
                    .winnerTeam(resolveWinnerTeam(acta, homeTeam, awayTeam, context))
                    .refereeName(refereeName(acta))
                    .homeGamesWon(gamesWon != null ? gamesWon.home() : null)
                    .awayGamesWon(gamesWon != null ? gamesWon.away() : null)
                    .homeSetsWon(setsWon != null ? setsWon.home() : null)
                    .awaySetsWon(setsWon != null ? setsWon.away() : null)
                    .protested(acta.wasProtested())
                    .status(MatchStatus.PLAYED);
            return existing ? builder.createExisting() : builder.createNew();
        }
    }

    /** Games, set scores and doubles pairs built from one acta, not yet saved. */
    private record BuiltGames(List<Game> games, List<SetScore> setScores, List<DoublesPair> doublesPairs) {
    }

    // --- match -----------------------------------------------------------------------------

    /**
     * The round is taken from the payload's {@code jornada} field when present; the day folder is
     * only a fallback for the reports where that field is missing.
     */
    private int resolveRound(Acta acta, MatchReportContext context) {
        if (acta.round() != null) {
            return acta.round();
        }
        LOGGER.warn("No jornada in payload for {}; using the day folder {}",
                context.matchReportFile(), context.day());
        context.runContext().recordRoundFallback(getClass().getSimpleName(), context.matchReportFile(),
                "No jornada in payload; round " + context.round() + " taken from the day folder "
                        + context.day());
        return context.round();
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
     * The payload names the winner by club name, which is ambiguous on its own. It is only trusted
     * when it matches one of this report's two team names; otherwise the games score decides, and a
     * draw leaves the winner unset.
     */
    private Team resolveWinnerTeam(Acta acta,
                                         Team homeTeam,
                                         Team awayTeam,
                                         MatchReportContext context) {
        if (acta.finalResult() == null) {
            return null;
        }

        String winnerName = acta.finalResult().winnerName();
        if (winnerName != null && acta.teams() != null) {
            ActaTeam home = acta.teams().home();
            ActaTeam away = acta.teams().away();
            if (home != null && winnerName.equals(home.name())) {
                return homeTeam;
            }
            if (away != null && winnerName.equals(away.name())) {
                return awayTeam;
            }
            LOGGER.debug("Winner \"{}\" matches neither team in {}; falling back to the score",
                    winnerName, context.matchReportFile());
        }

        ActaScore gamesWon = acta.finalResult().gamesWon();
        if (gamesWon == null || gamesWon.home() == null || gamesWon.away() == null) {
            return null;
        }
        if (gamesWon.home() > gamesWon.away()) {
            return homeTeam;
        }
        return gamesWon.away() > gamesWon.home() ? awayTeam : null;
    }

    // --- lineups ---------------------------------------------------------------------------

    private SideLineup resolveLineup(Map<String, ActaLineupPlayer> letters,
                                     Season season,
                                     MatchReportContext context) {
        Map<String, PlayerSeason> byLetter = new LinkedHashMap<>();
        Map<String, PlayerSeason> byName = new LinkedHashMap<>();
        Map<String, Double> rankingByLetter = new LinkedHashMap<>();

        letters.forEach((letter, player) -> {
            if (player == null || player.license() == null) {
                LOGGER.warn("Lineup letter {} has no licence in {}", letter, context.matchReportFile());
                return;
            }
            Optional<PlayerSeason> playerSeason =
                    playerSeasonRepository.findPlayerSeasonBySourceLicenseAndSeason(ImportSource.RFETM, player.license(), season);
            if (playerSeason.isEmpty()) {
                LOGGER.warn("No player registered for licence {} in {}; lineup letter {} left out",
                        player.license(), season, letter);
                return;
            }
            byLetter.put(letter, playerSeason.get());
            rankingByLetter.put(letter, player.ranking());
            if (player.name() != null) {
                byName.put(player.name(), playerSeason.get());
            }
        });

        return new SideLineup(byLetter, byName, rankingByLetter);
    }

    private List<Lineup> buildLineups(Match match,
                                      Team homeTeam,
                                      SideLineup home,
                                      Team awayTeam,
                                      SideLineup away) {
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
            Double ranking = side.rankingByLetter().get(letter);
            lineups.add(Lineup.builder()
                    .id(UUID.randomUUID())
                    .match(match)
                    .team(team)
                    .letter(letter)
                    .position(position)
                    .player(player)
                    .ranking(ranking != null ? ranking.floatValue() : null)
                    .createNew());
        });
    }

    // --- games -----------------------------------------------------------------------------

    private BuiltGames buildGames(MatchReportContext context,
                                  Acta acta,
                                  Match match,
                                  SideLineup home,
                                  SideLineup away) {
        List<Game> games = new ArrayList<>();
        List<SetScore> setScores = new ArrayList<>();
        List<DoublesPair> doublesPairs = new ArrayList<>();

        for (ActaGame actaGame : acta.games()) {
            if (actaGame.number() == null) {
                LOGGER.warn("Game without a number in {}; left out", context.matchReportFile());
                continue;
            }
            Game game = buildGame(actaGame, match, home, away, context.toSeason(), context);
            games.add(game);
            setScores.addAll(buildSetScores(actaGame, game));
            if (actaGame.isDoubles()) {
                doublesPairs.addAll(buildDoublesPairs(actaGame, game, home, away, context));
            }
        }

        return new BuiltGames(games, setScores, doublesPairs);
    }

    private Game buildGame(ActaGame actaGame, Match match, SideLineup home, SideLineup away, Season season,
                           MatchReportContext context) {
        boolean doubles = actaGame.isDoubles();
        ActaScore setsWon = actaGame.setsWon();
        ActaScore cumulative = actaGame.cumulativeScore();
        String winnerSide = toSide(actaGame.winner());

        PlayerSeason homePlayer = doubles ? null : playerOf(actaGame.home(), home, season, match.getHomeTeam(), context);
        PlayerSeason awayPlayer = doubles ? null : playerOf(actaGame.away(), away, season, match.getAwayTeam(), context);

        return Game.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.RFETM)
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

    /**
     * The lineup letter names the player declared for that slot, but the actual participant can
     * differ (an undeclared substitute). The participant's own licence, when the payload carries
     * one, is authoritative over the letter; lacking a licence, a name mismatch against the
     * declared player is resolved against the team's known roster for the season.
     */
    private PlayerSeason playerOf(ActaParticipant participant, SideLineup side, Season season, Team team,
                                  MatchReportContext context) {
        if (participant == null || participant.letter() == null) {
            return null;
        }
        PlayerSeason lineupPlayer = side.byLetter().get(participant.letter());
        if (participant.license() != null) {
            if (lineupPlayer != null && participant.license().equals(lineupPlayer.getLicense())) {
                return lineupPlayer;
            }
            PlayerSeason byLicense = playerSeasonRepository
                    .findPlayerSeasonBySourceLicenseAndSeason(ImportSource.RFETM, participant.license(), season)
                    .orElse(null);
            if (byLicense == null) {
                LOGGER.warn("Participant \"{}\" (licence {}) at letter {} could not be resolved in {}; game left unattributed",
                        participant.name(), participant.license(), participant.letter(), context.matchReportFile());
            }
            return byLicense;
        }
        if (participant.name() == null || participant.name().equals(nameOf(lineupPlayer))) {
            return lineupPlayer;
        }
        PlayerSeason byRosterName = findPlayerSeasonByNameInTeamRoster(participant.name(), team, season);
        if (byRosterName == null) {
            LOGGER.warn("Participant \"{}\" at letter {} does not match the declared player \"{}\" in {}, "
                            + "and no unambiguous roster match was found; game left unattributed",
                    participant.name(), participant.letter(), nameOf(lineupPlayer), context.matchReportFile());
            return null;
        }
        return byRosterName;
    }

    private static String nameOf(PlayerSeason player) {
        return player != null ? player.getName() : null;
    }

    private PlayerSeason findPlayerSeasonByNameInTeamRoster(String name, Team team, Season season) {
        if (team == null) {
            return null;
        }
        List<PlayerSeason> matches = playerSeasonRepository
                .findAllPlayerSeasonsByTeamIdsAndSource(List.of(team.getId()), ImportSource.RFETM)
                .stream()
                .filter(playerSeason -> season.equals(playerSeason.getSeason()))
                .filter(playerSeason -> name.equals(playerSeason.getName()))
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private PlayerSeason playerOf(ActaLineupPlayer participant, SideLineup side, Season season) {
        if (participant == null || participant.name() == null || participant.license() == null) {
            return null;
        }
        PlayerSeason player = side.byName().get(participant.name());
        if (player != null && participant.license().equals(player.getLicense())) {
            return player;
        }
        return playerSeasonRepository.findPlayerSeasonBySourceLicenseAndSeason(ImportSource.RFETM, participant.license(), season)
                .orElse(null);
    }

    private static List<SetScore> buildSetScores(ActaGame actaGame, Game game) {
        List<SetScore> setScores = new ArrayList<>();
        for (ActaSet actaSet : actaGame.sets()) {
            if (actaSet.number() == null || actaSet.homePoints() == null || actaSet.awayPoints() == null) {
                continue;
            }
            setScores.add(SetScore.builder()
                    .id(UUID.randomUUID())
                    .game(game)
                    .setNumber(actaSet.number())
                    .homePoints(actaSet.homePoints())
                    .awayPoints(actaSet.awayPoints())
                    .build());
        }
        return setScores;
    }

    private List<DoublesPair> buildDoublesPairs(ActaGame actaGame,
                                                Game game,
                                                SideLineup home,
                                                SideLineup away,
                                                MatchReportContext context) {
        List<DoublesPair> pairs = new ArrayList<>();
        addDoublesPair(pairs, game, SIDE_HOME, actaGame.home(), home, context);
        addDoublesPair(pairs, game, SIDE_AWAY, actaGame.away(), away, context);
        return pairs;
    }

    private void addDoublesPair(List<DoublesPair> pairs,
                                Game game,
                                String side,
                                ActaParticipant participant,
                                SideLineup lineup,
                                MatchReportContext context) {
        if (participant == null) {
            return;
        }
        Set<UUID> pairMembers = new HashSet<>();
        for (ActaLineupPlayer doublesPlayer : participant.doublesPlayers()) {
            PlayerSeason player = playerOf(doublesPlayer, lineup, context.toSeason());
            if (player == null) {
                LOGGER.warn("Doubles player \"{}\" with licence {} is unavailable in {}; pair member left out",
                        doublesPlayer.name(), doublesPlayer.license(), side, context.matchReportFile());
                continue;
            }
            // A pair naming the same player twice would break the (game, side, player, source)
            // unique key, so the repeated member is left out.
            if (!pairMembers.add(player.getId())) {
                LOGGER.warn("Doubles player \"{}\" with licence {} is listed twice on the {} side in {}; "
                        + "duplicate pair member left out", doublesPlayer.name(), doublesPlayer.license(), side,
                        context.matchReportFile());
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

    // --- clubs -----------------------------------------------------------------------------

    /** The lookup name of one side: the payload team name when present, else the RFETM club key's name. */
    private static String teamName(RfetmClubKey key, ActaTeam team) {
        return team != null && team.name() != null && !team.name().isBlank() ? team.name() : key.name();
    }

    private static ActaTeam homeTeam(MatchReportContext context) {
        return context.acta() == null || context.acta().teams() == null ? null : context.acta().teams().home();
    }

    private static ActaTeam awayTeam(MatchReportContext context) {
        return context.acta() == null || context.acta().teams() == null ? null : context.acta().teams().away();
    }

    /**
     * One side's lineup, resolved to stored players: by lineup letter for singles and by both name
     * and licence for doubles pairs.
     */
    private record SideLineup(Map<String, PlayerSeason> byLetter,
                              Map<String, PlayerSeason> byName,
                              Map<String, Double> rankingByLetter) {
    }
}
