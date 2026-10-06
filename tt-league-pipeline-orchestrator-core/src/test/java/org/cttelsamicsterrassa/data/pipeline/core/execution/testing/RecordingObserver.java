package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import java.util.ArrayList;
import java.util.List;

/** Records every change as one line: {@code run:STATUS} or {@code step:KIND/attempt:STATUS}; units go to {@link #unitEvents}. */
public class RecordingObserver implements RunObserver {

    public final List<String> events = new ArrayList<>();
    /** Every unit change as {@code unit:ordinal:STATUS}; kept apart so the run and step sequence stays readable. */
    public final List<String> unitEvents = new ArrayList<>();
    public final List<RunUnit> units = new ArrayList<>();

    @Override
    public void runChanged(PipelineRun run) {
        events.add("run:" + run.status());
    }

    @Override
    public void unitChanged(RunUnit unit) {
        unitEvents.add("unit:" + unit.ordinal() + ":" + unit.status());
        units.add(unit);
    }

    @Override
    public void stepChanged(PipelineStep step) {
        events.add("step:" + step.kind() + "/" + step.attempt() + ":" + step.status());
    }
}
