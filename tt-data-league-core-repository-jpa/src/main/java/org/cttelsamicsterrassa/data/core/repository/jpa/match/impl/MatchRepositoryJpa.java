package org.cttelsamicsterrassa.data.core.repository.jpa.match.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.mapper.MatchJPAToMatchMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.mapper.MatchToMatchJPAMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
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
                                                 UUID homeTeamId,
                                                 UUID awayTeamId) {
        return matchRepositoryHelper
                .findByCompetitionAndSeasonAndGroupNumberAndRoundAndHomeTeam_IdAndAwayTeam_Id(
                        competition, season.toString(), groupNumber, round, homeTeamId, awayTeamId)
                .map(matchJPAToMatchMapper);
    }

    @Override
    public List<Match> findAllMatchesByTeamIds(Collection<UUID> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return List.of();
        }
        return matchRepositoryHelper.findAllByTeamIds(teamIds)
                .stream()
                .map(matchJPAToMatchMapper)
                .toList();
    }

    @Override
    public List<Match> findAllMatchesByTeamIdsAndSource(Collection<UUID> teamIds, ImportSource source) {
        if (teamIds == null || teamIds.isEmpty()) {
            return List.of();
        }
        return matchRepositoryHelper.findAllByTeamIdsAndSource(
                        teamIds, source == null ? null : Source.valueOf(source.name()))
                .stream()
                .map(matchJPAToMatchMapper)
                .toList();
    }

    @Override
    public List<Match> findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(
            Collection<UUID> teamIds,
            ImportSource source,
            Season season,
            String competition) {
        if (teamIds == null || teamIds.isEmpty()) {
            return List.of();
        }
        return matchRepositoryHelper.findAllByTeamIdsAndSourceAndSeasonAndCompetition(
                        teamIds,
                        source == null ? null : Source.valueOf(source.name()),
                        season == null ? null : season.toString(),
                        competition)
                .stream()
                .map(matchJPAToMatchMapper)
                .toList();
    }

    @Override
    public void saveMatch(Match match) {
        matchRepositoryHelper.save(matchToMatchJPAMapper.apply(match));
    }
}
