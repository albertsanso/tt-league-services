package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PolicyLevel;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollDecision;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollSchedule;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollScheduleRepository;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.StalePollScheduleException;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JpaPollScheduleRepositoryTest extends AbstractPersistenceTest {

    private static final String SEASON = "2026-2027";

    @Autowired
    PollScheduleRepository schedules;
    @Autowired
    PipelineRunRepository runs;

    private static PollDecision open() {
        return new PollDecision(PolicyLevel.OPEN, Duration.ofHours(24), Duration.ofHours(24), T0.plusSeconds(60), null,
                0);
    }

    private static PollSchedule group(PipelineSource source, String scopeKey) {
        return PollSchedule.group(UUID.randomUUID(), source, SEASON, scopeKey,
                new ScopeFilter("tercera", "G1", "1a Fase", "Barcelona", "male", List.of(2, 5)), open(), T0);
    }

    private static PollSchedule fullRefresh(PipelineSource source) {
        return PollSchedule.fullRefresh(UUID.randomUUID(), source, SEASON,
                new PollDecision(PolicyLevel.FULL_REFRESH, Duration.ofDays(7), Duration.ofDays(7), T0, null, 0));
    }

    @Test
    void insertsAndReadsBackEveryField() {
        PollSchedule saved = schedules.save(group(PipelineSource.FCTT, "abc"));

        assertThat(saved.version()).isZero();
        PollSchedule read = schedules.findById(saved.id()).orElseThrow();
        assertThat(read).usingRecursiveComparison().isEqualTo(saved);
        assertThat(read.filter()).isEqualTo(
                new ScopeFilter("tercera", "G1", "1a Fase", "Barcelona", "male", List.of(2, 5)));
        assertThat(read.interval()).isEqualTo(Duration.ofHours(24));
        assertThat(read.nextRunAt()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void theFullRefreshUnitHasNoFilter() {
        PollSchedule saved = schedules.save(fullRefresh(PipelineSource.RFETM));

        PollSchedule read = schedules.findById(saved.id()).orElseThrow();
        assertThat(read.isFullRefresh()).isTrue();
        assertThat(read.filter()).isNull();
        assertThat(read.level()).isEqualTo(PolicyLevel.FULL_REFRESH);
        assertThat(read.interval()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void updatesIncreaseTheVersionAndKeepTheState() {
        PollSchedule saved = schedules.save(group(PipelineSource.FCTT, "abc"));
        PipelineRun run = runs.create(queued(PipelineSource.FCTT));

        PollSchedule launched = schedules.save(saved.launched(run.id(), T0.plusSeconds(5)));
        PollSchedule finished = schedules.save(launched.outcome(RunStatus.NO_CHANGES, T0.plusSeconds(90)));

        assertThat(launched.version()).isEqualTo(1);
        assertThat(launched.pendingRunId()).isEqualTo(run.id());
        assertThat(finished.version()).isEqualTo(2);
        assertThat(finished.pendingRunId()).isNull();
        assertThat(finished.lastRunAt()).isEqualTo(T0.plusSeconds(90));
        assertThat(finished.consecutiveNoChange()).isEqualTo(1);
    }

    @Test
    void aStoppedUnitKeepsItsStopStateAndHasNoInterval() {
        PollSchedule saved = schedules.save(group(PipelineSource.FCTT, "abc"));
        PollSchedule stopped = saved.decided(
                new PollDecision(PolicyLevel.STOPPED, null, null, null, PollDecision.OVERDUE_LIMIT, 0), T0);

        PollSchedule read = schedules.findById(schedules.save(stopped).id()).orElseThrow();

        assertThat(read.isStopped()).isTrue();
        assertThat(read.interval()).isNull();
        assertThat(read.nextRunAt()).isNull();
        assertThat(read.stoppedAt()).isEqualTo(T0);
        assertThat(read.stopReason()).isEqualTo(PollDecision.OVERDUE_LIMIT);
    }

    @Test
    void aStaleVersionIsRejectedAndNothingChanges() {
        PollSchedule saved = schedules.save(group(PipelineSource.FCTT, "abc"));
        schedules.save(saved.alerted(T0.plusSeconds(1)));

        assertThatThrownBy(() -> schedules.save(saved.alerted(T0.plusSeconds(2))))
                .isInstanceOf(StalePollScheduleException.class);
        assertThat(schedules.findById(saved.id()).orElseThrow().alertedAt()).isEqualTo(T0.plusSeconds(1));
    }

    @Test
    void aRemovedScheduleCannotBeUpdated() {
        PollSchedule saved = schedules.save(group(PipelineSource.FCTT, "abc"));
        PollSchedule updated = schedules.save(saved.alerted(T0));
        schedules.delete(saved.id());

        assertThatThrownBy(() -> schedules.save(updated)).isInstanceOf(StalePollScheduleException.class);
    }

    @Test
    void theSameUnitCreatedTwiceIsStale() {
        schedules.save(group(PipelineSource.FCTT, "abc"));

        assertThatThrownBy(() -> schedules.save(group(PipelineSource.FCTT, "abc")))
                .isInstanceOf(StalePollScheduleException.class);
        assertThat(schedules.query(null, null)).hasSize(1);
    }

    @Test
    void queryFiltersAndOrdersBySourceSeasonAndKey() {
        schedules.save(group(PipelineSource.FCTT, "bbb"));
        schedules.save(group(PipelineSource.FCTT, "aaa"));
        schedules.save(group(PipelineSource.RFETM, "aaa"));
        schedules.save(PollSchedule.group(UUID.randomUUID(), PipelineSource.FCTT, "2025-2026", "aaa",
                new ScopeFilter("c", null, null, null, null, List.of(1)), open(), T0));

        assertThat(schedules.query(PipelineSource.FCTT, SEASON)).extracting(PollSchedule::scopeKey)
                .containsExactly("aaa", "bbb");
        assertThat(schedules.query(PipelineSource.FCTT, null)).hasSize(3);
        assertThat(schedules.query(null, SEASON)).hasSize(3);
        assertThat(schedules.query(null, null)).hasSize(4);
        assertThat(schedules.findBySourceAndSeason(PipelineSource.RFETM, SEASON)).hasSize(1);
        assertThat(schedules.findBySourceAndSeason(PipelineSource.BCNESA, SEASON)).isEmpty();
    }

    @Test
    void deleteRemovesTheRowAndIgnoresUnknownIds() {
        PollSchedule saved = schedules.save(group(PipelineSource.FCTT, "abc"));

        schedules.delete(saved.id());
        schedules.delete(UUID.randomUUID());

        assertThat(schedules.findById(saved.id())).isEmpty();
    }
}
