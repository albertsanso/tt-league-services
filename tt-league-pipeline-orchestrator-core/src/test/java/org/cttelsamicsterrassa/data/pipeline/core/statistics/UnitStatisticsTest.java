package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.SETTINGS;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.at;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryDailyStatsRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryMatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryStatisticsReadRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus;
import org.junit.jupiter.api.Test;

class UnitStatisticsTest {

    private static final Instant FINISHED = at("2026-10-06T10:00:00Z");
    private static final DateRange WEEK = new DateRange(LocalDate.parse("2026-10-04"), LocalDate.parse("2026-10-10"));

    private final InMemoryStatisticsReadRepository reads = new InMemoryStatisticsReadRepository();
    private final StatisticsQueries queries = new StatisticsQueries(reads, new InMemoryDailyStatsRepository(),
            new InMemoryMatchDayRepository(), new FakeRunClock(at("2026-10-10T08:00:00Z")), SETTINGS);

    private static UnitFacts unit(PipelineSource source, String key, String label, UnitStatus status,
            Instant finished, Long seconds) {
        return new UnitFacts(UUID.randomUUID(), source, key, label, status,
                seconds == null ? null : finished.minusSeconds(seconds), finished);
    }

    @Test
    void unitOutcomesCountEachStatusPerSourceAndKeyAndAverageTheRunDurations() {
        List<UnitOutcomeStats.UnitOutcomes> outcomes = StatisticsRules.unitOutcomes(List.of(
                unit(PipelineSource.BCNESA, "k1", "old label", UnitStatus.SUCCEEDED, FINISHED.minusSeconds(100), 60L),
                unit(PipelineSource.BCNESA, "k1", "G1", UnitStatus.FAILED, FINISHED, 120L),
                unit(PipelineSource.BCNESA, "k1", "G1", UnitStatus.PARTIAL, FINISHED.minusSeconds(50), 30L),
                unit(PipelineSource.BCNESA, "k1", "G1", UnitStatus.NO_CHANGES, FINISHED.minusSeconds(40), 30L),
                unit(PipelineSource.BCNESA, "k1", "G1", UnitStatus.SKIPPED, FINISHED.minusSeconds(30), null),
                unit(PipelineSource.BCNESA, "k2", "G2", UnitStatus.SUCCEEDED, FINISHED, 10L),
                unit(PipelineSource.RFETM, "k1", "Same key other source", UnitStatus.FAILED, FINISHED, 20L)));

        assertThat(outcomes).hasSize(3);
        UnitOutcomeStats.UnitOutcomes first = outcomes.get(0);
        assertThat(first.source()).isEqualTo(PipelineSource.RFETM);
        assertThat(first.unitKey()).isEqualTo("k1");
        assertThat(first.failed()).isEqualTo(1);

        UnitOutcomeStats.UnitOutcomes bcnesaK1 = outcomes.get(1);
        assertThat(bcnesaK1.source()).isEqualTo(PipelineSource.BCNESA);
        assertThat(bcnesaK1.unitKey()).isEqualTo("k1");
        assertThat(bcnesaK1.label()).isEqualTo("G1");
        assertThat(bcnesaK1.succeeded()).isEqualTo(1);
        assertThat(bcnesaK1.failed()).isEqualTo(1);
        assertThat(bcnesaK1.partial()).isEqualTo(1);
        assertThat(bcnesaK1.noChanges()).isEqualTo(1);
        assertThat(bcnesaK1.skipped()).isEqualTo(1);
        // (60 + 120 + 30 + 30) / 4; the skipped unit never ran
        assertThat(bcnesaK1.averageDuration()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void aUnitKeyThatNeverRanHasNoAverage() {
        List<UnitOutcomeStats.UnitOutcomes> outcomes = StatisticsRules.unitOutcomes(
                List.of(unit(PipelineSource.FCTT, "k", "G", UnitStatus.SKIPPED, FINISHED, null)));

        assertThat(outcomes).singleElement().satisfies(row -> {
            assertThat(row.skipped()).isEqualTo(1);
            assertThat(row.averageDuration()).isNull();
        });
    }

    @Test
    void noUnitsGiveNoRows() {
        assertThat(StatisticsRules.unitOutcomes(List.of())).isEmpty();
    }

    @Test
    void theQueryReadsTheRangeAndSourcesAndAppliesTheUnitFilter() {
        reads.add(unit(PipelineSource.BCNESA, "k1", "G1", UnitStatus.FAILED, FINISHED, 10L));
        reads.add(unit(PipelineSource.BCNESA, "k2", "G2", UnitStatus.SUCCEEDED, FINISHED, 10L));
        reads.add(unit(PipelineSource.RFETM, "k1", "R1", UnitStatus.SUCCEEDED, FINISHED, 10L));
        reads.add(unit(PipelineSource.BCNESA, "k1", "G1", UnitStatus.FAILED, at("2026-11-01T10:00:00Z"), 10L));

        UnitOutcomeStats all = queries.units(WEEK, Set.of(), Optional.empty());
        UnitOutcomeStats bcnesa = queries.units(WEEK, Set.of(PipelineSource.BCNESA), Optional.empty());
        UnitOutcomeStats onlyK1 = queries.units(WEEK, Set.of(), Optional.of("k1"));

        assertThat(all.units()).hasSize(3);
        assertThat(bcnesa.units()).extracting(UnitOutcomeStats.UnitOutcomes::unitKey).containsExactly("k1", "k2");
        assertThat(onlyK1.units()).extracting(UnitOutcomeStats.UnitOutcomes::source)
                .containsExactly(PipelineSource.RFETM, PipelineSource.BCNESA);
    }

    @Test
    void runOutcomesAndStepAveragesHonourTheUnitFilter() {
        UUID withK1 = UUID.randomUUID();
        UUID withoutK1 = UUID.randomUUID();
        reads.add(new RunFacts(withK1, PipelineSource.BCNESA, RunStatus.PARTIAL, FINISHED.minusSeconds(60), FINISHED));
        reads.add(new RunFacts(withoutK1, PipelineSource.BCNESA, RunStatus.SUCCEEDED, FINISHED.minusSeconds(60),
                FINISHED));
        reads.add(new UnitFacts(withK1, PipelineSource.BCNESA, "k1", "G1", UnitStatus.FAILED,
                FINISHED.minusSeconds(30), FINISHED));
        reads.add(new UnitFacts(withoutK1, PipelineSource.BCNESA, "k2", "G2", UnitStatus.SUCCEEDED,
                FINISHED.minusSeconds(30), FINISHED));
        reads.add(new StepFacts(withK1, "k1", PipelineSource.BCNESA, StepKind.INGEST, StepStatus.SUCCEEDED, null,
                FINISHED.minusSeconds(100), FINISHED, null));
        reads.add(new StepFacts(withoutK1, "k2", PipelineSource.BCNESA, StepKind.INGEST, StepStatus.SUCCEEDED, null,
                FINISHED.minusSeconds(20), FINISHED, null));

        RunOutcomeStats unfiltered = queries.runs(WEEK, Set.of());
        RunOutcomeStats filtered = queries.runs(WEEK, Set.of(), Optional.of("k1"));

        assertThat(unfiltered.days()).singleElement().satisfies(day -> {
            assertThat(day.succeeded()).isEqualTo(1);
            assertThat(day.partial()).isEqualTo(1);
        });
        assertThat(unfiltered.stepAverages()).singleElement()
                .satisfies(average -> assertThat(average.attempts()).isEqualTo(2));
        assertThat(filtered.days()).singleElement().satisfies(day -> {
            assertThat(day.succeeded()).isZero();
            assertThat(day.partial()).isEqualTo(1);
        });
        assertThat(filtered.stepAverages()).singleElement().satisfies(average -> {
            assertThat(average.attempts()).isEqualTo(1);
            assertThat(average.average()).isEqualTo(Duration.ofSeconds(100));
        });
    }
}
