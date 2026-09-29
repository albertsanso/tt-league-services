package org.cttelsamicsterrassa.data.core.application.importresource.preview.dto;

/**
 * The changes an import preview projects for one competition, group and phase (FEAT-00088), one
 * count per planned lifecycle change kind. Informational only; it never gates the import.
 */
public record PreviewScopeChangesDto(
        String competition,
        Integer groupNumber,
        String phase,
        long newScheduled,
        long newPlayed,
        long upgrades,
        long reschedules,
        long unchanged,
        long playedKept,
        long regressions,
        long invalidOnPlayed,
        long identityConflicts,
        long notStored) {
}
