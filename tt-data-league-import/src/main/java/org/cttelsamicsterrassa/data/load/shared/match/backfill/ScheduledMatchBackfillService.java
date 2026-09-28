package org.cttelsamicsterrassa.data.load.shared.match.backfill;

import org.cttelsamicsterrassa.data.core.domain.match.model.ScheduledMatchBackfillCandidate;
import org.cttelsamicsterrassa.data.core.domain.match.model.ScheduledMatchBackfillWriteResult;
import org.cttelsamicsterrassa.data.core.domain.match.repository.ScheduledMatchBackfillRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Runs the FEAT-00078 backfill: finds legacy empty and "decided 0-0" {@code PLAYED} matches and, in
 * {@link ScheduledMatchBackfillMode#WRITE} mode, marks them {@code SCHEDULED}. There is no
 * catch-and-continue: repository failures propagate.
 */
@Component
public class ScheduledMatchBackfillService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledMatchBackfillService.class);

    private final ScheduledMatchBackfillRepository repository;

    public ScheduledMatchBackfillService(ScheduledMatchBackfillRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    public ScheduledMatchBackfillSummary run(ImportSource source, Season season, ScheduledMatchBackfillMode mode) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(season, "season must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        LOGGER.info("Starting scheduled-match backfill for {}/{} in {} mode", source, season, mode);

        List<ScheduledMatchBackfillCandidate> candidates = repository.findScheduledBackfillCandidates(source, season);
        for (ScheduledMatchBackfillCandidate candidate : candidates) {
            LOGGER.info(
                    "Backfill candidate: match={} competition={} group={} round={} phase={} date={} "
                            + "homeTeam={} awayTeam={} games={} lineups={}",
                    candidate.matchId(), candidate.competition(), candidate.groupNumber(), candidate.round(),
                    candidate.phase(), candidate.matchDate(), candidate.homeTeamId(), candidate.awayTeamId(),
                    candidate.gameCount(), candidate.lineupCount());
        }
        LOGGER.info("Found {} scheduled-match backfill candidates for {}/{}", candidates.size(), source, season);

        if (mode == ScheduledMatchBackfillMode.REPORT) {
            return new ScheduledMatchBackfillSummary(
                    source, season, mode, candidates, new ScheduledMatchBackfillWriteResult(0, 0, 0, 0, 0));
        }

        List<java.util.UUID> candidateIds = candidates.stream().map(ScheduledMatchBackfillCandidate::matchId).toList();
        ScheduledMatchBackfillWriteResult written = repository.markScheduled(source, season, candidateIds);
        LOGGER.info(
                "Scheduled-match backfill for {}/{} marked {} matches SCHEDULED, deleting {} games, {} lineups, "
                        + "{} set scores and {} doubles pairs",
                source, season, written.matchesUpdated(), written.gamesDeleted(), written.lineupsDeleted(),
                written.setScoresDeleted(), written.doublesPairsDeleted());

        return new ScheduledMatchBackfillSummary(source, season, mode, candidates, written);
    }
}
