package org.cttelsamicsterrassa.data.pipeline.core.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.cttelsamicsterrassa.data.pipeline.core.alert.port.Notification;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlertTest {

    private static final Instant T0 = Instant.parse("2026-10-05T10:00:00Z");

    private static final AlertCondition CONDITION = new AlertCondition(AlertKind.RUN_FAILURES, "FCTT",
            PipelineSource.FCTT, null, "title", "detail");

    private static Alert raised() {
        return Alert.raise(UUID.randomUUID(), CONDITION, T0);
    }

    @Test
    void raiseCopiesTheConditionAndStartsUnnotified() {
        Alert alert = raised();

        assertThat(alert.kind()).isEqualTo(AlertKind.RUN_FAILURES);
        assertThat(alert.conditionKey()).isEqualTo("FCTT");
        assertThat(alert.source()).isEqualTo(PipelineSource.FCTT);
        assertThat(alert.raisedAt()).isEqualTo(T0);
        assertThat(alert.notifiedAt()).isNull();
        assertThat(alert.notifyAttempts()).isZero();
        assertThat(alert.isActive()).isTrue();
        assertThat(alert.version()).isZero();
    }

    @Test
    void notifyFailedCountsAttemptsAndNotifiedForgetsTheFailure() {
        Alert failed = raised().notifyFailed("NotificationException");
        Alert sent = failed.notified(T0.plusSeconds(5));

        assertThat(failed.notifyAttempts()).isEqualTo(1);
        assertThat(failed.lastFailure()).isEqualTo("NotificationException");
        assertThat(failed.notifiedAt()).isNull();
        assertThat(sent.notifyAttempts()).isEqualTo(2);
        assertThat(sent.lastFailure()).isNull();
        assertThat(sent.isNotified()).isTrue();
    }

    @Test
    void aClearedAlertCannotChangeAgain() {
        Alert cleared = raised().cleared(T0.plus(Duration.ofHours(1)));

        assertThat(cleared.isActive()).isFalse();
        assertThatThrownBy(() -> cleared.notified(T0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cleared.notifyFailed("X")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cleared.cleared(T0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invariantsAreValidated() {
        assertThatThrownBy(() -> new AlertCondition(AlertKind.RUN_FAILURES, "k", null, null, "t".repeat(256), "d"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AlertCondition(AlertKind.RUN_FAILURES, " ", null, null, "t", "d"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> raised().notifyFailed("x".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AlertSettings(Duration.ZERO, Duration.ofHours(1), Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notificationValidatesSubjectAndBody() {
        assertThatThrownBy(() -> new Notification("two\nlines", "body")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Notification("s".repeat(201), "body"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Notification("subject", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Notification("s".repeat(200), "body").subject()).hasSize(200);
    }
}
