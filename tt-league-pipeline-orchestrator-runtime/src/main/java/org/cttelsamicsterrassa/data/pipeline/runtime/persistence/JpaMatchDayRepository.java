package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayChangeSet;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayEvent;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayFacets;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayKey;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayPage;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayQuery;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDaySummary;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayWindow;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.SourceSeason;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.MatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.StaleMatchDayException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link MatchDayRepository} over JPA. {@link #apply} is one transaction: a stored day or match is updated when its
 * stored version equals the aggregate version, otherwise (or when another writer created the same match-day key)
 * {@link StaleMatchDayException} is thrown and nothing is written.
 */
@Repository
@Transactional
class JpaMatchDayRepository implements MatchDayRepository {

    private static final String UNIQUE_VIOLATION = "23505";

    private final MatchDayJpaRepository days;
    private final MatchTrackingJpaRepository matches;
    private final MatchDayEventJpaRepository events;
    private final EntityManager entityManager;

    JpaMatchDayRepository(
            MatchDayJpaRepository days,
            MatchTrackingJpaRepository matches,
            MatchDayEventJpaRepository events,
            EntityManager entityManager) {
        this.days = days;
        this.matches = matches;
        this.events = events;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MatchDay> findById(UUID matchDayId) {
        return days.findById(matchDayId).map(JpaMatchDayRepository::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchDay> findBySourceAndSeason(PipelineSource source, String season) {
        return days.findBySourceAndSeason(source, season).stream().map(JpaMatchDayRepository::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchDay> findByState(MatchDayState state) {
        return days.findByState(state).stream().map(JpaMatchDayRepository::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchDay> findClosedSince(Instant since) {
        return days.findByStateAndClosedAtGreaterThanEqual(MatchDayState.CLOSED, since).stream()
                .map(JpaMatchDayRepository::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchTracking> findMatches(Collection<UUID> matchDayIds) {
        if (matchDayIds.isEmpty()) {
            return List.of();
        }
        return matches.findByMatchDayIdIn(matchDayIds).stream().map(JpaMatchDayRepository::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MatchTracking> findMatch(UUID matchId) {
        return matches.findById(matchId).map(JpaMatchDayRepository::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchDayEvent> findEvents(UUID matchDayId) {
        return events.findByMatchDayIdOrderByOccurredAtAscIdAsc(matchDayId).stream()
                .map(JpaMatchDayRepository::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<SourceSeason> findSourceSeasonsWithUnclosedDays() {
        Set<SourceSeason> result = new LinkedHashSet<>();
        for (Object[] row : days.findSourceSeasonsWithUnclosedDays()) {
            result.add(new SourceSeason((PipelineSource) row[0], (String) row[1]));
        }
        return result;
    }

    /** {@code from} and {@code to} compare with the first and last match dates (the grace period is not added). */
    @Override
    @Transactional(readOnly = true)
    public MatchDayPage query(MatchDayQuery query) {
        Specification<MatchDayEntity> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.source() != null) {
                predicates.add(cb.equal(root.get("source"), query.source()));
            }
            if (query.season() != null) {
                predicates.add(cb.equal(root.get("season"), query.season()));
            }
            if (query.state() != null) {
                predicates.add(cb.equal(root.get("state"), query.state()));
            }
            if (query.competition() != null) {
                predicates.add(cb.equal(root.get("competition"), query.competition()));
            }
            if (query.phase() != null) {
                predicates.add(cb.equal(root.get("phase"), query.phase()));
            }
            if (query.undated()) {
                predicates.add(cb.isNull(root.get("firstDate")));
            }
            if (query.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<java.time.LocalDate>get("lastDate"), query.from()));
            }
            if (query.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<java.time.LocalDate>get("firstDate"), query.to()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Sort order = Sort.by(
                Sort.Order.asc("firstDate").nullsLast(),
                Sort.Order.asc("competition"),
                Sort.Order.asc("groupNumber").nullsFirst(),
                Sort.Order.asc("phase").nullsFirst(),
                Sort.Order.asc("round"),
                Sort.Order.asc("id"));
        Page<MatchDayEntity> page = days.findAll(spec, PageRequest.of(query.page(), query.size(), order));
        List<MatchDayEntity> content = page.getContent();
        Map<UUID, Map<TrackedMatchStatus, Integer>> counts = new HashMap<>();
        Map<UUID, Map<TrackedMatchStatus, Integer>> ignored = new HashMap<>();
        if (!content.isEmpty()) {
            Set<UUID> ids = new LinkedHashSet<>();
            content.forEach(entity -> ids.add(entity.id));
            for (Object[] row : matches.countByDayAndStatus(ids)) {
                UUID dayId = (UUID) row[0];
                counts.computeIfAbsent(dayId, id -> new EnumMap<>(TrackedMatchStatus.class))
                        .put((TrackedMatchStatus) row[1], ((Number) row[2]).intValue());
                ignored.computeIfAbsent(dayId, id -> new EnumMap<>(TrackedMatchStatus.class))
                        .put((TrackedMatchStatus) row[1], ((Number) row[3]).intValue());
            }
        }
        List<MatchDaySummary> items = content.stream()
                .map(entity -> new MatchDaySummary(toDomain(entity), counts.getOrDefault(entity.id, Map.of()),
                        ignored.getOrDefault(entity.id, Map.of())))
                .toList();
        return new MatchDayPage(items, page.getTotalElements(), query.page(), query.size());
    }

    @Override
    @Transactional(readOnly = true)
    public MatchDayFacets facets(PipelineSource source, String season) {
        return new MatchDayFacets(distinct("season", source, null, false),
                distinct("competition", source, season, false), distinct("phase", source, season, true));
    }

    private List<String> distinct(String column, PipelineSource source, String season, boolean nonNull) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<String> cq = cb.createQuery(String.class);
        Root<MatchDayEntity> root = cq.from(MatchDayEntity.class);
        List<Predicate> predicates = new ArrayList<>();
        if (source != null) {
            predicates.add(cb.equal(root.get("source"), source));
        }
        if (season != null) {
            predicates.add(cb.equal(root.get("season"), season));
        }
        if (nonNull) {
            predicates.add(cb.isNotNull(root.get(column)));
        }
        cq.select(root.<String>get(column)).distinct(true).where(predicates.toArray(new Predicate[0]))
                .orderBy(cb.asc(root.get(column)));
        return entityManager.createQuery(cq).getResultList();
    }

    @Override
    public void apply(MatchDayChangeSet changes) {
        try {
            for (MatchDay day : changes.days()) {
                saveDay(day);
            }
            for (MatchTracking match : changes.matches()) {
                saveMatch(match);
            }
            if (!changes.removedMatchIds().isEmpty()) {
                matches.deleteAllByIdInBatch(changes.removedMatchIds());
            }
            for (MatchDayEvent event : changes.events()) {
                events.save(toEntity(event));
            }
            events.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new StaleMatchDayException("A match day or match changed concurrently", e);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e)) {
                throw new StaleMatchDayException("Another writer created the same match day or match", e);
            }
            throw e;
        }
    }

    private void saveDay(MatchDay day) {
        Optional<MatchDayEntity> stored = days.findById(day.id());
        MatchDayEntity entity = stored.orElseGet(() -> new MatchDayEntity(day.id()));
        if (stored.isPresent() && entity.version != day.version()) {
            throw new StaleMatchDayException("Match day " + day.id() + " changed concurrently; expected version "
                    + day.version());
        }
        copy(day, entity);
        days.saveAndFlush(entity);
    }

    private void saveMatch(MatchTracking match) {
        Optional<MatchTrackingEntity> stored = matches.findById(match.matchId());
        MatchTrackingEntity entity = stored.orElseGet(() -> new MatchTrackingEntity(match.matchId()));
        if (stored.isPresent() && entity.version != match.version()) {
            throw new StaleMatchDayException("Match " + match.matchId() + " changed concurrently; expected version "
                    + match.version());
        }
        copy(match, entity);
        matches.saveAndFlush(entity);
    }

    private static boolean isUniqueViolation(DataIntegrityViolationException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException hibernate
                    && UNIQUE_VIOLATION.equals(hibernate.getSQLState())) {
                return true;
            }
            if (t instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static void copy(MatchDay day, MatchDayEntity entity) {
        MatchDayKey key = day.key();
        entity.source = key.source();
        entity.season = key.season();
        entity.competition = key.competition();
        entity.groupNumber = key.groupNumber();
        entity.phase = key.phase();
        entity.round = key.round();
        entity.firstDate = day.window().firstDate();
        entity.lastDate = day.window().lastDate();
        entity.graceDays = day.window().graceDays();
        entity.state = day.state();
        entity.closeReason = day.closeReason();
        entity.closedAt = day.closedAt();
        entity.closedBy = day.closedBy();
        entity.openedAt = day.openedAt();
        entity.createdAt = day.createdAt();
        entity.lastRecomputedAt = day.lastRecomputedAt();
    }

    private static void copy(MatchTracking match, MatchTrackingEntity entity) {
        entity.matchDayId = match.matchDayId();
        entity.status = match.status();
        entity.matchDateTime = match.matchDateTime();
        entity.homeTeamName = match.homeTeamName();
        entity.awayTeamName = match.awayTeamName();
        entity.firstSeenAt = match.firstSeenAt();
        entity.statusChangedAt = match.statusChangedAt();
        entity.lastSeenAt = match.lastSeenAt();
        entity.reportedAt = match.reportedAt();
        entity.reportedRunId = match.reportedRunId();
        entity.ignoredAt = match.ignoredAt();
        entity.ignoredBy = match.ignoredBy();
    }

    private static MatchDayEventEntity toEntity(MatchDayEvent event) {
        MatchDayEventEntity entity = new MatchDayEventEntity(event.id());
        entity.matchDayId = event.matchDayId();
        entity.matchId = event.matchId();
        entity.kind = event.kind();
        entity.actor = event.actor();
        entity.occurredAt = event.occurredAt();
        entity.runId = event.runId();
        entity.note = event.note();
        return entity;
    }

    private static MatchDay toDomain(MatchDayEntity entity) {
        MatchDayKey key = new MatchDayKey(
                entity.source, entity.season, entity.competition, entity.groupNumber, entity.phase, entity.round);
        MatchDayWindow window = new MatchDayWindow(entity.firstDate, entity.lastDate, entity.graceDays);
        return MatchDay.restore(entity.id, key, window, entity.state, entity.closeReason, entity.closedAt,
                entity.closedBy, entity.openedAt, entity.createdAt, entity.lastRecomputedAt, entity.version);
    }

    private static MatchTracking toDomain(MatchTrackingEntity entity) {
        return MatchTracking.restore(entity.matchId, entity.matchDayId, entity.status, entity.matchDateTime,
                entity.homeTeamName, entity.awayTeamName, entity.firstSeenAt, entity.statusChangedAt,
                entity.lastSeenAt, entity.reportedAt, entity.reportedRunId, entity.ignoredAt, entity.ignoredBy,
                entity.version);
    }

    private static MatchDayEvent toDomain(MatchDayEventEntity entity) {
        return new MatchDayEvent(entity.id, entity.matchDayId, entity.matchId, entity.kind, entity.actor,
                entity.occurredAt, entity.runId, entity.note);
    }
}
