package org.cttelsamicsterrassa.data.load.shared.player;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.List;
import java.util.UUID;

public record ConsolidationWarning(
        ImportSource source,
        String reason,
        List<UUID> registrationIds,
        List<String> registrationNames
) {
    public ConsolidationWarning {
        registrationIds = List.copyOf(registrationIds);
        registrationNames = List.copyOf(registrationNames);
    }
}
