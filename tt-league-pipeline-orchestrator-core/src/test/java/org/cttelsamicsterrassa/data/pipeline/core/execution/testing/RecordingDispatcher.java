package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunDispatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RecordingDispatcher implements RunDispatcher {

    public final List<UUID> dispatched = new ArrayList<>();
    private RuntimeException failure;

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public void dispatch(UUID runId) {
        if (failure != null) {
            throw failure;
        }
        dispatched.add(runId);
    }
}
