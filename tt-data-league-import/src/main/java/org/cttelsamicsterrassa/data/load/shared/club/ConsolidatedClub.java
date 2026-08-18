package org.cttelsamicsterrassa.data.load.shared.club;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ConsolidatedClub(
        ImportSource source,
        String canonicalDisplayName,
        UUID canonicalClubId,
        MatchingMode matchingMode,
        List<UUID> registrationIds,
        List<String> registrationNames
) {
    public ConsolidatedClub {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(canonicalDisplayName, "canonicalDisplayName");
        Objects.requireNonNull(matchingMode, "matchingMode");
        registrationIds = List.copyOf(registrationIds);
        registrationNames = List.copyOf(registrationNames);
    }
}
