package org.cttelsamicsterrassa.data.pipeline.core.polling.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryMatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.CloseReason;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayChangeSet;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayKey;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayWindow;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import org.junit.jupiter.api.Test;

class ScopeBuilderTest {

    private static final String SEASON = "2026-2027";
    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");

    private final ScopeBuilder builder = new ScopeBuilder(BcnesaCompetitionNames.defaults());
    private final InMemoryMatchDayRepository repository = new InMemoryMatchDayRepository();

    private static IngestStatusRow fcttRow(String category, String group, String gender, int round) {
        return new IngestStatusRow(SEASON, category, group, "1a Fase", gender, "Barcelona", round, "scheduled", null,
                null);
    }

    private static IngestMatchDayStatus status(PipelineSource source, IngestStatusRow... rows) {
        return new IngestMatchDayStatus(source, SEASON, List.of(rows));
    }

    private static OpenMatchDay day(PipelineSource source, String competition, int group, int round) {
        MatchDayKey key = new MatchDayKey(source, SEASON, competition, group, null, round);
        UUID dayId = UUID.randomUUID();
        MatchTracking match = MatchTracking.first(UUID.randomUUID(), dayId, TrackedMatchStatus.SCHEDULED, NOW, "H",
                "A", NOW, null, null);
        return new OpenMatchDay(key, List.of(match));
    }

    private MatchDay store(MatchDay day, TrackedMatchStatus... statuses) {
        List<MatchTracking> matches = new ArrayList<>();
        for (TrackedMatchStatus status : statuses) {
            matches.add(MatchTracking.first(UUID.randomUUID(), day.id(), status, NOW, "H", "A", NOW,
                    status == TrackedMatchStatus.REPORTED ? NOW : null, null));
        }
        repository.apply(new MatchDayChangeSet(List.of(day), matches, java.util.Set.of(), List.of()));
        return day;
    }

    private static MatchDay newDay(String competition, int group, int round) {
        MatchDayKey key = new MatchDayKey(PipelineSource.FCTT, SEASON, competition, group, "1a Fase", round);
        return MatchDay.create(UUID.randomUUID(), key,
                new MatchDayWindow(LocalDate.parse("2026-10-03"), LocalDate.parse("2026-10-04"), 2), NOW);
    }

    @Test
    void mergesRoundsOfTheSameUnitIntoOneSortedFilter() {
        ScopeBuild build = builder.build(PipelineSource.FCTT, SEASON,
                List.of(day(PipelineSource.FCTT, "tercera-masculino", 1, 5),
                        day(PipelineSource.FCTT, "tercera-masculino", 1, 3)),
                status(PipelineSource.FCTT, fcttRow("tercera", "G1", "male", 3), fcttRow("tercera", "G1", "male", 4),
                        fcttRow("tercera", "G1", "male", 5)));

        assertThat(build.units()).hasSize(1);
        PollUnitScope unit = build.units().get(0);
        assertThat(unit.filter()).isEqualTo(
                new ScopeFilter("tercera", "G1", "1a Fase", "Barcelona", "male", List.of(3, 5)));
        assertThat(unit.candidates()).hasSize(2);
        assertThat(unit.scopeKey()).isEqualTo(unit.unit().scopeKey());
    }

    @Test
    void differentGroupsGiveDisjointUnits() {
        ScopeBuild build = builder.build(PipelineSource.FCTT, SEASON,
                List.of(day(PipelineSource.FCTT, "tercera-masculino", 1, 2),
                        day(PipelineSource.FCTT, "tercera-femenino", 1, 2),
                        day(PipelineSource.FCTT, "tercera-masculino", 2, 2)),
                status(PipelineSource.FCTT, fcttRow("tercera", "G1", "male", 2), fcttRow("tercera", "G1", "female", 2),
                        fcttRow("tercera", "G2", "male", 2)));

        assertThat(build.units()).hasSize(3);
        assertThat(build.units()).extracting(PollUnitScope::scopeKey).doesNotHaveDuplicates();
        assertThat(build.runScope().filters()).hasSize(3);
    }

    @Test
    void rfetmUnitsAreOnePerCategoryWithTheUnionOfTheirRounds() {
        IngestStatusRow groupOne = new IngestStatusRow(SEASON, "super-divisio", "1", null, "masculino", null, 4,
                "scheduled", null, null);
        IngestStatusRow groupTwo = new IngestStatusRow(SEASON, "super-divisio", "2", null, "femenino", null, 6,
                "scheduled", null, null);

        ScopeBuild build = builder.build(PipelineSource.RFETM, SEASON,
                List.of(day(PipelineSource.RFETM, "super-divisio-masculino", 1, 4),
                        day(PipelineSource.RFETM, "super-divisio-femenino", 2, 6)),
                status(PipelineSource.RFETM, groupOne, groupTwo));

        assertThat(build.units()).hasSize(1);
        assertThat(build.units().get(0).filter())
                .isEqualTo(new ScopeFilter("super-divisio", null, null, null, null, List.of(4, 6)));
    }

    @Test
    void anOpenDayWithoutAStatusRowFailsTheBuildListingTheKeys() {
        assertThatThrownBy(() -> builder.build(PipelineSource.FCTT, SEASON,
                List.of(day(PipelineSource.FCTT, "tercera-masculino", 1, 2),
                        day(PipelineSource.FCTT, "tercera-masculino", 1, 9)),
                status(PipelineSource.FCTT, fcttRow("tercera", "G1", "male", 2))))
                .isInstanceOfSatisfying(ScopeBuildException.class, e -> {
                    assertThat(e.code()).isEqualTo(ScopeBuildException.SCOPE_UNMATCHED);
                    assertThat(e.getMessage()).contains("1 open match day", "tercera-masculino group 1", "round 9")
                            .doesNotContain("round 2");
                });
    }

    @Test
    void theErrorNamesAtMostTenKeysPlusACount() {
        List<OpenMatchDay> open = new ArrayList<>();
        for (int round = 1; round <= 13; round++) {
            open.add(day(PipelineSource.FCTT, "tercera-masculino", 1, round));
        }

        assertThatThrownBy(() -> builder.build(PipelineSource.FCTT, SEASON, open, status(PipelineSource.FCTT)))
                .isInstanceOfSatisfying(ScopeBuildException.class, e -> assertThat(e.getMessage())
                        .contains("13 open match day", "round 10", "and 3 more").doesNotContain("round 11"));
    }

    @Test
    void rowsOfOtherSeasonsAreIgnored() {
        IngestStatusRow old = new IngestStatusRow("2025-2026", "tercera", "G1", "1a Fase", "male", null, 2,
                "scheduled", null, null);

        assertThatThrownBy(() -> builder.build(PipelineSource.FCTT, SEASON,
                List.of(day(PipelineSource.FCTT, "tercera-masculino", 1, 2)), status(PipelineSource.FCTT, old)))
                .isInstanceOf(ScopeBuildException.class);
    }

    @Test
    void noOpenDaysGiveNoUnits() {
        assertThat(builder.build(PipelineSource.FCTT, SEASON, List.of(), status(PipelineSource.FCTT)).units())
                .isEmpty();
    }

    @Test
    void scopeKeyIsStableWhenARoundIsAdded() {
        IngestMatchDayStatus status = status(PipelineSource.FCTT, fcttRow("tercera", "G1", "male", 2),
                fcttRow("tercera", "G1", "male", 3));

        String one = builder.build(PipelineSource.FCTT, SEASON, List.of(day(PipelineSource.FCTT, "tercera-masculino", 1, 2)),
                status).units().get(0).scopeKey();
        String two = builder.build(PipelineSource.FCTT, SEASON,
                List.of(day(PipelineSource.FCTT, "tercera-masculino", 1, 2),
                        day(PipelineSource.FCTT, "tercera-masculino", 1, 3)),
                status).units().get(0).scopeKey();

        assertThat(one).isEqualTo(two);
    }

    @Test
    void openMatchDaysKeepOpenDaysAndTheirUnresolvedMatchesOnly() {
        MatchDay open = store(newDay("tercera-masculino", 1, 1).open(NOW), TrackedMatchStatus.SCHEDULED,
                TrackedMatchStatus.AWAITING_RESULT, TrackedMatchStatus.REPORTED);

        List<OpenMatchDay> days = OpenMatchDays.load(repository, PipelineSource.FCTT, SEASON);

        assertThat(days).hasSize(1);
        assertThat(days.get(0).key()).isEqualTo(open.key());
        assertThat(days.get(0).candidates()).extracting(MatchTracking::status)
                .containsExactlyInAnyOrder(TrackedMatchStatus.SCHEDULED, TrackedMatchStatus.AWAITING_RESULT);
    }

    @Test
    void aPostponedMatchOnADayClosedAsAllResolvedStaysPolled() {
        MatchDay closed = newDay("tercera-masculino", 1, 2).open(NOW).close(CloseReason.ALL_RESOLVED, "system", NOW);
        store(closed, TrackedMatchStatus.REPORTED, TrackedMatchStatus.POSTPONED);

        List<OpenMatchDay> days = OpenMatchDays.load(repository, PipelineSource.FCTT, SEASON);

        assertThat(days).hasSize(1);
        assertThat(days.get(0).candidates()).extracting(MatchTracking::status)
                .containsExactly(TrackedMatchStatus.POSTPONED);
    }

    @Test
    void manuallyClosedRemovedUpcomingAndFullyReportedDaysAreNotPolled() {
        store(newDay("a-masculino", 1, 1).open(NOW).close(CloseReason.MANUAL, "ops", NOW),
                TrackedMatchStatus.POSTPONED);
        store(newDay("b-masculino", 1, 1).open(NOW).close(CloseReason.REMOVED, "system", NOW),
                TrackedMatchStatus.SCHEDULED);
        store(newDay("c-masculino", 1, 1), TrackedMatchStatus.SCHEDULED);
        store(newDay("d-masculino", 1, 1).open(NOW).close(CloseReason.ALL_RESOLVED, "system", NOW),
                TrackedMatchStatus.REPORTED);

        assertThat(OpenMatchDays.load(repository, PipelineSource.FCTT, SEASON)).isEmpty();
    }

    @Test
    void ignoredMatchesAreNotCandidates() {
        MatchDay open = newDay("tercera-masculino", 1, 1).open(NOW);
        MatchTracking ignored = MatchTracking.first(UUID.randomUUID(), open.id(), TrackedMatchStatus.SCHEDULED, NOW,
                "H", "A", NOW, null, null).ignore("ops", NOW);
        repository.apply(new MatchDayChangeSet(List.of(open), List.of(ignored), java.util.Set.of(), List.of()));

        assertThat(OpenMatchDays.load(repository, PipelineSource.FCTT, SEASON)).isEmpty();
    }
}
