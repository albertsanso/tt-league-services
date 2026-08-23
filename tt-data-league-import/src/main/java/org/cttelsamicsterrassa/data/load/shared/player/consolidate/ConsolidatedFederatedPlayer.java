package org.cttelsamicsterrassa.data.load.shared.player.consolidate;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.List;
import java.util.UUID;

public record ConsolidatedFederatedPlayer(
        ImportSource source,
        String canonicalDisplayName,
        UUID federatedPlayerId,
        MatchingMode matchingMode,
        List<UUID> registrationIds,
        List<String> registrationNames
) {
    public ConsolidatedFederatedPlayer {
        registrationIds = List.copyOf(registrationIds);
        registrationNames = List.copyOf(registrationNames);
    }
}
