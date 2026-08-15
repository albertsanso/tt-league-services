package org.cttelsamicsterrassa.data.core.repository.jpa.lineup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.model.ClubSeasonJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.model.MatchJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.model.PlayerSeasonJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "lineup",
        indexes = {
                @Index(name = "idx_lineup_match_id", columnList = "match_id"),
                @Index(name = "idx_lineup_club_id", columnList = "club_id"),
                @Index(name = "idx_lineup_player_id", columnList = "player_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_match_club_letter_position", columnNames = {"match_id", "club_id", "letter", "position"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LineupJPA {
    @Id
    private UUID id;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "source", nullable = true, length = 20)
    private Source source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private MatchJPA match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private ClubSeasonJPA clubSeason;

    @Column(name = "letter", nullable = false, length = 2)
    private String letter;

    @Column(name = "position", nullable = false)
    private Integer position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerSeasonJPA player;

    @Column(name = "ranking", nullable = true, precision = 10, scale = 2)
    private BigDecimal ranking;
}
