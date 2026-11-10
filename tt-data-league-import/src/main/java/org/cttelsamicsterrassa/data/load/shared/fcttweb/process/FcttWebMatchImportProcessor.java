package org.cttelsamicsterrassa.data.load.shared.fcttweb.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.game.model.SetScore;
import org.cttelsamicsterrassa.data.core.domain.game.repository.DoublesPairRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.GameRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.SetScoreRepository;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaClassification;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaCompletenessClassifier;
import org.cttelsamicsterrassa.data.load.shared.match.MatchRegions;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchFixtureIdentityGuard;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchFixtureIdentityGuard.IncomingFixture;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleAction;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleOutcome;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecyclePlan;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecyclePlanner;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleSource;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleWriter;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaLineupPlayer;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParticipant;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaScore;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaSet;
import org.cttelsamicsterrassa.data.load.shared.preview.FixturePreview;
import org.cttelsamicsterrassa.data.load.shared.preview.PreviewChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Stores the matches of a fctt.cat export for one {@link ImportSource}: the single implementation
 * behind the BCNESA and FCTT current-season import paths.
 *
 * <p>A fixture is resolved once ({@code classify}, orient, look both clubs up, find the stored match
 * by natural key, run the identity guard) and that one resolution feeds both {@link #process}, which
 * writes through the shared match lifecycle, and {@link #preview}, which never writes.</p>
 *
 * <h2>Hooks</h2>
 * <ul>
 *   <li>{@link ActaOrientation}: brings a played payload to home/away. {@link ActaOrientation#MIRRORED}
 *       for FCTT, whose older extracts may key everything by the A/B/C column; identity otherwise.</li>
 *   <li>The fixture split happens before this class: a context is always one fixture, whether the
 *       navigator took it whole from an ingest-layout file or split it out of a legacy BCNESA one.</li>
 * </ul>
 *
 * <h2>What stays source-specific</h2>
 * <ul>
 *   <li>Identity: {@code source} scopes every team, player and match lookup; the fixture id is the
 *       payload's {@code id_partido} for the fixture at index 0 only, so a legacy fixture inferred
 *       from a multi-fixture file never borrows it.</li>
 *   <li>Team names: BCNESA looks clubs up by their normalised name, FCTT by the name as written
 *       ({@link FcttWebTeamImportProcessor#storedName}).</li>
 *   <li>Early exits ({@code NotStored}): an FCTT report is skipped before any lookup when it is an
 *       unresolved pending fixture, lacks a team object or has an invalid group folder; BCNESA
 *       fixtures are filtered by the navigator before they get here.</li>
 *   <li>Played header: BCNESA derives games/sets won and the winner from the fixture's own games
 *       (the file-level {@code resultado_final} aggregates a multi-fixture file); FCTT reads
 *       {@code resultado_final}, which also covers walkovers that carry no games.</li>
 *   <li>Lineups: BCNESA builds them from the fixture's singles participants and stores no ranking;
 *       FCTT reads {@code alineaciones} with ranking.</li>
 *   <li>Lineup and doubles-pair rows carry {@code source} for FCTT only, as before; games and set
 *       scores carry it for both.</li>
 * </ul>
 */
public class FcttWebMatchImportProcessor {

    /** Matches come last: they reference clubs and players. */
    public static final int ORDER = 30;

    private static final Logger LOGGER = LoggerFactory.getLogger(FcttWebMatchImportProcessor.class);

    private static final String GAME_TYPE_INDIVIDUAL = "INDIVIDUAL";
    private static final String GAME_TYPE_DOUBLES = "DOUBLES";
    private static final String SIDE_HOME = "HOME";
    private static final String SIDE_AWAY = "AWAY";

    /** Lineup letter to the position it stands for within a team. */
    private static final Map<String, Integer> POSITION_BY_LETTER = Map.of(
            "A", 1, "B", 2, "C", 3,
            "X", 1, "Y", 2, "Z", 3);

    private final ImportSource source;
    private final ActaOrientation orientation;
    private final TeamRepository teamRepository;
    private final PlayerSeasonRepository playerSeasonRepository;
    private final MatchRepository matchRepository;
    private final ActaCompletenessClassifier classifier = new ActaCompletenessClassifier();
    private final MatchLifecycleWriter lifecycleWriter;
    private final MatchFixtureIdentityGuard identityGuard;
    private final MatchLifecyclePlanner planner = new MatchLifecyclePlanner();

    public FcttWebMatchImportProcessor(ImportSource source,
                                       TeamRepository teamRepository,
                                       PlayerSeasonRepository playerSeasonRepository,
                                       MatchRepository matchRepository,
                                       LineupRepository lineupRepository,
                                       GameRepository gameRepository,
                                       SetScoreRepository setScoreRepository,
                                       DoublesPairRepository doublesPairRepository) {
        this(source, ActaOrientation.defaultFor(source), teamRepository, playerSeasonRepository, matchRepository,
                lineupRepository, gameRepository, setScoreRepository, doublesPairRepository);
    }

    public FcttWebMatchImportProcessor(ImportSource source,
                                       ActaOrientation orientation,
                                       TeamRepository teamRepository,
                                       PlayerSeasonRepository playerSeasonRepository,
                                       MatchRepository matchRepository,
                                       LineupRepository lineupRepository,
                                       GameRepository gameRepository,
                                       SetScoreRepository setScoreRepository,
                                       DoublesPairRepository doublesPairRepository) {
        this.source = Objects.requireNonNull(source, "source");
        this.orientation = Objects.requireNonNull(orientation, "orientation");
        this.teamRepository = teamRepository;
        this.playerSeasonRepository = playerSeasonRepository;
        this.matchRepository = matchRepository;
        this.lifecycleWriter = new MatchLifecycleWriter(matchRepository, lineupRepository, gameRepository,
                setScoreRepository, doublesPairRepository);
        this.identityGuard = new MatchFixtureIdentityGuard(matchRepository);
    }

    public void process(FcttWebFixtureContext reportContext) {
        ResolvedFixture resolved = resolve(reportContext);
        FcttWebFixtureContext context = resolved.context();
        if (resolved.notStored() != null) {
            logNotStored(resolved);
            return;
        }

        Optional<Team> homeTeam = resolved.homeTeam();
        Optional<Team> awayTeam = resolved.awayTeam();
        warnIfUnregistered(homeTeam, resolved.homeTeamName(), resolved);
        warnIfUnregistered(awayTeam, resolved.awayTeamName(), resolved);
        // FEAT-00086: the fixture is "seen" even when a team is unregistered or the identity guard
        // later rejects it, so snapshot reconciliation cannot report it as absent.
        context.runContext().recordSnapshotFixture(resolved.competition(), resolved.groupNumber(),
                resolved.phase(), resolved.round(), resolved.sourceFixtureId(),
                homeTeam.map(Team::getId).orElse(null), awayTeam.map(Team::getId).orElse(null));
        if (homeTeam.isEmpty() || awayTeam.isEmpty()) {
            return;
        }
        if (resolved.conflict().isPresent()) {
            recordOutcome(context, MatchLifecycleOutcome.FIXTURE_IDENTITY_CONFLICT, resolved.conflict().get());
            return;
        }
        MatchLifecycleOutcome outcome = lifecycleWriter.apply(resolved.classification(), resolved.existing(),
                lifecycleSource(resolved, homeTeam.get(), awayTeam.get()), context.runContext().amendedActaMode(),
                context.matchReportFile());
        recordOutcome(context, outcome, resolved.classification().reason());
    }

    /**
     * FEAT-00088: the read-only projection of {@link #process} for the import preview. It shares the
     * resolution step (so the natural key and fixture id cannot diverge) and the lifecycle planner,
     * but never writes, never records a snapshot fixture and never records a match outcome.
     */
    public FixturePreview preview(FcttWebFixtureContext reportContext) {
        ResolvedFixture resolved = resolve(reportContext);
        if (resolved.classification().unresolvedPendingFixture()) {
            return fixturePreview(resolved, PreviewChange.NOT_STORED, null, false,
                    resolved.classification().reason());
        }
        if (resolved.notStored() != null) {
            return fixturePreview(resolved, PreviewChange.NOT_STORED, null, false,
                    resolved.notStored().reason(resolved));
        }
        if (isBlank(resolved.homeTeamName()) || isBlank(resolved.awayTeamName())) {
            return fixturePreview(resolved, PreviewChange.NOT_STORED, null, false, "team without a name");
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
                lifecycleSource(resolved, resolved.homeTeam().get(), resolved.awayTeam().get()));
        Integer existingRound = plan.action() == MatchLifecycleAction.UPGRADE_TO_PLAYED
                ? resolved.existing().get().getRound()
                : null;
        return fixturePreview(resolved, PreviewChange.of(plan), existingRound, false,
                resolved.classification().reason());
    }

    /**
     * The shared read-only resolution of one fixture: fixture-scoped classification, orientation,
     * scope, the early exits, both team lookups, the natural-key match and the identity guard.
     * {@code process} and {@code preview} both build on it so the key and fixture-id rules cannot
     * diverge.
     */
    private ResolvedFixture resolve(FcttWebFixtureContext reportContext) {
        ActaClassification classification = classifier.classify(reportContext.acta(), reportContext.games());
        Acta acta = classification.isPlayed() ? orientation.toHomeAway(reportContext.acta()) : reportContext.acta();
        FcttWebFixtureContext context = acta == reportContext.acta() ? reportContext : reportContext.withActa(acta);
        Season season = context.toSeason();
        String competition = context.competition();
        Integer groupNumber = context.groupNumberOrNull();
        int round = context.round();
        String phase = context.phase();
        String sourceFixtureId = context.fixtureIndex() == 0 ? acta.matchId() : null;

        NotStored notStored = notStored(context, classification);
        if (notStored != null) {
            return new ResolvedFixture(context, acta, classification, season, competition, groupNumber, round,
                    phase, sourceFixtureId, null, null, Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), notStored);
        }

        String homeTeamName = FcttWebTeamImportProcessor.storedName(source, context.homeTeamName());
        String awayTeamName = FcttWebTeamImportProcessor.storedName(source, context.awayTeamName());
        Optional<Team> homeTeam = lookupTeam(homeTeamName, season);
        Optional<Team> awayTeam = lookupTeam(awayTeamName, season);
        Optional<Match> existing = Optional.empty();
        Optional<String> conflict = Optional.empty();
        if (homeTeam.isPresent() && awayTeam.isPresent()) {
            existing = matchRepository.findMatchByNaturalKey(competition, season, groupNumber, round, phase,
                    homeTeam.get().getId(), awayTeam.get().getId());
            conflict = identityGuard.conflict(
                    new IncomingFixture(source, sourceFixtureId, competition, season, groupNumber, round, phase),
                    existing);
        }
        return new ResolvedFixture(context, acta, classification, season, competition, groupNumber, round,
                phase, sourceFixtureId, homeTeamName, awayTeamName, homeTeam, awayTeam, existing, conflict, null);
    }

    /** The early exit that applies before any team lookup or snapshot record, or {@code null}. */
    private NotStored notStored(FcttWebFixtureContext context, ActaClassification classification) {
        if (source != ImportSource.FCTT) {
            return null;
        }
        Acta acta = context.acta();
        if (classification.unresolvedPendingFixture()) {
            return NotStored.UNRESOLVED_PENDING;
        }
        if (acta.teams() == null || acta.teams().home() == null || acta.teams().away() == null) {
            return NotStored.INCOMPLETE_TEAMS;
        }
        if (context.hasInvalidGroupFolder()) {
            return NotStored.INVALID_GROUP_FOLDER;
        }
        return null;
    }

    private Optional<Team> lookupTeam(String name, Season season) {
        if (isBlank(name)) {
            return Optional.empty();
        }
        return teamRepository.findTeamByNameAndSeasonAndSource(name, season, source);
    }

    private void warnIfUnregistered(Optional<Team> team, String name, ResolvedFixture resolved) {
        if (team.isPresent()) {
            return;
        }
        if (isBlank(name)) {
            LOGGER.warn("{} report {} has a team without a name; match not stored", source,
                    resolved.context().matchReportFile());
        } else {
            LOGGER.warn("{} club {} has no entry for season {}; {} not stored", source, name, resolved.season(),
                    resolved.context().matchReportFile());
        }
    }

    private void logNotStored(ResolvedFixture resolved) {
        switch (resolved.notStored()) {
            case UNRESOLVED_PENDING -> LOGGER.warn("{} report {} is a pending fixture without teams; not stored",
                    source, resolved.context().matchReportFile());
            case INCOMPLETE_TEAMS -> LOGGER.warn("{} report {} has incomplete teams; match not stored",
                    source, resolved.context().matchReportFile());
            case INVALID_GROUP_FOLDER -> LOGGER.warn("{} report {} has invalid group folder {}; match not stored",
                    source, resolved.context().matchReportFile(), resolved.context().group());
        }
    }

    private MatchLifecycleSource lifecycleSource(ResolvedFixture resolved, Team homeTeam, Team awayTeam) {
        return new FixtureLifecycleSource(resolved, homeTeam, awayTeam);
    }

    private FixturePreview fixturePreview(ResolvedFixture resolved, PreviewChange change, Integer existingRound,
                                          boolean teamsPendingRegistration, String reason) {
        return new FixturePreview(source, resolved.competition(), resolved.groupNumber(),
                resolved.phase(), resolved.round(), resolved.sourceFixtureId(), resolved.homeTeamName(),
                resolved.awayTeamName(), resolved.classification(), change, existingRound,
                teamsPendingRegistration, reason, resolved.context().matchReportFile());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void recordOutcome(FcttWebFixtureContext context, MatchLifecycleOutcome outcome, String reason) {
        String effectiveReason = outcome.amendmentReason() != null ? outcome.amendmentReason() : reason;
        context.runContext().recordMatchOutcome(outcome, getClass().getSimpleName(),
                context.matchReportFile(), effectiveReason);
        if (outcome.isReportable()) {
            LOGGER.warn("{} match lifecycle {} for fixture {} of {}: {}", source, outcome, context.fixtureIndex(),
                    context.matchReportFile(), effectiveReason);
        }
    }

    /** Why a report is skipped before any team lookup or snapshot record. */
    private enum NotStored {
        UNRESOLVED_PENDING, INCOMPLETE_TEAMS, INVALID_GROUP_FOLDER;

        private String reason(ResolvedFixture resolved) {
            return switch (this) {
                case UNRESOLVED_PENDING -> resolved.classification().reason();
                case INCOMPLETE_TEAMS -> "incomplete teams";
                case INVALID_GROUP_FOLDER -> "invalid group folder " + resolved.context().group();
            };
        }
    }

    /** The read-only result of resolving one fixture, shared by {@code process} and {@code preview}. */
    private record ResolvedFixture(
            FcttWebFixtureContext context,
            Acta acta,
            ActaClassification classification,
            Season season,
            String competition,
            Integer groupNumber,
            int round,
            String phase,
            String sourceFixtureId,
            String homeTeamName,
            String awayTeamName,
            Optional<Team> homeTeam,
            Optional<Team> awayTeam,
            Optional<Match> existing,
            Optional<String> conflict,
            NotStored notStored) {
    }

    /**
     * The callbacks of the shared match lifecycle. Orientation has already been applied when the
     * classification is PLAYED, so the scheduled header never reads {@code resultado_final}.
     */
    private final class FixtureLifecycleSource implements MatchLifecycleSource {

        private final FcttWebFixtureContext context;
        private final Season season;
        private final String competition;
        private final Integer groupNumber;
        private final int round;
        private final Team homeTeam;
        private final Team awayTeam;
        private final String sourceFixtureId;

        private FixtureLifecycleSource(ResolvedFixture resolved, Team homeTeam, Team awayTeam) {
            this.context = resolved.context();
            this.season = resolved.season();
            this.competition = resolved.competition();
            this.groupNumber = resolved.groupNumber();
            this.round = resolved.round();
            this.sourceFixtureId = resolved.sourceFixtureId();
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
        }

        @Override
        public Match buildScheduledMatch(UUID id) {
            return header(id)
                    .status(MatchStatus.SCHEDULED)
                    .createNew();
        }

        @Override
        public MatchContent buildPlayedContent(UUID id, boolean existing) {
            Match match = buildPlayedMatch(id, existing);
            SideLineup home = resolveLineup(context, true, season);
            SideLineup away = resolveLineup(context, false, season);
            List<Lineup> lineups = buildLineups(match, homeTeam, home, awayTeam, away);
            BuiltGames built = buildGames(context, match, home, away);
            return new MatchContent(match, lineups, built.games(), built.setScores(), built.doublesPairs());
        }

        private Match buildPlayedMatch(UUID id, boolean existing) {
            PlayedResult result = playedResult(context, homeTeam, awayTeam);
            Match.MatchBuilder builder = header(id)
                    .winnerTeam(result.winner())
                    .homeGamesWon(result.homeGamesWon())
                    .awayGamesWon(result.awayGamesWon())
                    .homeSetsWon(result.homeSetsWon())
                    .awaySetsWon(result.awaySetsWon())
                    .status(MatchStatus.PLAYED);
            return existing ? builder.createExisting() : builder.createNew();
        }

        /** The fields every stored match carries, scheduled or played. */
        private Match.MatchBuilder header(UUID id) {
            Acta acta = context.acta();
            return Match.builder()
                    .id(id)
                    .source(source)
                    .region(MatchRegions.region(source, acta, competition))
                    .regionType(MatchRegions.regionType(source, acta, competition))
                    .sourceFixtureId(sourceFixtureId)
                    .competition(competition)
                    .season(season)
                    .groupNumber(groupNumber)
                    .round(round)
                    .phase(context.phase())
                    .dateTime(toDateTime(acta))
                    .city(acta.venue() == null ? null : acta.venue().city())
                    .venue(acta.venue() == null ? null : acta.venue().venue())
                    .homeTeam(homeTeam)
                    .awayTeam(awayTeam)
                    .refereeName(refereeName(acta))
                    .protested(acta.wasProtested());
        }
    }

    /** Games, their set scores and doubles pairs built from one fixture, not yet saved. */
    private record BuiltGames(List<Game> games, List<SetScore> setScores, List<DoublesPair> doublesPairs) {
    }

    // --- match -----------------------------------------------------------------------------

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

    /** The played header of a match: games and sets won per side and the winning team. */
    private record PlayedResult(Integer homeGamesWon, Integer awayGamesWon, Integer homeSetsWon,
                                Integer awaySetsWon, Team winner) {
    }

    private PlayedResult playedResult(FcttWebFixtureContext context, Team homeTeam, Team awayTeam) {
        return source == ImportSource.FCTT
                ? payloadResult(context, homeTeam, awayTeam)
                : fixtureResult(context, homeTeam, awayTeam);
    }

    /**
     * BCNESA: derived from this fixture's own games rather than the payload's {@code resultado_final},
     * which would aggregate every fixture of a multi-fixture file. The winner is the games-won
     * comparison, a draw left unset. BCNESA carries no set-by-set score in its PDF-based seasons, so
     * the sets won of each game are the finest granularity available.
     */
    private PlayedResult fixtureResult(FcttWebFixtureContext context, Team homeTeam, Team awayTeam) {
        List<ActaGame> games = context.games();
        int homeGamesWon = (int) games.stream().filter(g -> ActaGame.WINNER_HOME.equals(g.winner())).count();
        int awayGamesWon = (int) games.stream().filter(g -> ActaGame.WINNER_AWAY.equals(g.winner())).count();
        int homeSetsWon = games.stream().mapToInt(g -> setsWon(g, true)).sum();
        int awaySetsWon = games.stream().mapToInt(g -> setsWon(g, false)).sum();
        Team winner = homeGamesWon > awayGamesWon ? homeTeam : awayGamesWon > homeGamesWon ? awayTeam : null;
        return new PlayedResult(homeGamesWon, awayGamesWon, homeSetsWon, awaySetsWon, winner);
    }

    private static int setsWon(ActaGame game, boolean home) {
        ActaScore score = game.setsWon();
        if (score == null) {
            return 0;
        }
        Integer value = home ? score.home() : score.away();
        return value != null ? value : 0;
    }

    /** FCTT: read from {@code resultado_final}, the winner by name first and by score as a fallback. */
    private PlayedResult payloadResult(FcttWebFixtureContext context, Team homeTeam, Team awayTeam) {
        Acta acta = context.acta();
        ActaScore gamesWon = acta.finalResult() == null ? null : acta.finalResult().gamesWon();
        ActaScore setsWon = acta.finalResult() == null ? null : acta.finalResult().setsWon();
        return new PlayedResult(gamesWon == null ? null : gamesWon.home(), gamesWon == null ? null : gamesWon.away(),
                setsWon == null ? null : setsWon.home(), setsWon == null ? null : setsWon.away(),
                payloadWinner(context, homeTeam, awayTeam));
    }

    private Team payloadWinner(FcttWebFixtureContext context, Team homeTeam, Team awayTeam) {
        Acta acta = context.acta();
        if (acta.finalResult() == null) {
            return null;
        }
        String winnerName = acta.finalResult().winnerName();
        if (winnerName != null) {
            if (winnerName.equals(acta.teams().home().name())) {
                return homeTeam;
            }
            if (winnerName.equals(acta.teams().away().name())) {
                return awayTeam;
            }
            LOGGER.debug("{} winner \"{}\" matches neither team in {}; falling back to score", source,
                    winnerName, context.matchReportFile());
        }
        ActaScore gamesWon = acta.finalResult().gamesWon();
        if (gamesWon == null || gamesWon.home() == null || gamesWon.away() == null) {
            return null;
        }
        return gamesWon.home() > gamesWon.away() ? homeTeam
                : gamesWon.away() > gamesWon.home() ? awayTeam : null;
    }

    // --- lineups ---------------------------------------------------------------------------

    private SideLineup resolveLineup(FcttWebFixtureContext context, boolean home, Season season) {
        return source == ImportSource.FCTT
                ? lineupFromPayload(context, home, season)
                : lineupFromGames(context, home, season);
    }

    /**
     * FCTT: one side's lineup from {@code alineaciones}, keyed by lineup letter for position
     * resolution and by name for doubles-pair resolution, with each player's ranking.
     */
    private SideLineup lineupFromPayload(FcttWebFixtureContext context, boolean home, Season season) {
        Map<String, ActaLineupPlayer> letters = context.acta().lineups() == null
                ? Map.of()
                : home ? context.acta().lineups().home() : context.acta().lineups().away();
        Map<String, PlayerSeason> byLetter = new LinkedHashMap<>();
        Map<String, PlayerSeason> byName = new LinkedHashMap<>();
        Map<String, Double> rankingByLetter = new LinkedHashMap<>();
        letters.forEach((letter, player) -> {
            if (player == null || !FcttWebLicenses.isUsable(player.license())) {
                LOGGER.warn("{} lineup letter {} has no usable licence ({}) in {}; lineup left out", source, letter,
                        player == null ? null : player.license(), context.matchReportFile());
                return;
            }
            playerSeasonRepository.findPlayerSeasonBySourceLicenseAndSeason(source, player.license(), season)
                    .ifPresentOrElse(playerSeason -> {
                        byLetter.put(letter, playerSeason);
                        rankingByLetter.put(letter, player.ranking());
                        if (player.name() != null) {
                            byName.put(player.name(), playerSeason);
                        }
                    }, () -> LOGGER.warn("No {} registration for licence {} in {}; lineup {} left out", source,
                            player.license(), season, letter));
        });
        return new SideLineup(byLetter, byName, rankingByLetter);
    }

    /**
     * BCNESA: one side's lineup from this fixture's own singles games. It does not read
     * {@code alineaciones}: that block covers only a file's first fixture, and even in the one-fixture
     * export 476 of 16,387 files have a singles participant missing from it, while every singles
     * game carries its own participant licence.
     */
    private SideLineup lineupFromGames(FcttWebFixtureContext context, boolean home, Season season) {
        Map<String, PlayerSeason> byLetter = new LinkedHashMap<>();
        Map<String, PlayerSeason> byName = new LinkedHashMap<>();
        for (ActaGame game : context.games()) {
            if (game.isDoubles()) {
                continue;
            }
            ActaParticipant participant = home ? game.home() : game.away();
            if (participant == null || participant.letter() == null
                    || !FcttWebLicenses.isUsable(participant.license())) {
                continue;
            }
            Optional<PlayerSeason> playerSeason = playerSeasonRepository.findPlayerSeasonBySourceLicenseAndSeason(
                    source, participant.license(), season);
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
        return new SideLineup(byLetter, byName, Map.of());
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
                LOGGER.warn("Unknown {} lineup letter {} in match {}; entry left out", source, letter, match.getId());
                return;
            }
            Double ranking = side.rankingByLetter().get(letter);
            lineups.add(Lineup.builder()
                    .id(UUID.randomUUID())
                    .source(rowSource())
                    .match(match)
                    .team(team)
                    .letter(letter)
                    .position(position)
                    .player(player)
                    .ranking(ranking == null ? null : ranking.floatValue())
                    .createNew());
        });
    }

    /**
     * The {@code source} stamped on lineup and doubles-pair rows: FCTT sets it, BCNESA has always left
     * it unset (the column is nullable). Kept as is so this change does not rewrite what BCNESA stores;
     * games and set scores carry the source for both.
     */
    private ImportSource rowSource() {
        return source == ImportSource.FCTT ? source : null;
    }

    // --- games -----------------------------------------------------------------------------

    private BuiltGames buildGames(FcttWebFixtureContext context, Match match, SideLineup home, SideLineup away) {
        List<Game> games = new ArrayList<>();
        List<SetScore> setScores = new ArrayList<>();
        List<DoublesPair> doublesPairs = new ArrayList<>();

        for (ActaGame actaGame : context.games()) {
            if (actaGame.number() == null) {
                LOGGER.warn("{} game without a number in {}; left out", source, context.matchReportFile());
                continue;
            }
            Game game = buildGame(actaGame, match, home, away);
            games.add(game);
            setScores.addAll(buildSetScores(actaGame, game));
            if (actaGame.isDoubles()) {
                doublesPairs.addAll(buildDoublesPairs(actaGame, game, home, away, context));
            }
        }
        return new BuiltGames(games, setScores, doublesPairs);
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
                .source(source)
                .match(match)
                .gameNumber(actaGame.number())
                .type(doubles ? GAME_TYPE_DOUBLES : GAME_TYPE_INDIVIDUAL)
                .crossover(actaGame.crossover() == null ? "" : actaGame.crossover())
                .homePlayer(homePlayer)
                .awayPlayer(awayPlayer)
                .homeSetsWon(setsWon == null ? null : setsWon.home())
                .awaySetsWon(setsWon == null ? null : setsWon.away())
                .winner(winnerOf(winnerSide, homePlayer, awayPlayer))
                .winnerSide(winnerSide)
                .cumulativeHomeSetsWon(cumulative == null || cumulative.home() == null ? 0 : cumulative.home())
                .cumulativeAwaySetsWon(cumulative == null || cumulative.away() == null ? 0 : cumulative.away())
                .notPlayed(actaGame.wasNotPlayed())
                .reason(actaGame.reason())
                .createNew();
    }

    private List<SetScore> buildSetScores(ActaGame actaGame, Game game) {
        List<SetScore> scores = new ArrayList<>();
        for (ActaSet set : actaGame.sets()) {
            if (set.number() != null && set.homePoints() != null && set.awayPoints() != null) {
                scores.add(SetScore.builder()
                        .id(UUID.randomUUID())
                        .source(source)
                        .game(game)
                        .setNumber(set.number())
                        .homePoints(set.homePoints())
                        .awayPoints(set.awayPoints())
                        .build());
            }
        }
        return scores;
    }

    private static String toSide(String winner) {
        return ActaGame.WINNER_HOME.equals(winner) ? SIDE_HOME
                : ActaGame.WINNER_AWAY.equals(winner) ? SIDE_AWAY : null;
    }

    private static PlayerSeason winnerOf(String winnerSide, PlayerSeason homePlayer, PlayerSeason awayPlayer) {
        return SIDE_HOME.equals(winnerSide) ? homePlayer : SIDE_AWAY.equals(winnerSide) ? awayPlayer : null;
    }

    private static PlayerSeason playerOf(ActaParticipant participant, SideLineup side) {
        return participant == null || participant.letter() == null ? null : side.byLetter().get(participant.letter());
    }

    private List<DoublesPair> buildDoublesPairs(ActaGame actaGame, Game game, SideLineup home,
                                                SideLineup away, FcttWebFixtureContext context) {
        List<DoublesPair> pairs = new ArrayList<>();
        addDoublesPair(pairs, game, SIDE_HOME, actaGame.home(), home, context);
        addDoublesPair(pairs, game, SIDE_AWAY, actaGame.away(), away, context);
        return pairs;
    }

    private void addDoublesPair(List<DoublesPair> pairs, Game game, String side, ActaParticipant participant,
                                SideLineup lineup, FcttWebFixtureContext context) {
        if (participant == null) {
            return;
        }
        Set<UUID> pairMembers = new HashSet<>();
        for (ActaLineupPlayer player : participant.doublesPlayers()) {
            PlayerSeason playerSeason = playerOf(player, lineup, context.toSeason());
            if (playerSeason == null) {
                LOGGER.warn("{} doubles player \"{}\" with licence {} is unavailable in fixture {} of {}; "
                                + "pair member left out", source, player == null ? null : player.name(),
                        player == null ? null : player.license(), context.fixtureIndex(),
                        context.matchReportFile());
                continue;
            }
            // Some actas name the same player twice in one pair; a second row would break the
            // (game, side, player, source) unique key, so the repeated member is left out.
            if (!pairMembers.add(playerSeason.getId())) {
                LOGGER.warn("{} doubles player \"{}\" with licence {} is listed twice on the {} side in {}; "
                        + "duplicate pair member left out", source, player.name(), player.license(), side,
                        context.matchReportFile());
                continue;
            }
            pairs.add(DoublesPair.builder()
                    .id(UUID.randomUUID())
                    .source(rowSource())
                    .game(game)
                    .side(side)
                    .player(playerSeason)
                    .build());
        }
    }

    private PlayerSeason playerOf(ActaLineupPlayer player, SideLineup lineup, Season season) {
        if (player == null || player.name() == null || !FcttWebLicenses.isUsable(player.license())) {
            return null;
        }
        PlayerSeason inLineup = lineup.byName().get(player.name());
        if (inLineup != null && player.license().equals(inLineup.getLicense())) {
            return inLineup;
        }
        return playerSeasonRepository.findPlayerSeasonBySourceLicenseAndSeason(source, player.license(), season)
                .orElse(null);
    }

    /**
     * One side's lineup, resolved to stored players: by lineup letter for singles and by name and
     * licence for doubles pairs, plus the ranking per letter where the source carries one.
     */
    private record SideLineup(Map<String, PlayerSeason> byLetter,
                              Map<String, PlayerSeason> byName,
                              Map<String, Double> rankingByLetter) {
    }
}
