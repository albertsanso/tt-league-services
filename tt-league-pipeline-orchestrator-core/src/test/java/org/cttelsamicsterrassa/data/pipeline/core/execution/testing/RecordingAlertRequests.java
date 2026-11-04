package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import org.cttelsamicsterrassa.data.pipeline.core.alert.port.AlertRequests;
import java.util.concurrent.atomic.AtomicInteger;

/** Counts the alert evaluations that were requested. */
public class RecordingAlertRequests implements AlertRequests {

    private final AtomicInteger requests = new AtomicInteger();

    @Override
    public void request() {
        requests.incrementAndGet();
    }

    public int count() {
        return requests.get();
    }
}
