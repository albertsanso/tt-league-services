package org.cttelsamicsterrassa.data.load.bcnesa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BcnesaCompetitionNamesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "rtb-preferent|Preferent",
            "rtb-primera|Primera",
            "rtb-segona-a|Segona _A_",
            "rtb-segona-b|Segona _B_",
            "rtb-tercera-a|Tercera _A_",
            "rtb-tercera-b|Tercera _B_",
            "rtb-1a-comarcal|1a Comarcal",
            "rtb-2a-comarcal|2a Comarcal",
            "rtb-veterans-1a|Vet 1a",
            "rtb-veterans-2aa|Vet 2a _A_",
            "rtb-veterans-2ab|Vet 2a _B_",
            "rtb-veterans-3a-a|Vet 3a _A_",
            "rtb-veterans-3a-b|Vet 3a _B_",
            "rtb-veterans-4a-a|Vet 4a _A_",
            "rtb-veterans-4a-b|Vet 4a _B_",
            "rtb-veterans-4a-c|Vet 4a _C_"})
    void mapsEveryExportFolderToItsStoredName(String folder, String stored) {
        assertEquals(Optional.of(stored), BcnesaCompetitionNames.storedName(folder));
        assertTrue(BcnesaVeteransPhases.isVeteransCompetition(stored) == stored.startsWith("Vet"));
    }

    @Test
    void keepsLegacyFolderNamesUnchanged() {
        assertEquals(Optional.of("Preferent"), BcnesaCompetitionNames.storedName("Preferent"));
        assertEquals(Optional.of("Vet 1a"), BcnesaCompetitionNames.storedName("Vet 1a"));
    }

    @Test
    void anUnmappedRtbFolderHasNoStoredNameAndTheLookupIgnoresCase() {
        assertTrue(BcnesaCompetitionNames.storedName("rtb-unknown").isEmpty());
        assertEquals(Optional.of("Preferent"), BcnesaCompetitionNames.storedName("RTB-Preferent"));
    }
}
