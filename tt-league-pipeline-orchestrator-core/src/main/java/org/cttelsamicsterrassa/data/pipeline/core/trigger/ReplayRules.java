package org.cttelsamicsterrassa.data.pipeline.core.trigger;

import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;

/**
 * The only place that decides whether a run can be replayed. Pure: no I/O and no clock. A replay copies every unit that
 * still has a package, so the run is replayable as long as at least one unit's ZIP is retained.
 */
public final class ReplayRules {

    private ReplayRules() {
    }

    public static ReplayEligibility check(PipelineRun original, List<RunArtifact> artifacts) {
        if (!original.status().isTerminal()) {
            return ReplayEligibility.no(ReplayEligibility.RUN_ACTIVE);
        }
        List<RunArtifact> zips = artifacts.stream()
                .filter(artifact -> artifact.kind() == ArtifactKind.ZIP)
                .toList();
        if (zips.isEmpty()) {
            return ReplayEligibility.no(ReplayEligibility.NO_PACKAGE);
        }
        if (zips.stream().allMatch(RunArtifact::isPurged)) {
            return ReplayEligibility.no(ReplayEligibility.ARTIFACT_PURGED);
        }
        return ReplayEligibility.yes();
    }
}
