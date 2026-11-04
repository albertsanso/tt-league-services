package org.cttelsamicsterrassa.data.pipeline.core.alert;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * What the evaluator read for one pass. {@code openMatches} are the matches of {@code openDays};
 * {@code activeAlertDays} is the current state of the match days that active {@code MATCH_DAY_CLOSED} alerts refer
 * to; {@code newestTerminalRuns} holds up to two runs per source, newest first; {@code newestSuccess} is the
 * {@code finishedAt} of the newest successful run per source; {@code newestTerminalUnits} holds up to two
 * finished (not skipped) units per source and unit key, newest first.
 */
public record AlertFacts(
        List<MatchDay> openDays,
        List<MatchDay> recentlyClosedDays,
        List<MatchTracking> openMatches,
        List<MatchDay> activeAlertDays,
        Map<PipelineSource, List<PipelineRun>> newestTerminalRuns,
        Map<PipelineSource, Instant> newestSuccess,
        Map<PipelineSource, Map<String, List<RunUnit>>> newestTerminalUnits) {

    /** Facts without unit history. */
    public AlertFacts(
            List<MatchDay> openDays,
            List<MatchDay> recentlyClosedDays,
            List<MatchTracking> openMatches,
            List<MatchDay> activeAlertDays,
            Map<PipelineSource, List<PipelineRun>> newestTerminalRuns,
            Map<PipelineSource, Instant> newestSuccess) {
        this(openDays, recentlyClosedDays, openMatches, activeAlertDays, newestTerminalRuns, newestSuccess, Map.of());
    }

    public AlertFacts {
        openDays = List.copyOf(AlertChecks.required(openDays, "openDays"));
        recentlyClosedDays = List.copyOf(AlertChecks.required(recentlyClosedDays, "recentlyClosedDays"));
        openMatches = List.copyOf(AlertChecks.required(openMatches, "openMatches"));
        activeAlertDays = List.copyOf(AlertChecks.required(activeAlertDays, "activeAlertDays"));
        newestTerminalRuns = Map.copyOf(AlertChecks.required(newestTerminalRuns, "newestTerminalRuns"));
        newestSuccess = Map.copyOf(AlertChecks.required(newestSuccess, "newestSuccess"));
        newestTerminalUnits = Map.copyOf(AlertChecks.required(newestTerminalUnits, "newestTerminalUnits"));
    }
}
