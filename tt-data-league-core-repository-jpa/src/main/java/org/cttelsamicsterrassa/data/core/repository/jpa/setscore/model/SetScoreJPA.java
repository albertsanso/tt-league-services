package org.cttelsamicsterrassa.data.core.repository.jpa.setscore.model;

import jakarta.persistence.*;
import lombok.*;
import org.cttelsamicsterrassa.data.core.repository.jpa.game.model.GameJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "set_score",
        indexes = {
                @Index(name = "idx_set_score_game_id", columnList = "game_id")
        },
        uniqueConstraints = @UniqueConstraint(name = "uk_set_score_game_set_number", columnNames = {"game_id", "set_number"})
)
public class SetScoreJPA {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = true, length = 20)
    private Source source;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private GameJPA game;
    @Column(name = "set_number", nullable = false)
    private Integer setNumber;
    @Column(name = "home_points", nullable = false)
    private Integer homePoints;
    @Column(name = "away_points", nullable = false)
    private Integer awayPoints;
}
