package org.cttelsamicsterrassa.data.pipeline.runtime.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryDailyStatsRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryMatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryStatisticsReadRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.MatchFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.OperationalGauges;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsQueries;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsSettings;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayChangeSet;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayKey;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayWindow;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import org.junit.jupiter.api.Test;

class OperationalGaugeBinderTest {

    private static final Instant NOW = Instant.parse("2026-10-10T08:00:00Z");
    private static final StatisticsSettings SETTINGS =
            new StatisticsSettings(ZoneId.of("Europe/Madrid"), LocalTime.of(0, 30), 31);

    /** Counts reads and can be told to fail. */
    private static final class CountingMatchDays extends InMemoryMatchDayRepository {

        int reads;
        boolean failing;

        @Override
        public synchronized List<MatchDay> findByState(MatchDayState state) {
            reads++;
            if (failing) {
                throw new IllegalStateException("database down");
            }
            return super.findByState(state);
        }
    }

    private final InMemoryStatisticsReadRepository reads = new InMemoryStatisticsReadRepository();
    private final CountingMatchDays matchDays = new CountingMatchDays();
    private final FakeRunClock clock = new FakeRunClock(NOW);
    private final StatisticsQueries queries =
            new StatisticsQueries(reads, new InMemoryDailyStatsRepository(), matchDays, clock, SETTINGS);
    private final MeterRegistry registry = new SimpleMeterRegistry();

    private void bind() {
        new OperationalGaugeBinder(new OperationalGauges(queries, matchDays), clock).bindTo(registry);
    }

    private double pending(String source, String age) {
        return registry.get("pipeline.matches.pending").tags("source", source, "age", age).gauge().value();
    }

    private double overdue(String source) {
        return registry.get("pipeline.matches.overdue").tag("source", source).gauge().value();
    }

    private double open(String source) {
        return registry.get("pipeline.match.days.open").tag("source", source).gauge().value();
    }

    private static MatchFacts pendingMatch(PipelineSource source, TrackedMatchStatus status, Instant played) {
        return new MatchFacts(UUID.randomUUID(), UUID.randomUUID(), source, "2026-2027", "TERCERA",
                MatchDayState.OPEN, status, played, played.minus(Duration.ofDays(3)), null, false);
    }

    @Test
    void registersEveryGaugeForEverySource() {
        bind();

        for (PipelineSource source : PipelineSource.values()) {
            for (String age : List.of("under_1_day", "days_1_to_2", "days_2_to_7", "over_7_days")) {
                assertThat(pending(source.name(), age)).isZero();
            }
            assertThat(overdue(source.name())).isZero();
            assertThat(open(source.name())).isZero();
        }
    }

    @Test
    void reportsTheReadings() {
        reads.add(pendingMatch(PipelineSource.FCTT, TrackedMatchStatus.SCHEDULED, NOW.minus(Duration.ofHours(5))))
                .add(pendingMatch(PipelineSource.FCTT, TrackedMatchStatus.OVERDUE, NOW.minus(Duration.ofDays(9))));
        MatchDayKey key = new MatchDayKey(PipelineSource.FCTT, "2026-2027", "TERCERA", 1, "1a Fase", 1);
        MatchDayWindow window = new MatchDayWindow(LocalDate.parse("2026-10-08"), LocalDate.parse("2026-10-09"), 2);
        matchDays.apply(new MatchDayChangeSet(List.of(MatchDay.create(UUID.randomUUID(), key, window, NOW).open(NOW)),
                List.of(), Set.of(), List.of()));
        bind();

        assertThat(pending("FCTT", "under_1_day")).isEqualTo(1);
        assertThat(pending("FCTT", "over_7_days")).isEqualTo(1);
        assertThat(overdue("FCTT")).isEqualTo(1);
        assertThat(open("FCTT")).isEqualTo(1);
        assertThat(open("RFETM")).isZero();
    }

    @Test
    void oneReadServesAScrapeAndRefreshesOnlyAfterThirtySeconds() {
        bind();

        open("FCTT");
        open("RFETM");
        pending("FCTT", "under_1_day");
        assertThat(matchDays.reads).isEqualTo(1);

        clock.advance(Duration.ofSeconds(29));
        open("FCTT");
        assertThat(matchDays.reads).isEqualTo(1);

        clock.advance(Duration.ofSeconds(1));
        open("FCTT");
        assertThat(matchDays.reads).isEqualTo(2);
    }

    @Test
    void aFailedRefreshReportsNaNCountsTheFailureAndRecovers() {
        bind();
        assertThat(open("FCTT")).isZero();
        matchDays.failing = true;
        clock.advance(Duration.ofSeconds(30));

        assertThat(open("FCTT")).isNaN();
        assertThat(pending("FCTT", "under_1_day")).isNaN();
        assertThat(registry.get("pipeline.metrics.refresh.failures").counter().count()).isEqualTo(1);

        // the failure starts a new wait: no further read, no further count
        clock.advance(Duration.ofSeconds(10));
        assertThat(open("FCTT")).isNaN();
        assertThat(matchDays.reads).isEqualTo(2);
        assertThat(registry.get("pipeline.metrics.refresh.failures").counter().count()).isEqualTo(1);

        matchDays.failing = false;
        clock.advance(Duration.ofSeconds(20));
        assertThat(open("FCTT")).isZero();
        assertThat(matchDays.reads).isEqualTo(3);
    }
}
