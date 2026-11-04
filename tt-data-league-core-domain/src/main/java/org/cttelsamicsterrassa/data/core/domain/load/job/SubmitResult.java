package org.cttelsamicsterrassa.data.core.domain.load.job;

import java.util.Objects;

/**
 * Outcome of submitting an upload: the job that follows it, and whether it was created by this submission or is
 * an existing job for the same content.
 */
public record SubmitResult(ImportJob job, boolean created) {

    public SubmitResult {
        Objects.requireNonNull(job, "job");
    }
}
