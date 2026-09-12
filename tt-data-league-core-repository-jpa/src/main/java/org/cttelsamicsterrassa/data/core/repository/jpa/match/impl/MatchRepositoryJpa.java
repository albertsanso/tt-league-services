package org.cttelsamicsterrassa.data.core.repository.jpa.match.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSearchCriteria;
import org.cttelsamicsterrassa.data.core.domain.match.model.PlayerLocation;
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
import org.springframework.data.domain.PageRequest;

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
                                                 Integer groupNumber,
                                                 int round,
                                                 String phase,
                                                 UUID homeTeamId,
                                                 UUID awayTeamId) {
        return matchRepositoryHelper
                .findByCompetitionAndSeasonAndGroupNumberAndRoundAndPhaseAndHomeTeam_IdAndAwayTeam_Id(
                        competition, season.toString(), groupNumber, round, phase, homeTeamId, awayTeamId)
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

    @Override
    public void saveMatches(Collection<Match> matches) {
        matchRepositoryHelper.saveAll(matches.stream().map(matchToMatchJPAMapper).toList());
    }

    @Override
    public List<Match> searchMatches(MatchSearchCriteria criteria) {
        String[] playerNameFragments = nameFragments(criteria.playerName());
        String[] clubNameFragments = nameFragments(criteria.clubName());
        return matchRepositoryHelper.search(Source.valueOf(criteria.source().name()),
                        criteria.season().toString(), criteria.competition(), criteria.fromDate(), criteria.toDate(),
                        criteria.playerId(),
                        criteria.playerLocation() == null ? PlayerLocation.EITHER.name() : criteria.playerLocation().name(),
                        playerNameFragments[0], playerNameFragments[1], playerNameFragments[2],
                        playerNameFragments[3], playerNameFragments[4],
                        clubNameFragments[0], clubNameFragments[1], clubNameFragments[2],
                        clubNameFragments[3], clubNameFragments[4],
                        PageRequest.of(criteria.page(), criteria.pageSize()))
                .stream().map(matchJPAToMatchMapper).toList();
    }

    @Override
    public long countMatches(MatchSearchCriteria criteria) {
        String[] playerNameFragments = nameFragments(criteria.playerName());
        String[] clubNameFragments = nameFragments(criteria.clubName());
        return matchRepositoryHelper.countSearch(Source.valueOf(criteria.source().name()),
                criteria.season().toString(), criteria.competition(), criteria.fromDate(), criteria.toDate(),
                criteria.playerId(),
                criteria.playerLocation() == null ? PlayerLocation.EITHER.name() : criteria.playerLocation().name(),
                playerNameFragments[0], playerNameFragments[1], playerNameFragments[2],
                playerNameFragments[3], playerNameFragments[4],
                clubNameFragments[0], clubNameFragments[1], clubNameFragments[2],
                clubNameFragments[3], clubNameFragments[4]);
    }

    @Override
    public List<Match> findAllMatchesByFragmentsInName(List<String> fragments, int limit) {
        String[] nameFragments = new String[MAX_NAME_FRAGMENTS];
        java.util.Arrays.fill(nameFragments, "");
        if (fragments != null) {
            for (int i = 0; i < fragments.size() && i < MAX_NAME_FRAGMENTS; i++) {
                nameFragments[i] = fragments.get(i);
            }
        }
        return matchRepositoryHelper.searchByFragmentsInName(
                        nameFragments[0], nameFragments[1], nameFragments[2], nameFragments[3], nameFragments[4],
                        PageRequest.of(0, limit))
                .stream().map(matchJPAToMatchMapper).toList();
    }

    /**
     * Splits a free-text search term into up to {@value #MAX_NAME_FRAGMENTS} whitespace-separated
     * fragments so the search can match only when ALL fragments are found (e.g. "oscar campos"
     * matches a match containing "oscar" and "campos", each possibly in a different field: home/away
     * club name or a lineup player's name). Unused slots are empty strings, which the matching query
     * treats as "no fragment" (vacuously satisfied) rather than "match everything".
     */
    private static final int MAX_NAME_FRAGMENTS = 5;

    private static String[] nameFragments(String value) {
        String[] fragments = new String[MAX_NAME_FRAGMENTS];
        java.util.Arrays.fill(fragments, "");
        if (value == null || value.isBlank()) {
            return fragments;
        }
        String[] parts = value.trim().split("\\s+");
        for (int i = 0; i < parts.length && i < MAX_NAME_FRAGMENTS; i++) {
            fragments[i] = parts[i];
        }
        return fragments;
    }

    @Override
    public List<Match> findAllMatchesBySource(ImportSource source) {
        if (source == null) {
            return List.of();
        }
        return matchRepositoryHelper.findAllBySource(Source.valueOf(source.name())).stream()
                .map(matchJPAToMatchMapper).toList();
    }

    @Override
    public List<String> findAllSeasonsBySource(ImportSource source) {
        if (source == null) {
            return List.of();
        }
        return matchRepositoryHelper.findAllSeasonsBySource(Source.valueOf(source.name()));
    }

    @Override
    public List<String> findAllCompetitionsBySourceAndSeason(ImportSource source, Season season) {
        if (source == null || season == null) {
            return List.of();
        }
        return matchRepositoryHelper.findAllCompetitionsBySourceAndSeason(
                Source.valueOf(source.name()), season.toString());
    }

    @Override
    public long countAllMatches() {
        return matchRepositoryHelper.count();
    }

    @Override
    public List<String> findAllSeasons() {
        return matchRepositoryHelper.findAllSeasons();
    }

    @Override
    public long countMatchesBySeason(Season season) {
        if (season == null) {
            return 0;
        }
        return matchRepositoryHelper.countBySeason(season.toString());
    }
}
