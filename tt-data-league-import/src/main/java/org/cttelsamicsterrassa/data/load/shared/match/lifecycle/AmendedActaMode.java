package org.cttelsamicsterrassa.data.load.shared.match.lifecycle;

/**
 * Whether amended-acta detection (FEAT-00089) is enabled and, when it is, whether a detected
 * amendment is applied or only reported. {@code null} in the import options means disabled, exactly
 * like the consolidation mode, so detection is opt-in and off by default.
 */
public enum AmendedActaMode {

    /** Detect an amended acta and re-apply it to the stored PLAYED match. */
    WRITE,

    /** Detect an amended acta and report it without any persistence write. */
    REPORT
}