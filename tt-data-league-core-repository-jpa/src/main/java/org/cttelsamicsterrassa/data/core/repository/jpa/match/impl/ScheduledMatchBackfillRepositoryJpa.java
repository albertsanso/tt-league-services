package org.cttelsamicsterrassa.data.core.repository.jpa.match.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.match.model.ScheduledMatchBackfillCandidate;
import org.cttelsamicsterrassa.data.core.domain.match.model.ScheduledMatchBackfillWriteResult;
import org.cttelsamicsterrassa.data.core.domain.match.repository.ScheduledMatchBackfillRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.cttelsamicsterrassa.data.core.repository.jpa.doublespair.impl.DoublesPairRepositoryHelper;
import org.cttelsamicsterrassa.data.core.repository.jpa.game.impl.GameRepositoryHelper;
import org.cttelsamicsterrassa.data.core.repository.jpa.lineup.impl.LineupRepositoryHelper;
import org.cttelsamicsterrassa.data.core.repository.jpa.setscore.impl.SetScoreRepositoryHelper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Transactional
@Component
@AllArgsConstructor
public class ScheduledMatchBackfillRepositoryJpa implements ScheduledMatchBackfillRepository {

    /** Keeps every bulk statement under common JDBC bind-parameter limits within the single write transaction. */
    private static final int CHUNK_SIZE = 500;

    private final MatchRepositoryHelper matchRepositoryHelper;
    private final GameRepositoryHelper gameRepositoryHelper;
    private final LineupRepositoryHelper lineupRepositoryHelper;
    private final SetScoreRepositoryHelper setScoreRepositoryHelper;
    private final DoublesPairRepositoryHelper doublesPairRepositoryHelper;

    @Override
    public List<ScheduledMatchBackfillCandidate> findScheduledBackfillCandidates(ImportSource source, Season season) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(season, "season");
        return matchRepositoryHelper
                .findScheduledBackfillCandidates(Source.valueOf(source.name()), season.toString())
                .stream()
                .map(ScheduledMatchBackfillRepositoryJpa::toCandidate)
                .toList();
    }

    @Override
    public ScheduledMatchBackfillWriteResult markScheduled(ImportSource source, Season season, Collection<UUID> matchIds) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(season, "season");
        if (matchIds == null || matchIds.isEmpty()) {
            return new ScheduledMatchBackfillWriteResult(0, 0, 0, 0, 0);
        }

        Source jpaSource = Source.valueOf(source.name());
        String jpaSeason = season.toString();
        List<UUID> ids = List.copyOf(matchIds);

        Set<UUID> stillCandidates = new java.util.HashSet<>();
        for (List<UUID> chunk : chunks(ids)) {
            stillCandidates.addAll(matchRepositoryHelper.findScheduledBackfillCandidateIds(jpaSource, jpaSeason, chunk));
        }
        if (stillCandidates.size() != ids.size() || !stillCandidates.containsAll(ids)) {
            List<UUID> offending = ids.stream().filter(id -> !stillCandidates.contains(id)).toList();
            throw new IllegalStateException(
                    "Match ids are no longer backfill candidates for " + source + "/" + season + ": " + offending);
        }

        int doublesPairsDeleted = 0;
        int setScoresDeleted = 0;
        int gamesDeleted = 0;
        int lineupsDeleted = 0;
        int matchesUpdated = 0;
        for (List<UUID> chunk : chunks(ids)) {
            doublesPairsDeleted += doublesPairRepositoryHelper.deleteAllByMatchIds(chunk);
            setScoresDeleted += setScoreRepositoryHelper.deleteAllByMatchIds(chunk);
            gamesDeleted += gameRepositoryHelper.deleteAllByMatchIds(chunk);
            lineupsDeleted += lineupRepositoryHelper.deleteAllByMatchIds(chunk);
            int updated = matchRepositoryHelper.markScheduledByIds(chunk);
            if (updated != chunk.size()) {
                throw new IllegalStateException(
                        "Expected to mark " + chunk.size() + " matches SCHEDULED but updated " + updated);
            }
            matchesUpdated += updated;
        }

        return new ScheduledMatchBackfillWriteResult(
                matchesUpdated, gamesDeleted, lineupsDeleted, setScoresDeleted, doublesPairsDeleted);
    }

    private static List<List<UUID>> chunks(List<UUID> ids) {
        List<List<UUID>> result = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += CHUNK_SIZE) {
            result.add(ids.subList(i, Math.min(i + CHUNK_SIZE, ids.size())));
        }
        return result;
    }

    private static ScheduledMatchBackfillCandidate toCandidate(ScheduledMatchBackfillCandidateProjection projection) {
        return new ScheduledMatchBackfillCandidate(
                projection.matchId(),
                projection.competition(),
                projection.groupNumber(),
                projection.round(),
                projection.phase(),
                projection.matchDate(),
                projection.homeTeamId(),
                projection.awayTeamId(),
                Math.toIntExact(projection.gameCount()),
                Math.toIntExact(projection.lineupCount()));
    }
}
