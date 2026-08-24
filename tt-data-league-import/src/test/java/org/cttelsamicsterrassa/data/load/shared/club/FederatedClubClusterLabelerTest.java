package org.cttelsamicsterrassa.data.load.shared.club;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ClubNameNormalizer;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.FederatedClubClusterLabeler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FederatedClubClusterLabelerTest {

    @Test
    void preservesRepresentativeNameOrderWhenLabellingCommonTerms() {
        FederatedClubClusterLabeler labeler = new FederatedClubClusterLabeler(new ClubNameNormalizer());

        String label = labeler.label(ImportSource.RFETM, List.of(
                FederatedClub.createNew(ImportSource.RFETM, "CLUB UNIÓ ESPORTIVA TERRASSA"),
                FederatedClub.createNew(ImportSource.RFETM, "CTT UNIÓ ESPORTIVA TERRASSA")));

        assertEquals("UNIO ESPORTIVA TERRASSA", label);
    }
}
