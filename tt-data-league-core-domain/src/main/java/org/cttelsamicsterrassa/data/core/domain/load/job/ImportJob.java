package org.cttelsamicsterrassa.data.core.domain.load.job;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunSnapshot;
import org.cttelsamicsterrassa.data.core.domain.resource.model.UploadMode;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An uploaded ZIP followed from submission to the import of every ACTAS season of its manifest. The job stages the
 * uploaded bytes, stores them in the import folder and then runs one import per season, recording each season's
 * outcome. See {@link ImportJobStatus} for the lifecycle; illegal transitions throw {@link IllegalStateException}.
 */
public final class ImportJob extends Entity {
    private final UUID id;
    private final ImportSource source;
    private final List<String> seasons;
    private final UploadMode mode;
    private final Optional<String> contentSha256;
    private final Optional<String> clientRunId;
    private final Optional<String> manifestRunId;
    private final boolean allowPublishedShrink;
    private final Path stagedZipPath;
    private final String requestedBy;
    private ImportJobStatus status;
    private Optional<String> errorDetail;
    private final ZonedDateTime createdAt;
    private Optional<ZonedDateTime> startedAt;
    private Optional<ZonedDateTime> finishedAt;
    private final List<ImportJobSeason> seasonResults;

    private ImportJob(UUID id, ImportSource source, List<String> seasons, UploadMode mode,
                      Optional<String> contentSha256, Optional<String> clientRunId, Optional<String> manifestRunId,
                      boolean allowPublishedShrink, Path stagedZipPath, String requestedBy, ImportJobStatus status,
                      Optional<String> errorDetail, ZonedDateTime createdAt, Optional<ZonedDateTime> startedAt,
                      Optional<ZonedDateTime> finishedAt, List<ImportJobSeason> seasonResults) {
        this.id = Objects.requireNonNull(id, "id");
        this.source = Objects.requireNonNull(source, "source");
        this.seasons = List.copyOf(seasons);
        this.mode = Objects.requireNonNull(mode, "mode");
        this.contentSha256 = Objects.requireNonNull(contentSha256, "contentSha256");
        this.clientRunId = Objects.requireNonNull(clientRunId, "clientRunId");
        this.manifestRunId = Objects.requireNonNull(manifestRunId, "manifestRunId");
        this.allowPublishedShrink = allowPublishedShrink;
        this.stagedZipPath = Objects.requireNonNull(stagedZipPath, "stagedZipPath");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        this.status = Objects.requireNonNull(status, "status");
        this.errorDetail = Objects.requireNonNull(errorDetail, "errorDetail");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.finishedAt = Objects.requireNonNull(finishedAt, "finishedAt");
        this.seasonResults = new ArrayList<>(seasonResults);
    }

    /** Creates a new {@code QUEUED} job. */
    public static ImportJob queued(UUID id, ImportSource source, List<String> seasons, UploadMode mode,
                                   Optional<String> contentSha256, Optional<String> clientRunId,
                                   Optional<String> manifestRunId, boolean allowPublishedShrink,
                                   Path stagedZipPath, String requestedBy, ZonedDateTime createdAt) {
        return new ImportJob(id, source, seasons, mode, contentSha256, clientRunId, manifestRunId,
                allowPublishedShrink, stagedZipPath, requestedBy, ImportJobStatus.QUEUED, Optional.empty(),
                createdAt, Optional.empty(), Optional.empty(), List.of());
    }

    public static ImportJob createExisting(UUID id, ImportSource source, List<String> seasons, UploadMode mode,
                                           Optional<String> contentSha256, Optional<String> clientRunId,
                                           Optional<String> manifestRunId, boolean allowPublishedShrink,
                                           Path stagedZipPath, String requestedBy, ImportJobStatus status,
                                           Optional<String> errorDetail, ZonedDateTime createdAt,
                                           Optional<ZonedDateTime> startedAt, Optional<ZonedDateTime> finishedAt,
                                           List<ImportJobSeason> seasonResults) {
        return new ImportJob(id, source, seasons, mode, contentSha256, clientRunId, manifestRunId,
                allowPublishedShrink, stagedZipPath, requestedBy, status, errorDetail, createdAt, startedAt,
                finishedAt, seasonResults);
    }

    /** {@code QUEUED} -> {@code STORING}. */
    public void startStoring(ZonedDateTime now) {
        requireStatus(ImportJobStatus.QUEUED, "start storing");
        this.status = ImportJobStatus.STORING;
        this.startedAt = Optional.of(now);
    }

    /** {@code STORING} -> {@code IMPORTING}. */
    public void startImporting() {
        requireStatus(ImportJobStatus.STORING, "start importing");
        this.status = ImportJobStatus.IMPORTING;
    }

    /** Adds a season to import, while {@code IMPORTING}. */
    public ImportJobSeason addSeason(String season, UUID importResourceId) {
        requireStatus(ImportJobStatus.IMPORTING, "add a season");
        ImportJobSeason jobSeason = ImportJobSeason.createNew(season, importResourceId);
        seasonResults.add(jobSeason);
        return jobSeason;
    }

    /** Records the terminal run snapshot of one of this job's seasons. */
    public void recordSeasonRun(ImportJobSeason jobSeason, ImportRunSnapshot snapshot) {
        requireOwnSeason(jobSeason);
        if (!snapshot.isTerminal()) {
            throw new IllegalArgumentException("Import run " + snapshot.runId() + " is not terminal");
        }
        jobSeason.complete(snapshot.runId(), snapshot.status(), snapshot.result(), snapshot.errorDetail());
    }

    /** Fails one of this job's seasons that could not be run. */
    public void failSeason(ImportJobSeason jobSeason, String reason) {
        requireOwnSeason(jobSeason);
        jobSeason.fail(reason);
    }

    /**
     * Ends an {@code IMPORTING} job with the status its seasons imply: {@code SUCCEEDED} when there are no seasons
     * or every season is clean, {@code PARTIAL} when at least one season succeeded, otherwise {@code FAILED}.
     */
    public void finishFromSeasons(ZonedDateTime now) {
        requireStatus(ImportJobStatus.IMPORTING, "finish from its seasons");
        if (seasonResults.stream().anyMatch(season -> !season.getStatus().isTerminal())) {
            throw new IllegalStateException("Import job " + id + " still has seasons without an outcome");
        }
        ImportJobStatus outcome;
        if (seasonResults.stream().allMatch(ImportJobSeason::isClean)) {
            outcome = ImportJobStatus.SUCCEEDED;
        } else if (seasonResults.stream().anyMatch(ImportJobSeason::isSucceeded)) {
            outcome = ImportJobStatus.PARTIAL;
        } else {
            outcome = ImportJobStatus.FAILED;
        }
        end(outcome, Optional.empty(), now);
    }

    /** Fails an active job with {@code reason}; seasons without an outcome fail with the same reason. */
    public void fail(String reason, ZonedDateTime now) {
        if (!status.isActive()) {
            throw new IllegalStateException("Cannot fail import job " + id + " in status " + status);
        }
        Objects.requireNonNull(reason, "reason");
        seasonResults.stream()
                .filter(season -> !season.getStatus().isTerminal())
                .forEach(season -> season.fail(reason));
        end(ImportJobStatus.FAILED, Optional.of(reason), now);
    }

    /** Whether the job was storing or importing, so a restart interrupted it. */
    public boolean isInFlight() {
        return status == ImportJobStatus.STORING || status == ImportJobStatus.IMPORTING;
    }

    private void end(ImportJobStatus terminal, Optional<String> reason, ZonedDateTime now) {
        this.status = terminal;
        this.errorDetail = reason;
        this.finishedAt = Optional.of(now);
    }

    private void requireStatus(ImportJobStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException("Cannot " + action + " for import job " + id + " in status " + status);
        }
    }

    private void requireOwnSeason(ImportJobSeason jobSeason) {
        requireStatus(ImportJobStatus.IMPORTING, "record a season");
        if (seasonResults.stream().noneMatch(season -> season == jobSeason)) {
            throw new IllegalArgumentException("Season " + jobSeason.getSeason() + " does not belong to import job "
                    + id);
        }
    }

    public UUID getId() {
        return id;
    }

    public ImportSource getSource() {
        return source;
    }

    public List<String> getSeasons() {
        return seasons;
    }

    public UploadMode getMode() {
        return mode;
    }

    public Optional<String> getContentSha256() {
        return contentSha256;
    }

    public Optional<String> getClientRunId() {
        return clientRunId;
    }

    public Optional<String> getManifestRunId() {
        return manifestRunId;
    }

    public boolean isAllowPublishedShrink() {
        return allowPublishedShrink;
    }

    public Path getStagedZipPath() {
        return stagedZipPath;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public ImportJobStatus getStatus() {
        return status;
    }

    public Optional<String> getErrorDetail() {
        return errorDetail;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public Optional<ZonedDateTime> getStartedAt() {
        return startedAt;
    }

    public Optional<ZonedDateTime> getFinishedAt() {
        return finishedAt;
    }

    public List<ImportJobSeason> getSeasonResults() {
        return List.copyOf(seasonResults);
    }
}
