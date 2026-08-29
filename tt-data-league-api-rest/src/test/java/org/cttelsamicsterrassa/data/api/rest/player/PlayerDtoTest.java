package org.cttelsamicsterrassa.data.api.rest.player;

import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerDtoTest {

    @Test
    void preservesTheFederatedIdentityAndExposesCanonicalIdentity() {
        UUID federatedId = UUID.randomUUID();
        UUID canonicalId = UUID.randomUUID();
        Player canonical = Player.createExisting(canonicalId, "Canonical Player");
        FederatedPlayer federated = FederatedPlayer.createExisting(
                federatedId, ImportSource.FCTT, "Source Player", canonical);

        PlayerDto dto = PlayerDto.fromObject(federated);

        assertEquals(new PlayerDto(
                federatedId, "Source Player", "FCTT", canonicalId, "Canonical Player",
                List.of(), List.of("FCTT")), dto);
    }
}
