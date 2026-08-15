package org.cttelsamicsterrassa.data.core.repository.jpa.doublespair.mapper;

import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.cttelsamicsterrassa.data.core.repository.jpa.doublespair.Side;
import org.cttelsamicsterrassa.data.core.repository.jpa.doublespair.model.DoublesPairJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.game.mapper.GameToGameJPAMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.mapper.PlayerSeasonToPlayerSeasonJPAMapper;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@AllArgsConstructor
@Component
public class DoublesPairToDoublesPairJPAMapper implements Function<DoublesPair, DoublesPairJPA> {

    GameToGameJPAMapper gameToGameJPAMapper;
    PlayerSeasonToPlayerSeasonJPAMapper playerSeasonToPlayerSeasonJPAMapper;

    @Override
    public DoublesPairJPA apply(DoublesPair doublesPair) {
        if (doublesPair == null) {
            return null;
        }
        DoublesPairJPA doublesPairJPA = new DoublesPairJPA();
        doublesPairJPA.setId(doublesPair.getId());
        doublesPairJPA.setSource(doublesPair.getSource() == null ? null : Source.valueOf(doublesPair.getSource().name()));
        doublesPairJPA.setGame(gameToGameJPAMapper.apply(doublesPair.getGame()));
        doublesPairJPA.setSide(Side.valueOf(doublesPair.getSide()));
        doublesPairJPA.setPlayer(playerSeasonToPlayerSeasonJPAMapper.apply(doublesPair.getPlayer()));
        return doublesPairJPA;
    }
}
