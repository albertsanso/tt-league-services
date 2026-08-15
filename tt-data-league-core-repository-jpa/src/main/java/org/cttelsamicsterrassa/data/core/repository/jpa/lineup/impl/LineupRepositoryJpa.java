package org.cttelsamicsterrassa.data.core.repository.jpa.lineup.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.repository.jpa.lineup.mapper.LineupJPAToLineupMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.lineup.mapper.LineupToLineupJPAMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Transactional
@Component
@AllArgsConstructor
public class LineupRepositoryJpa implements LineupRepository {

    private final LineupRepositoryHelper lineupRepositoryHelper;
    private final LineupJPAToLineupMapper lineupJPAToLineupMapper;
    private final LineupToLineupJPAMapper lineupToLineupJPAMapper;

    @Override
    public List<Lineup> findLineupsByMatchId(UUID matchId) {
        return lineupRepositoryHelper.findAllByMatch_Id(matchId)
                .stream()
                .map(lineupJPAToLineupMapper)
                .toList();
    }

    @Override
    public void saveLineups(List<Lineup> lineups) {
        lineupRepositoryHelper.saveAll(lineups.stream().map(lineupToLineupJPAMapper).toList());
    }
}
