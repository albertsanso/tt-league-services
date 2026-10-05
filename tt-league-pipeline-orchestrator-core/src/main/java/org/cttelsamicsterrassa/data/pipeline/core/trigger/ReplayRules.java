package org.cttelsamicsterrassa.data.pipeline.core.trigger;

import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;

/** The only place that decides whether a run can be replayed. Pure: no I/O and no clock. */
public final class ReplayRules {

    private ReplayRules() {
    }

    public static ReplayEligibility check(PipelineRun original, List<RunArtifact> artifacts) {
        if (!original.status().isTerminal()) {
            return ReplayEligibility.no(ReplayEligibility.RUN_ACTIVE);
        }
        RunArtifact zip = artifacts.stream()
                .filter(artifact -> artifact.kind() == ArtifactKind.ZIP)
                .findFirst()
                .orElse(null);
        if (zip == null) {
            return ReplayEligibility.no(ReplayEligibility.NO_PACKAGE);
        }
        if (zip.isPurged()) {
            return ReplayEligibility.no(ReplayEligibility.ARTIFACT_PURGED);
        }
        return ReplayEligibility.yes();
    }
}
