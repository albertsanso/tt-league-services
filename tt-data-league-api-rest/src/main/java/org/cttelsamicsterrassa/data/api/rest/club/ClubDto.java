package org.cttelsamicsterrassa.data.api.rest.club;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;

import java.util.UUID;

public record ClubDto(UUID id, String name, String source, UUID canonicalClubId, String canonicalClubName) {
    public ClubDto(UUID id, String name, String source) {
        this(id, name, source, null, null);
    }

    public static ClubDto fromObject(FederatedClub club) {
        return new ClubDto(
                club.getId(),
                club.getName(),
                club.getSource().name(),
                club.getClub().map(canonical -> canonical.getId()).orElse(null),
                club.getClub().map(canonical -> canonical.getName()).orElse(null)
        );
    }
}
