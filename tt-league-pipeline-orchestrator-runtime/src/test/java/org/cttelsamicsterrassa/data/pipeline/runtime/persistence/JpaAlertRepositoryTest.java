package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.alert.Alert;
import org.cttelsamicsterrassa.data.pipeline.core.alert.AlertCondition;
import org.cttelsamicsterrassa.data.pipeline.core.alert.AlertKind;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.ActiveAlertExistsException;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.AlertRepository;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.StaleAlertException;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JpaAlertRepositoryTest extends AbstractPersistenceTest {

    @Autowired
    AlertRepository repository;

    private static Alert alert(AlertKind kind, String key, long raisedSeconds) {
        return Alert.raise(UUID.randomUUID(),
                new AlertCondition(kind, key, PipelineSource.FCTT, "2026-2027", "Title " + key, "Detail\nof " + key),
                T0.plusSeconds(raisedSeconds));
    }

    @Test
    void raisesAndReadsBackEveryField() {
        Alert raised = repository.raise(alert(AlertKind.RUN_FAILURES, "FCTT", 0));

        assertThat(raised.version()).isZero();
        assertThat(repository.findActive()).singleElement().usingRecursiveComparison().isEqualTo(raised);
    }

    @Test
    void aSecondActiveAlertForTheSameKindAndKeyIsAConflict() {
        repository.raise(alert(AlertKind.RUN_FAILURES, "FCTT", 0));

        assertThatThrownBy(() -> repository.raise(alert(AlertKind.RUN_FAILURES, "FCTT", 1)))
                .isInstanceOf(ActiveAlertExistsException.class);
        assertThat(repository.findActive()).hasSize(1);
    }

    @Test
    void anAlertCanBeRaisedAgainAfterItWasCleared() {
        Alert first = repository.raise(alert(AlertKind.RUN_FAILURES, "FCTT", 0));
        repository.update(first.cleared(T0.plusSeconds(5)));

        repository.raise(alert(AlertKind.RUN_FAILURES, "FCTT", 10));

        assertThat(repository.findActive()).hasSize(1);
    }

    @Test
    void updateKeepsNotificationStateAndIncrementsTheVersion() {
        Alert raised = repository.raise(alert(AlertKind.MATCH_DAY_CLOSED, "day-1", 0));

        Alert failed = repository.update(raised.notifyFailed("NotificationException"));
        Alert sent = repository.update(failed.notified(T0.plusSeconds(30)));

        assertThat(failed.version()).isEqualTo(1);
        assertThat(sent.version()).isEqualTo(2);
        assertThat(repository.findActive()).singleElement().satisfies(stored -> {
            assertThat(stored.notifiedAt()).isEqualTo(T0.plusSeconds(30));
            assertThat(stored.notifyAttempts()).isEqualTo(2);
            assertThat(stored.lastFailure()).isNull();
        });
    }

    @Test
    void aStaleVersionIsRejected() {
        Alert raised = repository.raise(alert(AlertKind.RUN_FAILURES, "FCTT", 0));
        repository.update(raised.notifyFailed("NotificationException"));

        assertThatThrownBy(() -> repository.update(raised.notified(T0.plusSeconds(5))))
                .isInstanceOf(StaleAlertException.class);
    }

    @Test
    void anUnknownAlertCannotBeUpdated() {
        assertThatThrownBy(() -> repository.update(alert(AlertKind.RUN_FAILURES, "FCTT", 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findActiveIsOldestFirstAndSkipsClearedAlerts() {
        Alert late = repository.raise(alert(AlertKind.RUN_FAILURES, "RFETM", 20));
        Alert early = repository.raise(alert(AlertKind.NO_RECENT_SUCCESS, "FCTT", 5));
        Alert cleared = repository.raise(alert(AlertKind.MATCH_DAY_CLOSED, "day-1", 10));
        repository.update(cleared.cleared(T0.plusSeconds(11)));

        assertThat(repository.findActive()).extracting(Alert::id).containsExactly(early.id(), late.id());
    }
}
