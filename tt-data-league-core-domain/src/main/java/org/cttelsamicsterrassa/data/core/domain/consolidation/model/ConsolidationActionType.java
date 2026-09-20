package org.cttelsamicsterrassa.data.core.domain.consolidation.model;

/**
 * The kind of manual consolidation that produced a {@link ConsolidationAction}.
 *
 * <p>Only {@link #MERGE} is produced today. {@link #SPLIT} and {@link #RENAME} are declared so that
 * the persisted {@code STRING} enum column already accepts them when those operations gain a manual
 * entry point; no code emits them yet.</p>
 */
public enum ConsolidationActionType {
    MERGE,
    SPLIT,
    RENAME
}
