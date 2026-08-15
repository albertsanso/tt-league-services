package org.cttelsamicsterrassa.data.core.repository.jpa.setscore.mapper;

import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.game.model.SetScore;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.repository.jpa.game.mapper.GameJPAToGameMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.setscore.model.SetScoreJPA;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@AllArgsConstructor
@Component
public class SetScoreJPAToSetScoreMapper implements Function<SetScoreJPA, SetScore> {

    GameJPAToGameMapper gameJPAToGameMapper;

    @Override
    public SetScore apply(SetScoreJPA setScoreJPA) {
        if (setScoreJPA == null) {
            return null;
        }
        return SetScore.builder()
                .id(setScoreJPA.getId())
                .source(setScoreJPA.getSource() == null ? null : ImportSource.valueOf(setScoreJPA.getSource().name()))
                .game(gameJPAToGameMapper.apply(setScoreJPA.getGame()))
                .setNumber(setScoreJPA.getSetNumber())
                .homePoints(setScoreJPA.getHomePoints())
                .awayPoints(setScoreJPA.getAwayPoints())
                .build();
    }
}
