package org.cttelsamicsterrassa.data.load.shared.club;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ConsolidationWarning(
        ImportSource source,
        String reason,
        List<UUID> registrationIds,
        List<String> registrationNames
) {
    public ConsolidationWarning {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(reason, "reason");
        registrationIds = List.copyOf(registrationIds);
        registrationNames = List.copyOf(registrationNames);
    }
}
