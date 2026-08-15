package org.cttelsamicsterrassa.data.core.repository.jpa.common;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

/**
 * JPA-side mirror of {@link ImportSource}, stored as
 * {@code EnumType.STRING} on rows whose natural key must be scoped per federation.
 */
public enum Source {
    RFETM,
    BCNESA,
    FCTT
}
