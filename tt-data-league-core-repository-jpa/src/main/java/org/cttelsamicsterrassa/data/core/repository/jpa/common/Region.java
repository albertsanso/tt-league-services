package org.cttelsamicsterrassa.data.core.repository.jpa.common;

/**
 * JPA-side mirror of the domain {@link org.cttelsamicsterrassa.data.core.domain.shared.model.Region}, stored as {@code EnumType.STRING}. The persisted
 * value is the upper-case name; the lower-case wire value is a domain/API concern.
 */
public enum Region {
    ESPANA,
    CATALUNYA,
    BARCELONA,
    GIRONA,
    LLEIDA,
    TARRAGONA
}
