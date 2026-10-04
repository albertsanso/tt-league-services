package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactContent;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportCounters;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportJobState;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportSeasonState;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportSubmission;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/** Queues one response per call and records the submissions (with the uploaded bytes) and polled job ids. */
public class ScriptedImportGateway implements ImportGateway {

    public record Submitted(String fileName, byte[] bytes, UUID clientRunId) {
    }

    private final Deque<Object> submits = new ArrayDeque<>();
    private final Deque<Object> polls = new ArrayDeque<>();
    private Object repeatingPoll;

    public final List<Submitted> submissions = new ArrayList<>();
    public final List<UUID> polledIds = new ArrayList<>();

    public ScriptedImportGateway submit(ImportSubmission submission) {
        submits.add(submission);
        return this;
    }

    public ScriptedImportGateway submitFails(GatewayException.Kind kind, int status) {
        submits.add(new GatewayException(kind, status, "submit failed " + status));
        return this;
    }

    public ScriptedImportGateway poll(ImportJobState state) {
        polls.add(state);
        return this;
    }

    public ScriptedImportGateway pollFails(GatewayException.Kind kind, int status) {
        polls.add(new GatewayException(kind, status, "poll failed " + status));
        return this;
    }

    public ScriptedImportGateway pollForever(ImportJobState state) {
        repeatingPoll = state;
        return this;
    }

    public static ImportSubmission created(UUID jobId) {
        return new ImportSubmission(jobId, "QUEUED", true);
    }

    public static ImportJobState job(UUID jobId, String status, String errorDetail, ImportSeasonState... seasons) {
        return new ImportJobState(jobId, status, errorDetail, List.of(seasons), "{\"status\":\"" + status + "\"}");
    }

    public static ImportSeasonState season(String season, String status, ImportCounters counters,
            String... issues) {
        return new ImportSeasonState(season, status, null, counters, List.of(issues));
    }

    public static ImportCounters counters(long filesSeen, long itemsPersisted) {
        return new ImportCounters(filesSeen, itemsPersisted, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public ImportSubmission submit(String fileName, ArtifactContent content, UUID clientRunId) {
        try (InputStream in = content.open()) {
            submissions.add(new Submitted(fileName, in.readAllBytes(), clientRunId));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Object response = submits.poll();
        if (response == null) {
            throw new AssertionError("No scripted submit response left");
        }
        if (response instanceof RuntimeException exception) {
            throw exception;
        }
        return (ImportSubmission) response;
    }

    @Override
    public ImportJobState getJob(UUID importJobId) {
        polledIds.add(importJobId);
        Object response = polls.isEmpty() ? repeatingPoll : polls.poll();
        if (response == null) {
            throw new AssertionError("No scripted getJob response left");
        }
        if (response instanceof RuntimeException exception) {
            throw exception;
        }
        return (ImportJobState) response;
    }
}
