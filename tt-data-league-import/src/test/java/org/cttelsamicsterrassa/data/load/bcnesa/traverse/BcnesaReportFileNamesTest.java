package org.cttelsamicsterrassa.data.load.bcnesa.traverse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BcnesaReportFileNamesTest {

    @Test
    void acceptsTheCurrentAndEveryLegacyName() {
        assertTrue(BcnesaReportFileNames.isMatchReport("jornada_01_local_team_439_away_team_438.json"));
        assertTrue(BcnesaReportFileNames.isMatchReport("acta_1.json"));
        assertTrue(BcnesaReportFileNames.isMatchReport("acta_5_page_1.json"));
        assertTrue(BcnesaReportFileNames.isMatchReport("acta_151-247_7.json"));
        assertTrue(BcnesaReportFileNames.isMatchReport("acta.json"));
    }

    @Test
    void rejectsUnsupportedNames() {
        assertFalse(BcnesaReportFileNames.isMatchReport("manifest.json"));
        assertFalse(BcnesaReportFileNames.isMatchReport("jornada_01.json"));
        assertFalse(BcnesaReportFileNames.isMatchReport("jornada_01_local_team_x_away_team_2.json"));
    }

    @Test
    void derivesTheRoundFromEachNameGeneration() {
        assertEquals(1, BcnesaReportFileNames.roundFromFileName("jornada_01_local_team_439_away_team_438.json"));
        assertEquals(5, BcnesaReportFileNames.roundFromFileName("acta_5_page_1.json"));
        assertEquals(7, BcnesaReportFileNames.roundFromFileName("acta_151-247_7.json"));
        assertNull(BcnesaReportFileNames.roundFromFileName("acta_x.json"));
    }
}
