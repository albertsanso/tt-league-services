package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import org.cttelsamicsterrassa.data.pipeline.core.alert.Alert;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.ActiveAlertExistsException;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.AlertRepository;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.StaleAlertException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One uncleared alert per kind and key, and the version rules of the JPA adapter. */
public class InMemoryAlertRepository implements AlertRepository {

    private final Map<UUID, Alert> alerts = new LinkedHashMap<>();

    @Override
    public synchronized List<Alert> findActive() {
        return alerts.values().stream()
                .filter(Alert::isActive)
                .sorted(Comparator.comparing(Alert::raisedAt).thenComparing(Alert::id))
                .toList();
    }

    @Override
    public synchronized Alert raise(Alert alert) {
        boolean exists = alerts.values().stream().anyMatch(stored -> stored.isActive()
                && stored.kind() == alert.kind() && stored.conditionKey().equals(alert.conditionKey()));
        if (exists) {
            throw new ActiveAlertExistsException(alert.kind(), alert.conditionKey());
        }
        alerts.put(alert.id(), alert);
        return alert;
    }

    @Override
    public synchronized Alert update(Alert alert) {
        Alert stored = alerts.get(alert.id());
        if (stored == null) {
            throw new IllegalArgumentException("Unknown alert: " + alert.id());
        }
        if (stored.version() != alert.version()) {
            throw new StaleAlertException(alert.id(), alert.version());
        }
        Alert next = Alert.restore(alert.id(), alert.kind(), alert.conditionKey(), alert.source(), alert.season(),
                alert.title(), alert.detail(), alert.raisedAt(), alert.notifiedAt(), alert.notifyAttempts(),
                alert.lastFailure(), alert.clearedAt(), alert.version() + 1);
        alerts.put(alert.id(), next);
        return next;
    }

    /** Every alert ever stored, cleared ones included, in insertion order. */
    public synchronized List<Alert> all() {
        return List.copyOf(alerts.values());
    }
}
