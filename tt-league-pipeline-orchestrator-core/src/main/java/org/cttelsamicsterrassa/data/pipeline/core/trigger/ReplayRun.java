package org.cttelsamicsterrassa.data.pipeline.core.trigger;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunLauncher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunLauncher.LaunchRequest;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ActiveRunConflictException;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunArtifactRepository;

/**
 * The only creator of RETRY runs. A replay re-submits the original's stored package: it is never queued behind an
 * active run and never calls ingest.
 */
public final class ReplayRun {

    public sealed interface Outcome permits Created, NotFound, NotReplayable, Rejected {
    }

    public record Created(PipelineRun run) implements Outcome {
    }

    public record NotFound() implements Outcome {
    }

    public record NotReplayable(String code, String message) implements Outcome {
    }

    public record Rejected(String code, String message, UUID activeRunId) implements Outcome {
    }

    public static final String ACTIVE_RUN = "ACTIVE_RUN";

    private final PipelineRunRepository runs;
    private final RunArtifactRepository artifactRows;
    private final ArtifactStore artifacts;
    private final RunLauncher launcher;

    public ReplayRun(
            PipelineRunRepository runs,
            RunArtifactRepository artifactRows,
            ArtifactStore artifacts,
            RunLauncher launcher) {
        this.runs = Objects.requireNonNull(runs, "runs is required");
        this.artifactRows = Objects.requireNonNull(artifactRows, "artifactRows is required");
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts is required");
        this.launcher = Objects.requireNonNull(launcher, "launcher is required");
    }

    public Outcome replay(UUID originalRunId, String requestedBy) {
        Optional<PipelineRun> found = runs.findById(originalRunId);
        if (found.isEmpty()) {
            return new NotFound();
        }
        PipelineRun original = found.get();
        List<RunArtifact> rows = artifactRows.findByRunId(originalRunId);
        ReplayEligibility eligibility = ReplayRules.check(original, rows);
        if (!eligibility.allowed()) {
            return new NotReplayable(eligibility.code(), message(eligibility.code(), originalRunId));
        }
        RunArtifact zip = rows.stream()
                .filter(artifact -> artifact.kind() == ArtifactKind.ZIP)
                .findFirst()
                .orElseThrow();
        if (!artifacts.exists(zip.storageKey())) {
            return new NotReplayable(ReplayEligibility.ARTIFACT_PURGED,
                    message(ReplayEligibility.ARTIFACT_PURGED, originalRunId));
        }
        try {
            return new Created(launcher.launch(new LaunchRequest(original.source(), original.season(),
                    original.scope(), original.force(), RunTrigger.RETRY, requestedBy, original.id())));
        } catch (ActiveRunConflictException conflict) {
            UUID activeId = runs.findActiveBySource(original.source()).map(PipelineRun::id).orElse(null);
            return new Rejected(ACTIVE_RUN, "Source " + original.source() + " already has an active run", activeId);
        }
    }

    private static String message(String code, UUID runId) {
        return switch (code) {
            case ReplayEligibility.RUN_ACTIVE -> "Run " + runId + " has not finished yet";
            case ReplayEligibility.NO_PACKAGE -> "Run " + runId + " has no stored package to replay";
            default -> "The stored package of run " + runId + " has been purged";
        };
    }
}
