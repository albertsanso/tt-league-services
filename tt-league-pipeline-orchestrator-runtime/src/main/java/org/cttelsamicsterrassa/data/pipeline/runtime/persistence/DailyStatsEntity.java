package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** One snapshot row per source and local day. No foreign keys: it outlives the rows it was computed from. */
@Entity
@IdClass(DailyStatsEntity.Key.class)
@Table(schema = "pipeline", name = "daily_stats")
class DailyStatsEntity {

    @Id
    @Column(name = "stat_date")
    LocalDate statDate;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    PipelineSource source;

    @Column(nullable = false)
    int runs;

    @Column(nullable = false)
    int failures;

    @Column(name = "matches_reported", nullable = false)
    int matchesReported;

    @Column(name = "avg_time_to_report_seconds")
    Long avgTimeToReportSeconds;

    @Column(name = "pending_end_of_day", nullable = false)
    int pendingEndOfDay;

    @Column(nullable = false, length = 64)
    String zone;

    @Column(name = "computed_at", nullable = false)
    Instant computedAt;

    protected DailyStatsEntity() {
    }

    DailyStatsEntity(LocalDate statDate, PipelineSource source) {
        this.statDate = statDate;
        this.source = source;
    }

    static final class Key implements Serializable {

        private static final long serialVersionUID = 1L;

        LocalDate statDate;
        PipelineSource source;

        Key() {
        }

        Key(LocalDate statDate, PipelineSource source) {
            this.statDate = statDate;
            this.source = source;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(statDate, key.statDate) && source == key.source;
        }

        @Override
        public int hashCode() {
            return Objects.hash(statDate, source);
        }
    }
}
