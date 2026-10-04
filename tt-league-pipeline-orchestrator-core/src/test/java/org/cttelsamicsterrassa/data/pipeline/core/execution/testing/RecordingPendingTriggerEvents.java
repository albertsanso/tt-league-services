package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.PendingTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.PendingTriggerEvents;
import java.util.ArrayList;
import java.util.List;

/** Records one line per event: {@code queued:SOURCE}, {@code launched:SOURCE} or {@code dropped:SOURCE:CODE}. */
public class RecordingPendingTriggerEvents implements PendingTriggerEvents {

    public final List<String> events = new ArrayList<>();
    private RuntimeException failure;

    /** Makes every callback throw, to prove listeners cannot break the caller. */
    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public void queued(PendingTrigger trigger) {
        events.add("queued:" + trigger.source());
        fail();
    }

    @Override
    public void launched(PendingTrigger trigger, PipelineRun run) {
        events.add("launched:" + trigger.source());
        fail();
    }

    @Override
    public void dropped(PendingTrigger trigger, String code) {
        events.add("dropped:" + trigger.source() + ":" + code);
        fail();
    }

    private void fail() {
        if (failure != null) {
            throw failure;
        }
    }
}
