package org.cttelsamicsterrassa.data.core.repository.jpa.doublespair.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.cttelsamicsterrassa.data.core.repository.jpa.doublespair.Side;
import org.cttelsamicsterrassa.data.core.repository.jpa.game.model.GameJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.model.PlayerSeasonJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;

import java.util.UUID;

@Entity
@Table(
        name = "doubles_pair",
        indexes = {
                @Index(name = "idx_doubles_pair_game_id", columnList = "game_id"),
                @Index(name = "idx_doubles_pair_player_id", columnList = "player_id")
        },
        uniqueConstraints = @UniqueConstraint(name = "uk_doubles_pair_game_side_player", columnNames = {"game_id", "side", "player_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DoublesPairJPA {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = true, length = 20)
    private Source source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private GameJPA game;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 4)
    private Side side;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerSeasonJPA player;
}
