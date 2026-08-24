package org.cttelsamicsterrassa.data.api.rest.club;

import org.cttelsamicsterrassa.data.core.application.club.find.dto.ClubFederatedReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.ClubCompetitionReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.ClubSearchReadModel;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;

import java.util.List;
import java.util.UUID;

public record ClubDto(
        UUID id,
        String name,
        String source,
        UUID canonicalClubId,
        String canonicalClubName,
        List<FederatedClubDto> federatedClubs,
        List<String> sources,
        List<CompetitionDto> competitions,
        int playerCount,
        List<String> seasons) {
    public ClubDto(UUID id, String name, String source) {
        this(id, name, source, null, null, List.of(), source == null ? List.of() : List.of(source),
                List.of(), 0, List.of());
    }

    public ClubDto(UUID id, String name, String source, UUID canonicalClubId, String canonicalClubName) {
        this(id, name, source, canonicalClubId, canonicalClubName, List.of(),
                source == null ? List.of() : List.of(source), List.of(), 0, List.of());
    }

    public static ClubDto fromObject(FederatedClub club) {
        return new ClubDto(
                club.getId(),
                club.getName(),
                club.getSource().name(),
                club.getClub().map(canonical -> canonical.getId()).orElse(null),
                club.getClub().map(canonical -> canonical.getName()).orElse(null),
                List.of(),
                List.of(club.getSource().name()),
                List.of(),
                0,
                List.of()
        );
    }

    public static ClubDto fromObject(ClubSearchReadModel club) {
        List<FederatedClubDto> federatedClubs = club.federatedClubs().stream()
                .map(FederatedClubDto::fromObject)
                .toList();
        String source = federatedClubs.stream()
                .map(FederatedClubDto::source)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .reduce((first, second) -> "MULTIPLE")
                .orElse(null);
        return new ClubDto(
                club.id(),
                club.name(),
                source,
                club.id(),
                club.name(),
                federatedClubs,
                federatedClubs.stream()
                        .map(FederatedClubDto::source)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .sorted()
                        .toList(),
                club.competitions().stream()
                        .map(CompetitionDto::fromObject)
                        .toList(),
                club.playerCount(),
                club.seasons().stream().map(Object::toString).toList());
    }

    public record FederatedClubDto(UUID id, String name, String source) {
        private static FederatedClubDto fromObject(ClubFederatedReadModel club) {
            return new FederatedClubDto(
                    club.id(),
                    club.name(),
                    club.source() == null ? null : club.source().name());
        }
    }

    public record CompetitionDto(
            String name,
            String source,
            String season,
            int matchCount,
            int wins,
            int draws,
            int losses) {
        private static CompetitionDto fromObject(ClubCompetitionReadModel competition) {
            return new CompetitionDto(
                    competition.name(),
                    competition.source() == null ? null : competition.source().name(),
                    competition.season().toString(),
                    competition.matchCount(),
                    competition.wins(),
                    competition.draws(),
                    competition.losses());
        }
    }
}
