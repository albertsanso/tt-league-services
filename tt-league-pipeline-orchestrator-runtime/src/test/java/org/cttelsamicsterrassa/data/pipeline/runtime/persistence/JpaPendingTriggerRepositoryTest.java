package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.PendingTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ScopeType;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.PendingTriggerExistsException;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.PendingTriggerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JpaPendingTriggerRepositoryTest extends AbstractPersistenceTest {

    @Autowired
    PendingTriggerRepository pending;

    private static PendingTrigger trigger(PipelineSource source, int seconds) {
        return new PendingTrigger(source, "2025-2026", ScopeType.GROUP,
                List.of(new ScopeFilter("DH", "A", null, null, "M", List.of(3, 4))), true, "alice",
                T0.plusSeconds(seconds));
    }

    @Test
    void addsAndReadsBackEveryField() {
        PendingTrigger stored = pending.add(trigger(PipelineSource.BCNESA, 0));

        assertThat(pending.findAll()).singleElement().usingRecursiveComparison().isEqualTo(stored);
    }

    @Test
    void aSecondTriggerForTheSourceIsRejected() {
        pending.add(trigger(PipelineSource.RFETM, 0));

        assertThatThrownBy(() -> pending.add(trigger(PipelineSource.RFETM, 1)))
                .isInstanceOf(PendingTriggerExistsException.class);
        assertThat(pending.findAll()).hasSize(1);
    }

    @Test
    void takeRemovesTheTriggerAndIsEmptyAfterwards() {
        pending.add(trigger(PipelineSource.FCTT, 0));

        assertThat(pending.take(PipelineSource.FCTT)).isPresent();
        assertThat(pending.take(PipelineSource.FCTT)).isEmpty();
        assertThat(pending.findAll()).isEmpty();
    }

    @Test
    void takeOnAnEmptySourceIsEmpty() {
        assertThat(pending.take(PipelineSource.RFETM)).isEmpty();
    }

    @Test
    void findAllIsOldestRequestFirst() {
        pending.add(trigger(PipelineSource.FCTT, 10));
        pending.add(trigger(PipelineSource.RFETM, 0));

        assertThat(pending.findAll()).extracting(PendingTrigger::source)
                .containsExactly(PipelineSource.RFETM, PipelineSource.FCTT);
    }

    @Test
    void fullSeasonTriggersStoreNoFilters() {
        pending.add(new PendingTrigger(PipelineSource.RFETM, "2025-2026", ScopeType.FULL_SEASON, List.of(), false,
                "bob", T0));

        assertThat(pending.findAll().get(0).filters()).isEmpty();
    }
}
