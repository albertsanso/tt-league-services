package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.PendingTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.PendingTriggerExistsException;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.PendingTriggerRepository;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryPendingTriggerRepository implements PendingTriggerRepository {

    private final Map<PipelineSource, PendingTrigger> triggers = new EnumMap<>(PipelineSource.class);

    @Override
    public synchronized PendingTrigger add(PendingTrigger trigger) {
        if (triggers.containsKey(trigger.source())) {
            throw new PendingTriggerExistsException(trigger.source());
        }
        triggers.put(trigger.source(), trigger);
        return trigger;
    }

    @Override
    public synchronized Optional<PendingTrigger> take(PipelineSource source) {
        return Optional.ofNullable(triggers.remove(source));
    }

    @Override
    public synchronized List<PendingTrigger> findAll() {
        return triggers.values().stream().sorted(Comparator.comparing(PendingTrigger::requestedAt)).toList();
    }
}
