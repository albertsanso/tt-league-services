package org.cttelsamicsterrassa.data.core.domain.consolidation.model;

import org.albertsanso.commons.model.Entity;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The historic record of one manual consolidation.
 *
 * <p>Every club reference held here is a snapshot taken at merge time (see
 * {@link ConsolidationActionClub}). For a {@link ConsolidationActionType#MERGE} the source clubs
 * carry their <em>pre</em>-merge names and no longer exist, while {@link #getCanonicalName()} is
 * the <em>post</em>-merge name of the surviving target club.</p>
 *
 * <p>This entity publishes no domain event: it is the result of handling one, and emitting a
 * second would loop.</p>
 */
public class ConsolidationAction extends Entity {
    private final UUID id;
    private final ConsolidationActionType type;
    private final ZonedDateTime occurredOn;
    private final UUID performedByUserId;
    private final String performedByUsername;
    private final String canonicalName;
    private final List<ConsolidationActionClub> sourceClubs;
    private final List<ConsolidationActionClub> targetClubs;

    private ConsolidationAction(
            UUID id,
            ConsolidationActionType type,
            ZonedDateTime occurredOn,
            UUID performedByUserId,
            String performedByUsername,
            String canonicalName,
            List<ConsolidationActionClub> sourceClubs,
            List<ConsolidationActionClub> targetClubs) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.occurredOn = Objects.requireNonNull(occurredOn, "occurredOn must not be null");
        this.performedByUserId = performedByUserId;
        this.performedByUsername = performedByUsername;
        this.canonicalName = validateCanonicalName(canonicalName);
        this.sourceClubs = validateSourceClubs(sourceClubs);
        this.targetClubs = targetClubs == null ? List.of() : List.copyOf(targetClubs);
    }

    public static ConsolidationAction createNew(
            ConsolidationActionType type,
            ZonedDateTime occurredOn,
            UUID performedByUserId,
            String performedByUsername,
            String canonicalName,
            List<ConsolidationActionClub> sourceClubs,
            List<ConsolidationActionClub> targetClubs) {
        return new ConsolidationAction(
                UUID.randomUUID(), type, occurredOn, performedByUserId, performedByUsername,
                canonicalName, sourceClubs, targetClubs);
    }

    public static ConsolidationAction createExisting(
            UUID id,
            ConsolidationActionType type,
            ZonedDateTime occurredOn,
            UUID performedByUserId,
            String performedByUsername,
            String canonicalName,
            List<ConsolidationActionClub> sourceClubs,
            List<ConsolidationActionClub> targetClubs) {
        return new ConsolidationAction(
                id, type, occurredOn, performedByUserId, performedByUsername,
                canonicalName, sourceClubs, targetClubs);
    }

    public UUID getId() {
        return id;
    }

    public ConsolidationActionType getType() {
        return type;
    }

    public ZonedDateTime getOccurredOn() {
        return occurredOn;
    }

    public UUID getPerformedByUserId() {
        return performedByUserId;
    }

    public String getPerformedByUsername() {
        return performedByUsername;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    public List<ConsolidationActionClub> getSourceClubs() {
        return sourceClubs;
    }

    public List<ConsolidationActionClub> getTargetClubs() {
        return targetClubs;
    }

    private static String validateCanonicalName(String canonicalName) {
        Objects.requireNonNull(canonicalName, "canonicalName must not be null");
        if (canonicalName.isBlank()) {
            throw new IllegalArgumentException("canonicalName must not be blank");
        }
        return canonicalName;
    }

    private static List<ConsolidationActionClub> validateSourceClubs(List<ConsolidationActionClub> sourceClubs) {
        if (sourceClubs == null || sourceClubs.isEmpty()) {
            throw new IllegalArgumentException("sourceClubs must not be empty");
        }
        return List.copyOf(sourceClubs);
    }
}
