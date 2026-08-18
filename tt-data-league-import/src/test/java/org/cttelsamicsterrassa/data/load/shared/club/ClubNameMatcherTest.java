package org.cttelsamicsterrassa.data.load.shared.club;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ClubNameComparison;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ClubNameMatchClass;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ClubNameMatcher;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ClubNameNormalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClubNameMatcherTest {

    private final ClubNameMatcher matcher = new ClubNameMatcher(new ClubNameNormalizer());

    @Test
    void classifiesExactMatchesAfterNormalization() {
        ClubNameComparison comparison = matcher.compare(
                ImportSource.FCTT, "HORTITEC ALZIRA TT", "hortitec   alzira");
        assertTrue(comparison.exact());
        assertEquals(1.0, comparison.score());
    }

    @Test
    void acceptsAMinorTypoAsAFuzzyCandidate() {
        ClubNameComparison comparison = matcher.compare(
                ImportSource.BCNESA, "FALCONS DE SABADELL", "FALCONS DE SABDELL");
        assertTrue(comparison.fuzzyCandidate());
        assertTrue(comparison.score() >= ClubNameMatcher.FUZZY_ACCEPTANCE_THRESHOLD);
    }

    @Test
    void rejectsShortNamesDifferentTokensAndLowScores() {
        assertEquals(ClubNameMatchClass.REJECTED_SHORT,
                matcher.compare(ImportSource.BCNESA, "AA", "AB").classification());
        assertEquals(ClubNameMatchClass.REJECTED_TOKEN_MISMATCH,
                matcher.compare(ImportSource.BCNESA, "CTT ATENEU", "CTT DELS HORTS").classification());
        assertEquals(ClubNameMatchClass.REJECTED_BELOW_THRESHOLD,
                matcher.compare(ImportSource.BCNESA, "FALCONS DE SABADELL", "FALCONS DE TERRASSA").classification());
    }

    @Test
    void treatsTerminalTeamLettersAsTheSameClub() {
        ClubNameComparison comparison = matcher.compare(
                ImportSource.BCNESA, "CLUB ARIEL A", "CLUB ARIEL B");
        assertTrue(comparison.exact());
    }
}
