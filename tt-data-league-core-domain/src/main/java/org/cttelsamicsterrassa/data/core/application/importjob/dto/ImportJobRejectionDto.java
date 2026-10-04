package org.cttelsamicsterrassa.data.core.application.importjob.dto;

/**
 * Why an import job submission was rejected: {@code INVALID} (file, ZIP, manifest or run id) or {@code SHRINK}
 * (the upload shrinks published actas without the override).
 */
public record ImportJobRejectionDto(Reason reason, String message) {

    public enum Reason {
        INVALID,
        SHRINK
    }
}
