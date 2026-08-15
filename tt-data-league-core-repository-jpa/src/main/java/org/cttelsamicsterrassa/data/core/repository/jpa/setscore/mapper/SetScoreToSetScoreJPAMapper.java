package org.cttelsamicsterrassa.data.core.repository.jpa.setscore.mapper;

import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.game.model.SetScore;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.cttelsamicsterrassa.data.core.repository.jpa.game.mapper.GameToGameJPAMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.setscore.model.SetScoreJPA;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@AllArgsConstructor
@Component
public class SetScoreToSetScoreJPAMapper implements Function<SetScore, SetScoreJPA> {

    GameToGameJPAMapper gameToGameJPAMapper;

    @Override
    public SetScoreJPA apply(SetScore setScore) {
        if (setScore == null) {
            return null;
        }
        SetScoreJPA setScoreJPA = new SetScoreJPA();
        setScoreJPA.setId(setScore.getId());
        setScoreJPA.setSource(setScore.getSource() == null ? null : Source.valueOf(setScore.getSource().name()));
        setScoreJPA.setGame(gameToGameJPAMapper.apply(setScore.getGame()));
        setScoreJPA.setSetNumber(setScore.getSetNumber());
        setScoreJPA.setHomePoints(setScore.getHomePoints());
        setScoreJPA.setAwayPoints(setScore.getAwayPoints());
        return setScoreJPA;
    }
}
