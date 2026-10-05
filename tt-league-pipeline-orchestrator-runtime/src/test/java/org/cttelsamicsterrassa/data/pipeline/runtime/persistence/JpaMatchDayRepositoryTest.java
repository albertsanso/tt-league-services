package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.CloseReason;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayChangeSet;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayCompletion;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayEvent;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayEventKind;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayFacets;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayKey;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayPage;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayQuery;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayWindow;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.SourceSeason;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.MatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.StaleMatchDayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JpaMatchDayRepositoryTest extends AbstractPersistenceTest {

    private static final String SEASON = "2026-2027";

    @Autowired
    MatchDayRepository repository;
    @Autowired
    PipelineRunRepository runs;

    private static MatchDayKey key(PipelineSource source, Integer group, String phase, int round) {
        return new MatchDayKey(source, SEASON, "TERCERA", group, phase, round);
    }

    private static MatchDay day(MatchDayKey key, LocalDate first, LocalDate last) {
        MatchDayWindow window = first == null ? MatchDayWindow.undated(2) : new MatchDayWindow(first, last, 2);
        return MatchDay.create(UUID.randomUUID(), key, window, T0);
    }

    private static MatchDay day(int round, String first) {
        LocalDate date = first == null ? null : LocalDate.parse(first);
        return day(key(PipelineSource.FCTT, 1, "1a Fase", round), date, date);
    }

    private static MatchTracking match(MatchDay day, TrackedMatchStatus status) {
        Instant reported = status == TrackedMatchStatus.REPORTED ? T0 : null;
        return MatchTracking.first(UUID.randomUUID(), day.id(), status, T0, "Home", "Away", T0, reported, null);
    }

    private static MatchDayChangeSet changes(List<MatchDay> days, List<MatchTracking> matches,
            List<MatchDayEvent> events) {
        return new MatchDayChangeSet(days, matches, Set.of(), events);
    }

    private static MatchDayEvent event(MatchDay day, UUID matchId, MatchDayEventKind kind) {
        return MatchDayEvent.of(day.id(), matchId, kind, "ana", T0, null, kind == MatchDayEventKind.NOTE ? "n" : null);
    }

    @Test
    void appliesNewDaysMatchesAndEventsAndReadsEveryFieldBack() {
        PipelineRun run = runs.create(queued(PipelineSource.FCTT));
        MatchDay day = day(key(PipelineSource.FCTT, null, null, 3), LocalDate.parse("2026-10-04"),
                LocalDate.parse("2026-10-05")).open(T0);
        MatchTracking reported = MatchTracking.first(UUID.randomUUID(), day.id(), TrackedMatchStatus.REPORTED, T0,
                "CTT A", "CTT B", T0, T0.plusSeconds(5), run.id());
        MatchTracking ignored = match(day, TrackedMatchStatus.OVERDUE).ignore("ana", T0.plusSeconds(9));
        MatchDayEvent opened = MatchDayEvent.of(day.id(), null, MatchDayEventKind.OPENED, "system:tracker", T0,
                run.id(), "note");

        repository.apply(changes(List.of(day), List.of(reported, ignored), List.of(opened)));

        assertThat(repository.findById(day.id())).get().usingRecursiveComparison().isEqualTo(day);
        assertThat(repository.findBySourceAndSeason(PipelineSource.FCTT, SEASON)).hasSize(1);
        assertThat(repository.findBySourceAndSeason(PipelineSource.RFETM, SEASON)).isEmpty();
        assertThat(repository.findMatches(List.of(day.id()))).usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrder(reported, ignored);
        assertThat(repository.findMatch(reported.matchId())).get().usingRecursiveComparison().isEqualTo(reported);
        assertThat(repository.findEvents(day.id())).containsExactly(opened);
        assertThat(repository.findMatches(List.of())).isEmpty();
    }

    @Test
    void updatesWithMatchingVersionsAndIncrementsThem() {
        MatchDay day = day(1, "2026-10-04");
        MatchTracking match = match(day, TrackedMatchStatus.SCHEDULED);
        repository.apply(changes(List.of(day), List.of(match), List.of()));

        MatchDay loaded = repository.findById(day.id()).orElseThrow();
        MatchTracking loadedMatch = repository.findMatch(match.matchId()).orElseThrow();
        assertThat(loaded.version()).isZero();
        repository.apply(changes(List.of(loaded.open(T0.plusSeconds(1)).recomputed(T0.plusSeconds(1))),
                List.of(loadedMatch.ignore("ana", T0.plusSeconds(1))), List.of()));

        MatchDay updated = repository.findById(day.id()).orElseThrow();
        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.state()).isEqualTo(MatchDayState.OPEN);
        assertThat(repository.findMatch(match.matchId()).orElseThrow().isIgnored()).isTrue();
        assertThat(repository.findMatch(match.matchId()).orElseThrow().version()).isEqualTo(1);
    }

    @Test
    void aStaleDayVersionIsRejectedAndNothingIsWritten() {
        MatchDay day = day(1, "2026-10-04");
        repository.apply(changes(List.of(day), List.of(), List.of()));
        MatchDay first = repository.findById(day.id()).orElseThrow();
        repository.apply(changes(List.of(first.recomputed(T0.plusSeconds(1))), List.of(), List.of()));
        MatchTracking newMatch = match(day, TrackedMatchStatus.OVERDUE);

        assertThatThrownBy(() -> repository.apply(changes(List.of(first.recomputed(T0.plusSeconds(2))),
                List.of(newMatch), List.of(event(day, null, MatchDayEventKind.NOTE)))))
                .isInstanceOf(StaleMatchDayException.class);

        assertThat(repository.findById(day.id()).orElseThrow().lastRecomputedAt()).isEqualTo(T0.plusSeconds(1));
        assertThat(repository.findMatch(newMatch.matchId())).isEmpty();
        assertThat(repository.findEvents(day.id())).isEmpty();
    }

    @Test
    void aStaleMatchVersionRollsBackTheDayChangeToo() {
        MatchDay day = day(1, "2026-10-04");
        MatchTracking match = match(day, TrackedMatchStatus.SCHEDULED);
        repository.apply(changes(List.of(day), List.of(match), List.of()));
        MatchDay loadedDay = repository.findById(day.id()).orElseThrow();
        MatchTracking loadedMatch = repository.findMatch(match.matchId()).orElseThrow();
        repository.apply(changes(List.of(), List.of(loadedMatch.ignore("ana", T0)), List.of()));

        assertThatThrownBy(() -> repository.apply(changes(List.of(loadedDay.open(T0.plusSeconds(3))),
                List.of(loadedMatch.ignore("bea", T0)), List.of())))
                .isInstanceOf(StaleMatchDayException.class);

        assertThat(repository.findById(day.id()).orElseThrow().state()).isEqualTo(MatchDayState.UPCOMING);
        assertThat(repository.findMatch(match.matchId()).orElseThrow().ignoredBy()).isEqualTo("ana");
    }

    @Test
    void aSecondMatchDayWithTheSameKeyIsRejected() {
        MatchDayKey key = key(PipelineSource.FCTT, null, null, 1);
        repository.apply(changes(List.of(day(key, null, null)), List.of(), List.of()));

        assertThatThrownBy(() -> repository.apply(changes(List.of(day(key, null, null)), List.of(), List.of())))
                .isInstanceOf(StaleMatchDayException.class);

        assertThat(repository.findBySourceAndSeason(PipelineSource.FCTT, SEASON)).hasSize(1);
    }

    @Test
    void removesMatchesAndKeepsTheirEvents() {
        MatchDay day = day(1, "2026-10-04");
        MatchTracking gone = match(day, TrackedMatchStatus.SCHEDULED);
        MatchTracking kept = match(day, TrackedMatchStatus.SCHEDULED);
        repository.apply(changes(List.of(day), List.of(gone, kept), List.of()));

        repository.apply(new MatchDayChangeSet(List.of(), List.of(), Set.of(gone.matchId()),
                List.of(event(day, gone.matchId(), MatchDayEventKind.MATCH_REMOVED))));

        assertThat(repository.findMatches(List.of(day.id()))).extracting(MatchTracking::matchId)
                .containsExactly(kept.matchId());
        assertThat(repository.findEvents(day.id())).singleElement()
                .satisfies(e -> assertThat(e.matchId()).isEqualTo(gone.matchId()));
    }

    @Test
    void movesAMatchToAnotherMatchDay() {
        MatchDay round1 = day(1, "2026-10-04");
        MatchDay round2 = day(2, "2026-10-11");
        MatchTracking match = match(round1, TrackedMatchStatus.SCHEDULED);
        repository.apply(changes(List.of(round1, round2), List.of(match), List.of()));

        MatchTracking loaded = repository.findMatch(match.matchId()).orElseThrow();
        repository.apply(changes(List.of(), List.of(loaded.moveTo(round2.id())), List.of()));

        assertThat(repository.findMatches(List.of(round1.id()))).isEmpty();
        assertThat(repository.findMatches(List.of(round2.id()))).hasSize(1);
    }

    @Test
    void eventsAreOldestFirst() {
        MatchDay day = day(1, "2026-10-04");
        MatchDayEvent later = MatchDayEvent.of(day.id(), null, MatchDayEventKind.CLOSED, "ana", T0.plusSeconds(60),
                null, null);
        MatchDayEvent earlier = MatchDayEvent.of(day.id(), null, MatchDayEventKind.OPENED, "ana", T0, null, null);

        repository.apply(changes(List.of(day), List.of(), List.of(later, earlier)));

        assertThat(repository.findEvents(day.id())).extracting(MatchDayEvent::kind)
                .containsExactly(MatchDayEventKind.OPENED, MatchDayEventKind.CLOSED);
    }

    @Test
    void findsSourceSeasonsWithUnclosedDaysOnly() {
        MatchDay open = day(1, "2026-10-04");
        MatchDay closed = day(key(PipelineSource.RFETM, 1, null, 1), LocalDate.parse("2026-10-04"),
                LocalDate.parse("2026-10-04")).close(CloseReason.MANUAL, "ana", T0);
        MatchDay otherSeason = MatchDay.create(UUID.randomUUID(),
                new MatchDayKey(PipelineSource.BCNESA, "2025-2026", "DH", null, null, 1), MatchDayWindow.undated(2), T0);
        repository.apply(changes(List.of(open, closed, otherSeason), List.of(), List.of()));

        assertThat(repository.findSourceSeasonsWithUnclosedDays()).containsExactlyInAnyOrder(
                new SourceSeason(PipelineSource.FCTT, SEASON), new SourceSeason(PipelineSource.BCNESA, "2025-2026"));
    }

    @Test
    void queryFiltersSortsPagesAndCountsMatches() {
        MatchDay round1 = day(1, "2026-09-27");
        MatchDay round2 = day(2, "2026-10-04").open(T0);
        MatchDay round3 = day(3, null);
        MatchDay rfetm = day(key(PipelineSource.RFETM, 1, null, 1), LocalDate.parse("2026-10-04"),
                LocalDate.parse("2026-10-04"));
        MatchTracking overdue = match(round2, TrackedMatchStatus.OVERDUE).ignore("ana", T0);
        repository.apply(changes(List.of(round3, round2, rfetm, round1),
                List.of(overdue, match(round2, TrackedMatchStatus.REPORTED), match(round2, TrackedMatchStatus.OVERDUE)),
                List.of()));

        MatchDayPage all = repository.query(new MatchDayQuery(null, null, null, null, null, false, null, null, 0, 10));
        assertThat(all.total()).isEqualTo(4);
        assertThat(all.items()).extracting(item -> item.day().id())
                .containsExactly(round1.id(), rfetm.id(), round2.id(), round3.id());

        MatchDayPage fctt = repository.query(new MatchDayQuery(PipelineSource.FCTT, SEASON, null, null, null, false, null, null, 0, 10));
        assertThat(fctt.items()).hasSize(3);
        assertThat(repository.query(new MatchDayQuery(null, null, MatchDayState.OPEN, null, null, false, null, null, 0, 10)).items())
                .extracting(item -> item.day().id()).containsExactly(round2.id());
        assertThat(repository.query(new MatchDayQuery(null, null, null, null, null, false,
                LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-04"), 0, 10)).items()).extracting(item -> item.day().id())
                .containsExactly(rfetm.id(), round2.id());
        assertThat(repository.query(new MatchDayQuery(null, null, null, null, null, false, LocalDate.parse("2026-10-05"), null, 0, 10))
                .total()).isZero();

        MatchDayPage second = repository.query(new MatchDayQuery(null, null, null, null, null, false, null, null, 1, 3));
        assertThat(second.total()).isEqualTo(4);
        assertThat(second.items()).hasSize(1);

        var summary = all.items().stream().filter(item -> item.day().id().equals(round2.id())).findFirst()
                .orElseThrow();
        assertThat(summary.countsByStatus()).containsEntry(TrackedMatchStatus.OVERDUE, 2)
                .containsEntry(TrackedMatchStatus.REPORTED, 1).containsEntry(TrackedMatchStatus.SCHEDULED, 0);
        assertThat(summary.ignoredCount()).isEqualTo(1);
        assertThat(all.items().get(0).ignoredCount()).isZero();
    }

    @Test
    void queryFiltersByCompetitionPhaseAndUndated() {
        MatchDay tercera = day(1, "2026-10-04");
        MatchDay segunda = day(new MatchDayKey(PipelineSource.FCTT, SEASON, "SEGUNDA", 1, "2a Fase", 1),
                LocalDate.parse("2026-10-04"), LocalDate.parse("2026-10-04"));
        MatchDay undated = day(2, null);
        repository.apply(changes(List.of(tercera, segunda, undated), List.of(), List.of()));

        assertThat(repository.query(new MatchDayQuery(null, null, null, "SEGUNDA", null, false, null, null, 0, 10))
                .items()).extracting(item -> item.day().id()).containsExactly(segunda.id());
        assertThat(repository.query(new MatchDayQuery(null, null, null, null, "1a Fase", false, null, null, 0, 10))
                .items()).extracting(item -> item.day().id()).containsExactly(tercera.id(), undated.id());
        assertThat(repository.query(new MatchDayQuery(null, null, null, null, null, true, null, null, 0, 10))
                .items()).extracting(item -> item.day().id()).containsExactly(undated.id());
        assertThat(repository.query(new MatchDayQuery(null, null, null, "TERCERA", "2a Fase", false, null, null, 0, 10))
                .total()).isZero();
    }

    @Test
    void facetsAreDistinctSortedAndScopedBySourceAndSeason() {
        MatchDay fctt = day(1, "2026-10-04");
        MatchDay fcttSegunda = day(new MatchDayKey(PipelineSource.FCTT, SEASON, "SEGUNDA", 1, "2a Fase", 1),
                LocalDate.parse("2026-10-04"), LocalDate.parse("2026-10-04"));
        MatchDay rfetm = day(new MatchDayKey(PipelineSource.RFETM, SEASON, "SUPER", 1, null, 1),
                LocalDate.parse("2026-10-04"), LocalDate.parse("2026-10-04"));
        MatchDay older = MatchDay.create(UUID.randomUUID(),
                new MatchDayKey(PipelineSource.FCTT, "2025-2026", "OLD", 1, "Fase Vieja", 1), MatchDayWindow.undated(2),
                T0);
        repository.apply(changes(List.of(fctt, fcttSegunda, rfetm, older), List.of(), List.of()));

        MatchDayFacets everything = repository.facets(null, null);
        assertThat(everything.seasons()).containsExactly("2025-2026", SEASON);
        assertThat(everything.competitions()).containsExactly("OLD", "SEGUNDA", "SUPER", "TERCERA");
        assertThat(everything.phases()).containsExactly("1a Fase", "2a Fase", "Fase Vieja");

        MatchDayFacets fcttSeason = repository.facets(PipelineSource.FCTT, SEASON);
        assertThat(fcttSeason.seasons()).containsExactly("2025-2026", SEASON);
        assertThat(fcttSeason.competitions()).containsExactly("SEGUNDA", "TERCERA");
        assertThat(fcttSeason.phases()).containsExactly("1a Fase", "2a Fase");

        MatchDayFacets rfetmFacets = repository.facets(PipelineSource.RFETM, SEASON);
        assertThat(rfetmFacets.seasons()).containsExactly(SEASON);
        assertThat(rfetmFacets.competitions()).containsExactly("SUPER");
        assertThat(rfetmFacets.phases()).isEmpty();
    }

    @Test
    void summariesKeepTheIgnoredCountPerStatus() {
        MatchDay open = day(1, "2026-10-04").open(T0);
        MatchTracking ignoredOverdue = match(open, TrackedMatchStatus.OVERDUE).ignore("ana", T0);
        MatchTracking ignoredReported = match(open, TrackedMatchStatus.REPORTED).ignore("ana", T0);
        repository.apply(changes(List.of(open),
                List.of(ignoredOverdue, ignoredReported, match(open, TrackedMatchStatus.REPORTED),
                        match(open, TrackedMatchStatus.AWAITING_RESULT)),
                List.of()));

        var summary = repository.query(new MatchDayQuery(null, null, null, null, null, false, null, null, 0, 10))
                .items().get(0);

        assertThat(summary.ignoredByStatus()).containsEntry(TrackedMatchStatus.OVERDUE, 1)
                .containsEntry(TrackedMatchStatus.REPORTED, 1).containsEntry(TrackedMatchStatus.AWAITING_RESULT, 0);
        assertThat(summary.totalCount()).isEqualTo(2);
        assertThat(summary.reportedCount()).isEqualTo(1);
        assertThat(summary.completion()).isEqualTo(MatchDayCompletion.IN_PROGRESS);
    }

    @Test
    void refreshRequestedEventsAreStoredWithTheirRun() {
        PipelineRun run = runs.create(queued(PipelineSource.FCTT));
        MatchDay open = day(1, "2026-10-04").open(T0);
        repository.apply(changes(List.of(open), List.of(), List.of()));

        repository.apply(changes(List.of(repository.findById(open.id()).orElseThrow()), List.of(),
                List.of(MatchDayEvent.of(open.id(), null, MatchDayEventKind.REFRESH_REQUESTED, "ana", T0, run.id(),
                        null))));

        assertThat(repository.findEvents(open.id())).singleElement().satisfies(event -> {
            assertThat(event.kind()).isEqualTo(MatchDayEventKind.REFRESH_REQUESTED);
            assertThat(event.runId()).isEqualTo(run.id());
        });
        assertThat(runs.findByIds(List.of(run.id(), UUID.randomUUID()))).extracting(PipelineRun::id)
                .containsExactly(run.id());
        assertThat(runs.findByIds(List.of())).isEmpty();
    }
}
