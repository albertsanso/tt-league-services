package org.cttelsamicsterrassa.data.core.repository.jpa.match.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.match.model.MatchJPA;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MatchRepositoryHelper extends JpaRepository<MatchJPA, UUID> {

    Optional<MatchJPA> findByExternalId(String externalId);

    Optional<MatchJPA> findByCompetitionAndSeasonAndGroupNumberAndRoundAndHomeClub_IdAndAwayClub_Id(
            String competition,
            String season,
            Integer groupNumber,
            Integer round,
            UUID homeClubId,
            UUID awayClubId);
}
