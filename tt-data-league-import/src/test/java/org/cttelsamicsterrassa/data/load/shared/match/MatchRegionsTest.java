package org.cttelsamicsterrassa.data.load.shared.match;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Region;
import org.cttelsamicsterrassa.data.core.domain.shared.model.RegionType;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** FEAT-00121: payload values win, are checked against the source, and fall back to the resolver. */
class MatchRegionsTest {

    @Test
    void aPayloadValueTheSourceCanHaveWins() {
        Acta acta = acta("girona", "comarcal");

        assertEquals(Region.GIRONA, MatchRegions.region(ImportSource.BCNESA, acta, "Preferent"));
        assertEquals(RegionType.COMARCAL, MatchRegions.regionType(ImportSource.BCNESA, acta, "Preferent"));
    }

    @Test
    void aMissingPayloadValueIsDerivedFromTheSourceAndCompetition() {
        Acta acta = acta(null, null);

        assertEquals(Region.BARCELONA, MatchRegions.region(ImportSource.BCNESA, acta, "Preferent"));
        assertEquals(RegionType.NACIONAL, MatchRegions.regionType(ImportSource.RFETM, acta, "any"));
        assertNull(MatchRegions.region(ImportSource.BCNESA, acta, "rtx-preferent"));
    }

    @Test
    void aPayloadValueTheSourceCannotHaveIsRejected() {
        IllegalArgumentException region = assertThrows(IllegalArgumentException.class,
                () -> MatchRegions.region(ImportSource.RFETM, acta("girona", null), "any"));
        IllegalArgumentException type = assertThrows(IllegalArgumentException.class,
                () -> MatchRegions.regionType(ImportSource.BCNESA, acta(null, "nacional"), "Preferent"));

        assertEquals("region 'girona' is not valid for source RFETM", region.getMessage());
        assertEquals("tipo_region 'nacional' is not valid for source BCNESA", type.getMessage());
    }

    private static Acta acta(String region, String regionType) {
        return new Acta(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, region, regionType);
    }
}
