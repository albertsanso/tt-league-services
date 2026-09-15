package org.cttelsamicsterrassa.data.core.domain.shared.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TieEligibleCompetitionsTest {

    @Test
    void isTieEligibleForEachAllowlistedCompetition() {
        assertTrue(TieEligibleCompetitions.isTieEligible("super-divisio-femenino"));
        assertTrue(TieEligibleCompetitions.isTieEligible("super-divisio-masculino"));
        assertTrue(TieEligibleCompetitions.isTieEligible("fasc-super-divisio-femenino"));
        assertTrue(TieEligibleCompetitions.isTieEligible("fasc-super-divisio-masculino"));
    }

    @Test
    void isNotTieEligibleForAnyOtherCompetition() {
        assertFalse(TieEligibleCompetitions.isTieEligible("primera-divisio-masculino"));
        assertFalse(TieEligibleCompetitions.isTieEligible("Preferent"));
        assertFalse(TieEligibleCompetitions.isTieEligible("Temporada 2023-2024"));
    }

    @Test
    void isCaseSensitiveAndDoesNotMatchAPartialName() {
        assertFalse(TieEligibleCompetitions.isTieEligible("SUPER-DIVISIO-MASCULINO"));
        assertFalse(TieEligibleCompetitions.isTieEligible("super-divisio"));
    }

    @Test
    void trimsSurroundingWhitespaceBeforeMatching() {
        assertTrue(TieEligibleCompetitions.isTieEligible("  super-divisio-masculino  "));
    }

    @Test
    void isNotTieEligibleForNullOrBlank() {
        assertFalse(TieEligibleCompetitions.isTieEligible(null));
        assertFalse(TieEligibleCompetitions.isTieEligible(""));
        assertFalse(TieEligibleCompetitions.isTieEligible("   "));
    }
}
