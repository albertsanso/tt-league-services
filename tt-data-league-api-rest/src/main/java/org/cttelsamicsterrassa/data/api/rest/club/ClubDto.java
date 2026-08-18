package org.cttelsamicsterrassa.data.api.rest.club;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;

import java.util.UUID;

public record ClubDto(UUID id, String name) {
    public static ClubDto fromObject(Club club) {
        return new ClubDto(
                club.getId(),
                club.getName()
        );
    }
}
