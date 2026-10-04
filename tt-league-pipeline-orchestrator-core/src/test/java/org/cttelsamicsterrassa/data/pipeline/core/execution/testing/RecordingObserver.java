package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import java.util.ArrayList;
import java.util.List;

/** Records every change as one line: {@code run:STATUS} or {@code step:KIND/attempt:STATUS}. */
public class RecordingObserver implements RunObserver {

    public final List<String> events = new ArrayList<>();

    @Override
    public void runChanged(PipelineRun run) {
        events.add("run:" + run.status());
    }

    @Override
    public void stepChanged(PipelineStep step) {
        events.add("step:" + step.kind() + "/" + step.attempt() + ":" + step.status());
    }
}
