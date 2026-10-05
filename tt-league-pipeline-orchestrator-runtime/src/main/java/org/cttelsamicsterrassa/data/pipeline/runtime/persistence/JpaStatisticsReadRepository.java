package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.IngestHealth;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepStatus;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.CorrectionFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.MatchFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.RunFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.StepFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.port.StatisticsReadRepository;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only JPQL over the existing entities. Filters rows only: percentiles, buckets and every other figure stay in
 * the core ({@code StatisticsRules}), so nothing is aggregated in SQL.
 */
@Repository
@Transactional(readOnly = true)
class JpaStatisticsReadRepository implements StatisticsReadRepository {

    private static final String MATCH_SELECT = "select t.matchId, t.matchDayId, d.source, d.season, d.competition, "
            + "d.state, t.status, t.matchDateTime, t.firstSeenAt, t.reportedAt, t.ignoredAt "
            + "from MatchTrackingEntity t, MatchDayEntity d where t.matchDayId = d.id";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<RunFacts> terminalRunsFinishedBetween(Instant from, Instant to, Set<PipelineSource> sources) {
        List<RunStatus> terminal = List.of(RunStatus.NO_CHANGES, RunStatus.SUCCEEDED, RunStatus.PARTIAL,
                RunStatus.FAILED);
        String jpql = "select r.id, r.source, r.status, r.startedAt, r.finishedAt from PipelineRunEntity r "
                + "where r.finishedAt >= :from and r.finishedAt < :to and r.status in :terminal"
                + sourceFilter("r", sources);
        Query query = entityManager.createQuery(jpql)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("terminal", terminal);
        bindSources(query, sources);
        return rows(query).stream()
                .map(row -> new RunFacts((UUID) row[0], (PipelineSource) row[1], (RunStatus) row[2], (Instant) row[3],
                        (Instant) row[4]))
                .toList();
    }

    @Override
    public List<StepFacts> stepsFinishedBetween(Instant from, Instant to, Set<PipelineSource> sources) {
        String jpql = "select s.runId, r.source, s.kind, s.status, s.outcome, s.startedAt, s.finishedAt, "
                + "s.httpErrors, s.timeouts, s.parseErrors from PipelineStepEntity s, PipelineRunEntity r "
                + "where s.runId = r.id and s.finishedAt >= :from and s.finishedAt < :to "
                + "and s.status in :finished" + sourceFilter("r", sources);
        Query query = entityManager.createQuery(jpql)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("finished", List.of(StepStatus.SUCCEEDED, StepStatus.FAILED));
        bindSources(query, sources);
        return rows(query).stream()
                .map(row -> new StepFacts((UUID) row[0], (PipelineSource) row[1], (StepKind) row[2],
                        (StepStatus) row[3], (String) row[4], (Instant) row[5], (Instant) row[6],
                        row[7] == null ? null : new IngestHealth((Long) row[7], (Long) row[8], (Long) row[9])))
                .toList();
    }

    @Override
    public List<MatchFacts> matchesBySeason(Set<PipelineSource> sources, String season) {
        String jpql = MATCH_SELECT + (season == null ? "" : " and d.season = :season") + sourceFilter("d", sources);
        Query query = entityManager.createQuery(jpql);
        if (season != null) {
            query.setParameter("season", season);
        }
        bindSources(query, sources);
        return matches(query);
    }

    @Override
    public List<MatchFacts> matchesForDay(Instant start, Instant end) {
        String jpql = MATCH_SELECT
                + " and ((t.reportedAt >= :start and t.reportedAt < :end)"
                + " or (t.ignoredAt is null and t.matchDateTime < :end"
                + " and (t.reportedAt is null or t.reportedAt >= :end)))";
        return matches(entityManager.createQuery(jpql).setParameter("start", start).setParameter("end", end));
    }

    @Override
    public List<CorrectionFacts> importReportsReceivedBetween(
            Instant from, Instant to, Set<PipelineSource> sources) {
        String jpql = "select i.runId, r.source, i.receivedAt, i.amendedPlayed from ImportReportEntity i, "
                + "PipelineRunEntity r where i.runId = r.id and i.receivedAt >= :from and i.receivedAt < :to"
                + sourceFilter("r", sources);
        Query query = entityManager.createQuery(jpql).setParameter("from", from).setParameter("to", to);
        bindSources(query, sources);
        return rows(query).stream()
                .map(row -> new CorrectionFacts((UUID) row[0], (PipelineSource) row[1], (Instant) row[2],
                        (Long) row[3]))
                .toList();
    }

    private static List<MatchFacts> matches(Query query) {
        return rows(query).stream()
                .map(row -> new MatchFacts((UUID) row[0], (UUID) row[1], (PipelineSource) row[2], (String) row[3],
                        (String) row[4], (MatchDayState) row[5], (TrackedMatchStatus) row[6], (Instant) row[7],
                        (Instant) row[8], (Instant) row[9], row[10] != null))
                .toList();
    }

    private static String sourceFilter(String alias, Set<PipelineSource> sources) {
        return sources.isEmpty() ? "" : " and " + alias + ".source in :sources";
    }

    private static void bindSources(Query query, Set<PipelineSource> sources) {
        if (!sources.isEmpty()) {
            query.setParameter("sources", sources);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object[]> rows(Query query) {
        return query.getResultList();
    }
}
