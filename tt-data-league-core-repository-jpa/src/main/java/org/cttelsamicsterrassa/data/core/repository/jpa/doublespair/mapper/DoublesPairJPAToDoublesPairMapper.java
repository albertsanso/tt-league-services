package org.cttelsamicsterrassa.data.core.repository.jpa.doublespair.mapper;

import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.repository.jpa.doublespair.model.DoublesPairJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.game.mapper.GameJPAToGameMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.mapper.PlayerSeasonJPAToPlayerSeasonMapper;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@AllArgsConstructor
@Component
public class DoublesPairJPAToDoublesPairMapper implements Function<DoublesPairJPA, DoublesPair> {

    GameJPAToGameMapper gameJPAToGameMapper;
    PlayerSeasonJPAToPlayerSeasonMapper playerSeasonJPAToPlayerSeasonMapper;

    @Override
    public DoublesPair apply(DoublesPairJPA doublesPairJPA) {
        if (doublesPairJPA == null) {
            return null;
        }
        return DoublesPair.builder()
                .id(doublesPairJPA.getId())
                .source(doublesPairJPA.getSource() == null ? null : ImportSource.valueOf(doublesPairJPA.getSource().name()))
                .game(gameJPAToGameMapper.apply(doublesPairJPA.getGame()))
                .side(doublesPairJPA.getSide().name())
                .player(playerSeasonJPAToPlayerSeasonMapper.apply(doublesPairJPA.getPlayer()))
                .build();
    }
}
