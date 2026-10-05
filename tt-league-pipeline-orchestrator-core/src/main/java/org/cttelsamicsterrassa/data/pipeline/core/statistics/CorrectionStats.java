package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import java.time.LocalDate;
import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** Amended actas re-applied after the first report, per day and source, plus the totals per source. */
public record CorrectionStats(List<DayCorrections> days, List<SourceCorrections> totals) {

    public CorrectionStats {
        days = List.copyOf(days);
        totals = List.copyOf(totals);
    }

    public record DayCorrections(LocalDate date, PipelineSource source, long amendedPlayed) {}

    public record SourceCorrections(PipelineSource source, long amendedPlayed) {}
}
