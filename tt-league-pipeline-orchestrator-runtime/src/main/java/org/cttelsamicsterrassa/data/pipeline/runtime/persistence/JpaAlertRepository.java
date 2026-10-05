package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.sql.SQLException;
import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.alert.Alert;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.ActiveAlertExistsException;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.AlertRepository;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.StaleAlertException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link AlertRepository} over JPA; the partial unique index {@code ux_alert_active} guards concurrent raisers. */
@Repository
@Transactional
class JpaAlertRepository implements AlertRepository {

    static final String ACTIVE_ALERT_INDEX = "ux_alert_active";
    private static final String UNIQUE_VIOLATION = "23505";

    private final AlertJpaRepository alerts;

    JpaAlertRepository(AlertJpaRepository alerts) {
        this.alerts = alerts;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Alert> findActive() {
        return alerts.findByClearedAtIsNullOrderByRaisedAtAscIdAsc().stream()
                .map(JpaAlertRepository::toDomain)
                .toList();
    }

    @Override
    public Alert raise(Alert alert) {
        AlertEntity entity = new AlertEntity(alert.id());
        copyFields(alert, entity);
        try {
            return toDomain(alerts.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            if (violatesActiveAlertIndex(e)) {
                throw new ActiveAlertExistsException(alert.kind(), alert.conditionKey(), e);
            }
            throw e;
        }
    }

    @Override
    public Alert update(Alert alert) {
        AlertEntity entity = alerts.findById(alert.id())
                .orElseThrow(() -> new IllegalArgumentException("Unknown alert " + alert.id()));
        if (entity.version != alert.version()) {
            throw new StaleAlertException(alert.id(), alert.version());
        }
        copyFields(alert, entity);
        try {
            return toDomain(alerts.saveAndFlush(entity));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new StaleAlertException(alert.id(), alert.version());
        }
    }

    private static boolean violatesActiveAlertIndex(DataIntegrityViolationException e) {
        boolean uniqueViolation = false;
        boolean namesIndex = false;
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException hibernate
                    && UNIQUE_VIOLATION.equals(hibernate.getSQLState())) {
                uniqueViolation = true;
            }
            if (t instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                uniqueViolation = true;
            }
            if (t.getMessage() != null && t.getMessage().contains(ACTIVE_ALERT_INDEX)) {
                namesIndex = true;
            }
        }
        return uniqueViolation && namesIndex;
    }

    private static void copyFields(Alert alert, AlertEntity entity) {
        entity.kind = alert.kind();
        entity.conditionKey = alert.conditionKey();
        entity.source = alert.source();
        entity.season = alert.season();
        entity.title = alert.title();
        entity.detail = alert.detail();
        entity.raisedAt = alert.raisedAt();
        entity.notifiedAt = alert.notifiedAt();
        entity.notifyAttempts = alert.notifyAttempts();
        entity.lastFailure = alert.lastFailure();
        entity.clearedAt = alert.clearedAt();
    }

    private static Alert toDomain(AlertEntity entity) {
        return Alert.restore(entity.id, entity.kind, entity.conditionKey, entity.source, entity.season, entity.title,
                entity.detail, entity.raisedAt, entity.notifiedAt, entity.notifyAttempts, entity.lastFailure,
                entity.clearedAt, entity.version);
    }
}
