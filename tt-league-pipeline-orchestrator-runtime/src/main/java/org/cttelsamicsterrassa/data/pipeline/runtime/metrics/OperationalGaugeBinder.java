package org.cttelsamicsterrassa.data.pipeline.runtime.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.function.ToDoubleFunction;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.AgeBucket;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.OperationalGauges;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.OperationalReadings;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.PendingByAge.SourcePending;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gauges for pending matches and open match days. Every gauge reads one cached {@link OperationalReadings}; the first
 * read after the cache is older than {@link #MAX_AGE} refreshes it under a lock, so a scrape does one read and nothing
 * runs while nobody scrapes. A failed refresh makes every gauge {@code NaN} until a refresh succeeds, and starts a new
 * wait, so an outage costs one read per {@link #MAX_AGE}.
 */
public final class OperationalGaugeBinder implements MeterBinder {

    static final Duration MAX_AGE = Duration.ofSeconds(30);

    private static final Logger LOG = LoggerFactory.getLogger(OperationalGaugeBinder.class);

    private final OperationalGauges gauges;
    private final RunClock clock;
    private final Object lock = new Object();

    private Counter failures;
    private OperationalReadings readings;
    private Instant refreshedAt;

    public OperationalGaugeBinder(OperationalGauges gauges, RunClock clock) {
        this.gauges = Objects.requireNonNull(gauges, "gauges is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        failures = Counter.builder("pipeline.metrics.refresh.failures").register(registry);
        for (PipelineSource source : PipelineSource.values()) {
            for (AgeBucket bucket : AgeBucket.values()) {
                register(registry, "pipeline.matches.pending", source,
                        r -> ageCount(r.pending().get(source), bucket), "age", bucket.name().toLowerCase(Locale.ROOT));
            }
            register(registry, "pipeline.matches.overdue", source, r -> r.pending().get(source).overdue());
            register(registry, "pipeline.match.days.open", source, r -> r.openMatchDays().get(source));
        }
    }

    private void register(
            MeterRegistry registry, String meter, PipelineSource source, ToDoubleFunction<OperationalReadings> value,
            String... extraTags) {
        Gauge.builder(meter, this, binder -> binder.read(value))
                .tag("source", source.name()).tags(extraTags).register(registry);
    }

    private static int ageCount(SourcePending row, AgeBucket bucket) {
        return switch (bucket) {
            case UNDER_1_DAY -> row.under1Day();
            case DAYS_1_TO_2 -> row.days1To2();
            case DAYS_2_TO_7 -> row.days2To7();
            case OVER_7_DAYS -> row.over7Days();
        };
    }

    private double read(ToDoubleFunction<OperationalReadings> value) {
        synchronized (lock) {
            Instant now = clock.now();
            if (refreshedAt == null || Duration.between(refreshedAt, now).compareTo(MAX_AGE) >= 0) {
                refresh(now);
            }
            return readings == null ? Double.NaN : value.applyAsDouble(readings);
        }
    }

    /** The one place that reads the repositories. A failure is counted and logged, never hidden by an old value. */
    private void refresh(Instant now) {
        refreshedAt = now;
        try {
            readings = gauges.read();
        } catch (RuntimeException e) {
            readings = null;
            failures.increment();
            LOG.warn("operational gauge refresh failed: {}: {}", e.getClass().getName(), e.getMessage());
        }
    }
}
