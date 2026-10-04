package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.FetchedPackage;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestRunRequest;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestRunState;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.PackageSink;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.StoredArtifact;
import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Queues one response (a value or a RuntimeException) per call and records the calls. {@code getRun} falls back to
 * the repeating response when its queue is empty.
 */
public class ScriptedIngestGateway implements IngestGateway {

    /** Package body and the checksum the service declares for it. */
    public record PackageResponse(String declaredSha256, byte[] body) {

        public static PackageResponse valid(byte[] body) {
            return new PackageResponse(InMemoryArtifactStore.sha256(body), body);
        }
    }

    private final Deque<Object> starts = new ArrayDeque<>();
    private final Deque<Object> polls = new ArrayDeque<>();
    private final Deque<Object> packages = new ArrayDeque<>();
    private Object repeatingPoll;

    public final List<IngestRunRequest> startRequests = new ArrayList<>();
    public final List<String> polledIds = new ArrayList<>();
    public final List<String> fetchedIds = new ArrayList<>();

    public ScriptedIngestGateway start(String ingestRunId) {
        starts.add(ingestRunId);
        return this;
    }

    public ScriptedIngestGateway startFails(GatewayException.Kind kind, int status) {
        starts.add(new GatewayException(kind, status, "start failed " + status));
        return this;
    }

    public ScriptedIngestGateway poll(IngestRunState state) {
        polls.add(state);
        return this;
    }

    public ScriptedIngestGateway pollFails(GatewayException.Kind kind, int status) {
        polls.add(new GatewayException(kind, status, "poll failed " + status));
        return this;
    }

    public ScriptedIngestGateway pollForever(IngestRunState state) {
        repeatingPoll = state;
        return this;
    }

    public ScriptedIngestGateway packageResponse(PackageResponse response) {
        packages.add(response);
        return this;
    }

    public ScriptedIngestGateway packageFails(GatewayException.Kind kind, int status) {
        packages.add(new GatewayException(kind, status, "package failed " + status));
        return this;
    }

    public ScriptedIngestGateway startThrows(RuntimeException exception) {
        starts.add(exception);
        return this;
    }

    public static IngestRunState running(String id) {
        return new IngestRunState(id, "RUNNING", null, false, false, null);
    }

    public static IngestRunState finished(String id, String outcome, boolean withPackage) {
        return new IngestRunState(id, "SUCCEEDED", outcome, false, withPackage, null);
    }

    public static IngestRunState failed(String id, String outcome, String error) {
        return new IngestRunState(id, "FAILED", outcome, true, false, error);
    }

    @Override
    public String startRun(IngestRunRequest request) {
        startRequests.add(request);
        return (String) next(starts, "startRun");
    }

    @Override
    public IngestRunState getRun(String ingestRunId) {
        polledIds.add(ingestRunId);
        Object response = polls.isEmpty() ? repeatingPoll : polls.poll();
        if (response == null) {
            throw new AssertionError("No scripted getRun response left");
        }
        if (response instanceof RuntimeException exception) {
            throw exception;
        }
        return (IngestRunState) response;
    }

    @Override
    public FetchedPackage fetchPackage(String ingestRunId, PackageSink sink) {
        fetchedIds.add(ingestRunId);
        Object response = next(packages, "fetchPackage");
        PackageResponse pkg = (PackageResponse) response;
        StoredArtifact stored = sink.write(new ByteArrayInputStream(pkg.body()));
        return new FetchedPackage(pkg.declaredSha256(), stored);
    }

    private static Object next(Deque<Object> queue, String call) {
        Object response = queue.poll();
        if (response == null) {
            throw new AssertionError("No scripted " + call + " response left");
        }
        if (response instanceof RuntimeException exception) {
            throw exception;
        }
        return response;
    }
}
