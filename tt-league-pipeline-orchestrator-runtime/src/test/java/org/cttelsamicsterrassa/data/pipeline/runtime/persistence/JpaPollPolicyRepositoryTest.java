package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollingPolicySettings;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollingSettings;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollPolicyRepository;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.StalePollPolicyException;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JpaPollPolicyRepositoryTest extends AbstractPersistenceTest {

    @Autowired
    PollPolicyRepository policies;

    private static PollingSettings custom() {
        return new PollingSettings(Duration.ofHours(1), Duration.ofMinutes(90), Duration.ofHours(3),
                Duration.ofHours(12), Duration.ofHours(24), Duration.ofHours(30), 14, Duration.ofDays(5), 4);
    }

    @Test
    void createsAtVersionOneAndReadsBackTheSettings() {
        PollingPolicySettings saved = policies.save(PipelineSource.FCTT, custom(), "alice", T0, 0);

        assertThat(saved.version()).isEqualTo(1);
        assertThat(policies.find(PipelineSource.FCTT)).contains(saved);
        assertThat(saved.settings()).isEqualTo(custom());
        assertThat(saved.updatedBy()).isEqualTo("alice");
        assertThat(saved.updatedAt()).isEqualTo(T0);
        assertThat(policies.find(PipelineSource.RFETM)).isEmpty();
    }

    @Test
    void updatesNeedTheStoredVersion() {
        policies.save(PipelineSource.FCTT, custom(), "alice", T0, 0);

        PollingPolicySettings updated =
                policies.save(PipelineSource.FCTT, PollingSettings.defaults(), "bob", T0.plusSeconds(5), 1);

        assertThat(updated.version()).isEqualTo(2);
        assertThat(updated.settings()).isEqualTo(PollingSettings.defaults());
        assertThat(updated.updatedBy()).isEqualTo("bob");
        assertThatThrownBy(() -> policies.save(PipelineSource.FCTT, custom(), "carol", T0, 1))
                .isInstanceOf(StalePollPolicyException.class);
        assertThat(policies.find(PipelineSource.FCTT).orElseThrow().updatedBy()).isEqualTo("bob");
    }

    @Test
    void creatingTwiceOrUpdatingAMissingOverrideIsStale() {
        policies.save(PipelineSource.FCTT, custom(), "alice", T0, 0);

        assertThatThrownBy(() -> policies.save(PipelineSource.FCTT, custom(), "bob", T0, 0))
                .isInstanceOf(StalePollPolicyException.class);
        assertThatThrownBy(() -> policies.save(PipelineSource.RFETM, custom(), "bob", T0, 2))
                .isInstanceOf(StalePollPolicyException.class);
    }

    @Test
    void findAllIsOrderedBySourceAndDeleteReportsWhetherOneExisted() {
        policies.save(PipelineSource.FCTT, custom(), "alice", T0, 0);
        policies.save(PipelineSource.RFETM, custom(), "alice", T0, 0);

        assertThat(policies.findAll()).extracting(PollingPolicySettings::source)
                .containsExactly(PipelineSource.RFETM, PipelineSource.FCTT);
        assertThat(policies.delete(PipelineSource.FCTT)).isTrue();
        assertThat(policies.delete(PipelineSource.FCTT)).isFalse();
        assertThat(policies.findAll()).hasSize(1);
    }

    @Test
    void aDeletedOverrideCanBeCreatedAgainAtVersionOne() {
        policies.save(PipelineSource.FCTT, custom(), "alice", T0, 0);
        policies.delete(PipelineSource.FCTT);

        PollingPolicySettings again = policies.save(PipelineSource.FCTT, custom(), "alice", T0, 0);

        assertThat(again.version()).isEqualTo(1);
    }
}
