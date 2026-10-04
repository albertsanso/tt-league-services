package org.cttelsamicsterrassa.data.pipeline.core.trigger;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun.Command;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun.Outcome;
import java.util.List;
import java.util.Objects;

/**
 * One fixed-schedule tick for one source: a full-season, non-forced {@code SCHEDULED} run through {@link TriggerRun}.
 * Ticks always reject on conflict, so a source with an active run is skipped and never gets a pending trigger.
 */
public final class ScheduledRunTick {

    public static final String REQUESTED_BY = "system:scheduler";

    private static final System.Logger LOG = System.getLogger(ScheduledRunTick.class.getName());

    private final TriggerRun triggerRun;
    private final String season;

    public ScheduledRunTick(TriggerRun triggerRun, String season) {
        this.triggerRun = Objects.requireNonNull(triggerRun, "triggerRun is required");
        this.season = PipelineRun.requireValidSeason(season);
    }

    public Outcome tick(PipelineSource source) {
        Objects.requireNonNull(source, "source is required");
        List<Outcome> outcomes = triggerRun.trigger(new Command(List.of(source), season, ScopeType.FULL_SEASON,
                List.of(), false, RunTrigger.SCHEDULED, REQUESTED_BY, ConflictMode.REJECT));
        if (outcomes.size() != 1) {
            throw new IllegalStateException("Expected one outcome for " + source + " but got " + outcomes.size());
        }
        Outcome outcome = outcomes.get(0);
        switch (outcome) {
            case Outcome.Created created -> LOG.log(System.Logger.Level.INFO,
                    "Scheduled run {0} created for {1} season {2}", created.run().id(), source, season);
            case Outcome.Rejected rejected -> LOG.log(System.Logger.Level.INFO,
                    "Scheduled run for {0} skipped: {1}", source, rejected.message());
            case Outcome.Queued queued -> throw new IllegalStateException(
                    "Scheduled tick for " + source + " was queued although ticks reject on conflict");
            case Outcome.Unavailable unavailable -> throw new IllegalStateException(
                    "Scheduled tick for " + source + " reported an unavailable FULL_SEASON scope: "
                            + unavailable.code());
        }
        return outcome;
    }
}
