package org.cttelsamicsterrassa.data.core.repository.jpa.match.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.mapper.MatchJPAToMatchMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.mapper.MatchToMatchJPAMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Transactional
@Component
@AllArgsConstructor
public class MatchRepositoryJpa implements MatchRepository {

    private final MatchRepositoryHelper matchRepositoryHelper;
    private final MatchJPAToMatchMapper matchJPAToMatchMapper;
    private final MatchToMatchJPAMapper matchToMatchJPAMapper;

    @Override
    public Optional<Match> findMatchById(UUID id) {
        return matchRepositoryHelper.findById(id).map(matchJPAToMatchMapper);
    }

    @Override
    public Optional<Match> findMatchByExternalId(String externalId) {
        return matchRepositoryHelper.findByExternalId(externalId).map(matchJPAToMatchMapper);
    }

    @Override
    public Optional<Match> findMatchByNaturalKey(String competition,
                                                 Season season,
                                                 int groupNumber,
                                                 int round,
                                                 UUID homeClubSeasonId,
                                                 UUID awayClubSeasonId) {
        return matchRepositoryHelper
                .findByCompetitionAndSeasonAndGroupNumberAndRoundAndHomeClub_IdAndAwayClub_Id(
                        competition, season.toString(), groupNumber, round, homeClubSeasonId, awayClubSeasonId)
                .map(matchJPAToMatchMapper);
    }

    @Override
    public void saveMatch(Match match) {
        matchRepositoryHelper.save(matchToMatchJPAMapper.apply(match));
    }
}
