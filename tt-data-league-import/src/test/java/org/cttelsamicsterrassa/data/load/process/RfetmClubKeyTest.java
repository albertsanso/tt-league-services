package org.cttelsamicsterrassa.data.load.process;

import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmClubKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RfetmClubKeyTest {

    @Test
    void prefersTheFederationIdWhenThePayloadCarriesOne() {
        RfetmClubKey key = RfetmClubKey.of("193", "HORTITEC ALZIRA TT", "2023-2024", "super-divisio-masculino");

        assertTrue(key.isFederationId());
        assertEquals("193", key.value());
        assertEquals("193", key.rfetmId());
    }

    @Test
    void treatsABlankIdAsAbsent() {
        RfetmClubKey key = RfetmClubKey.of("  ", "HORTITEC ALZIRA TT", "2025-2026", "super-divisio-masculino");

        assertFalse(key.isFederationId());
        assertNull(key.rfetmId());
    }

    @Test
    void derivesAStableKeyFromTheNameWhenThereIsNoId() {
        RfetmClubKey first = RfetmClubKey.of(null, "HORTITEC ALZIRA TT", "2025-2026", "super-divisio-masculino");
        RfetmClubKey second = RfetmClubKey.of(null, "HORTITEC ALZIRA TT", "2025-2026", "super-divisio-masculino");

        assertEquals(first.value(), second.value());
        assertTrue(first.value().startsWith("nm:"));
        assertEquals("HORTITEC ALZIRA TT", first.name());
    }

    @Test
    void separatesTheSameNameAcrossCompetitionsAndSeasons() {
        RfetmClubKey superDivisio = RfetmClubKey.of(null, "SHARED NAME", "2025-2026", "super-divisio-masculino");
        RfetmClubKey primera = RfetmClubKey.of(null, "SHARED NAME", "2025-2026", "primera-divisio-masculino");
        RfetmClubKey femenino = RfetmClubKey.of(null, "SHARED NAME", "2025-2026", "super-divisio-femenino");
        RfetmClubKey lastSeason = RfetmClubKey.of(null, "SHARED NAME", "2024-2025", "super-divisio-masculino");

        // The A and B teams of one club share a name and are told apart by their division.
        assertNotEquals(superDivisio.value(), primera.value());
        assertNotEquals(superDivisio.value(), femenino.value());
        assertNotEquals(superDivisio.value(), lastSeason.value());
    }

    @Test
    void foldsAwayCasingAndSpacingDifferencesInTheName() {
        RfetmClubKey plain = RfetmClubKey.of(null, "HORTITEC ALZIRA TT", "2025-2026", "super-divisio-masculino");
        RfetmClubKey noisy = RfetmClubKey.of(null, "  hortitec   alzira tt ", "2025-2026", "super-divisio-masculino");

        assertEquals(plain.value(), noisy.value());
    }

    @Test
    void neverProducesAKeyLongerThanTheColumnAllows() {
        RfetmClubKey key = RfetmClubKey.of(null,
                "EIVISSA-CARMEN PELUQUEROS PATRONATO HUMANITARIO DE LAS ISLAS BALEARES",
                "2025-2026", "primera-nacional-femenino");

        assertTrue(key.value().length() <= 20, key.value());
    }

    @Test
    void cannotBeIdentifiedByNeitherIdNorName() {
        assertNull(RfetmClubKey.of(null, null, "2025-2026", "super-divisio-masculino"));
        assertNull(RfetmClubKey.of("", "   ", "2025-2026", "super-divisio-masculino"));
        assertThrows(IllegalArgumentException.class,
                () -> RfetmClubKey.ofName("2025-2026", "super-divisio-masculino", " "));
    }
}
