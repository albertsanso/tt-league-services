package org.cttelsamicsterrassa.data.core.domain.shared.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionResolverTest {

    @Test
    void rfetmIsSpainWideAndFcttIsCataloniaWideWhateverTheCompetition() {
        assertEquals(new RegionResolver.Resolution(Region.ESPANA, RegionType.NACIONAL),
                RegionResolver.resolve(ImportSource.RFETM, null).orElseThrow());
        assertEquals(new RegionResolver.Resolution(Region.CATALUNYA, RegionType.AUTONOMICA),
                RegionResolver.resolve(ImportSource.FCTT, "tercera-nacional").orElseThrow());
    }

    @ParameterizedTest
    @CsvSource({
            "rtb-preferent,BARCELONA,PROVINCIAL",
            "RTG-Preferent,GIRONA,PROVINCIAL",
            "rtl-1a-comarcal,LLEIDA,COMARCAL",
            "rtt-2a-comarcal,TARRAGONA,COMARCAL",
            "Preferent,BARCELONA,PROVINCIAL",
            "Vet 1a,BARCELONA,PROVINCIAL",
            "1a Comarcal,BARCELONA,COMARCAL",
            "2a Comarcal,BARCELONA,COMARCAL"})
    void bcnesaReadsTheProvinceFromTheExportPrefixAndLegacyNamesAreBarcelona(
            String competition, Region region, RegionType type) {
        assertEquals(new RegionResolver.Resolution(region, type),
                RegionResolver.resolve(ImportSource.BCNESA, competition).orElseThrow());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "rtx-preferent"})
    void bcnesaUnknownInputHasNoRegion(String competition) {
        assertTrue(RegionResolver.resolve(ImportSource.BCNESA, competition).isEmpty());
    }

    @Test
    void noSourceHasNoRegion() {
        assertEquals(Optional.empty(), RegionResolver.resolve(null, "Preferent"));
    }

    @Test
    void wireValuesAreLowerCaseAndParsedStrictly() {
        assertEquals("tarragona", Region.TARRAGONA.value());
        assertEquals("autonomica", RegionType.AUTONOMICA.value());
        assertEquals(Optional.of(Region.GIRONA), Region.fromValue(" Girona "));
        assertEquals(Optional.of(RegionType.COMARCAL), RegionType.fromValue("comarcal"));
        assertEquals(Optional.empty(), Region.fromValue("mars"));
        assertEquals(Optional.empty(), RegionType.fromValue(null));
    }
}
