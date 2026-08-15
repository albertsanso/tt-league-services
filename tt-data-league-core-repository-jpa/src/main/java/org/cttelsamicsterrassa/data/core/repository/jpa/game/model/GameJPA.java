package org.cttelsamicsterrassa.data.core.repository.jpa.game.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.UUID;

import org.cttelsamicsterrassa.data.core.repository.jpa.game.GameType;
import org.cttelsamicsterrassa.data.core.repository.jpa.game.MatchResult;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.model.MatchJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.model.PlayerSeasonJPA;

@Entity
@Getter
@Setter
@RequiredArgsConstructor
@Table(
        name = "game",
        indexes = {
                @Index(name = "idx_game_match_id", columnList = "match_id"),
                @Index(name = "idx_game_home_player_id", columnList = "home_player_id"),
                @Index(name = "idx_game_away_player_id", columnList = "away_player_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_match_game_number", columnNames = {"match_id", "game_number"})
        }
)
public class GameJPA {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private Source source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private MatchJPA match;

    @Column(name = "game_number", nullable = false)
    private Integer gameNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private GameType type;

    @Column(name = "crossover", nullable = false, length = 20)
    private String crossover;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_player_id", nullable = true)
    private PlayerSeasonJPA homePlayer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_player_id", nullable = true)
    private PlayerSeasonJPA awayPlayer;

    @Column(name = "home_sets_won", nullable = true)
    private Integer homeSetsWon;

    @Column(name = "away_sets_won", nullable = true)
    private Integer awaySetsWon;

    @Enumerated(EnumType.STRING)
    @Column(name = "winner", nullable = true, length = 4)
    private MatchResult winner;

    @Column(name = "cumul_home", nullable = false)
    private Integer cumulativeHome;

    @Column(name = "cumul_away", nullable = false)
    private Integer cumulativeAway;

    @Column(name = "not_played", nullable = false, columnDefinition = "boolean default false")
    private boolean notPlayed;

    @Column(name = "reason", nullable = true, length = 255)
    private String reason;

    /*
    @OneToMany(mappedBy = "game", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("setNumber ASC")
    private List<SetScoreJPA> sets = new ArrayList<>();

    @OneToMany(mappedBy = "game", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DoublesPairJPA> doublesPairs = new ArrayList<>();*/
}
