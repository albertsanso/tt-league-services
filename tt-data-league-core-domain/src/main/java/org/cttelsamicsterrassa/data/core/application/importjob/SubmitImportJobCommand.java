package org.cttelsamicsterrassa.data.core.application.importjob;

import org.albertsanso.commons.command.DomainCommand;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class SubmitImportJobCommand extends DomainCommand {
    private final String filename;
    private final byte[] content;
    private final Optional<String> runId;
    private final boolean allowPublishedShrink;
    private final String requestedBy;

    public SubmitImportJobCommand(String filename, byte[] content, Optional<String> runId,
                                  boolean allowPublishedShrink, String requestedBy) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.filename = filename;
        this.content = content;
        this.runId = Objects.requireNonNull(runId, "runId");
        this.allowPublishedShrink = allowPublishedShrink;
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
    }

    public String getFilename() {
        return filename;
    }

    public byte[] getContent() {
        return content;
    }

    public Optional<String> getRunId() {
        return runId;
    }

    public boolean isAllowPublishedShrink() {
        return allowPublishedShrink;
    }

    public String getRequestedBy() {
        return requestedBy;
    }
}
