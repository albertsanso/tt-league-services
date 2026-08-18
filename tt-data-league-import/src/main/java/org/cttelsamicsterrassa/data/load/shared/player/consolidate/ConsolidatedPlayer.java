package org.cttelsamicsterrassa.data.load.shared.player.consolidate;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.List;
import java.util.UUID;

public record ConsolidatedPlayer(
        ImportSource source,
        String canonicalDisplayName,
        UUID playerId,
        MatchingMode matchingMode,
        List<UUID> registrationIds,
        List<String> registrationNames
) {
    public ConsolidatedPlayer {
        registrationIds = List.copyOf(registrationIds);
        registrationNames = List.copyOf(registrationNames);
    }
}
