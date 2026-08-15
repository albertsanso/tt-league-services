package org.cttelsamicsterrassa.data.core.repository.jpa.match.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.model.ClubSeasonJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;

@Entity
@Getter
@Setter
@RequiredArgsConstructor
@Table(
        name = "match_record",
        indexes = {
                @Index(name = "idx_match_competition_season_group_round", columnList = "competition,season,group_num,round"),
                @Index(name = "idx_match_external_id", columnList = "external_id"),
                @Index(name="idx_match_home_club_id", columnList="home_club_id"),
                @Index(name="idx_match_away_club_id", columnList="away_club_id"),
                @Index(name="idx_match_winner_club_id", columnList="winner_club_id")
        },
        uniqueConstraints = {
                // A round holds one match per pair of clubs, so the two clubs are part of the key.
                // Without them a whole matchday would collapse into a single row.
                @UniqueConstraint(
                        name = "uk_competition_season_group_round_clubs",
                        columnNames = {"competition", "season", "group_num", "round", "home_club_id", "away_club_id"}),
                @UniqueConstraint(name = "uk_match_external_id", columnNames = {"external_id"})
        }
)
public class MatchJPA {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private Source source;

    @Column(name = "external_id", nullable = true, length = 20)
    private String externalId;

    @Column(name = "competition", nullable = true, length = 255)
    private String competition;

    @Column(name = "season", nullable = true, length = 9)
    private String season;

    @Column(name = "group_num", nullable = false)
    private Integer groupNumber;

    @Column(name = "round", nullable = false)
    private Integer round;

    @Column(name = "match_date", nullable = true)
    private LocalDate matchDate;

    @Column(name = "match_time", nullable = true)
    private LocalTime matchTime;

    @Column(name = "city", nullable = true, length = 255)
    private String city;

    @Column(name = "venue", nullable = true, length = 255)
    private String venue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_club_id", nullable = false)
    private ClubSeasonJPA homeClub;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_club_id", nullable = false)
    private ClubSeasonJPA awayClub;

    @Column(name = "referee_name", nullable = true, length = 255)
    private String refereeName;

    @Column(name = "referee_license", nullable = true, length = 20)
    private String refereeLicense;

    @Column(name = "home_games_won", nullable = true)
    private Integer homeGamesWon;

    @Column(name = "away_games_won", nullable = true)
    private Integer awayGamesWon;

    @Column(name = "home_sets_won", nullable = true)
    private Integer homeSetsWon;

    @Column(name = "away_sets_won", nullable = true)
    private Integer awaySetsWon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_club_id", nullable = true)
    private ClubSeasonJPA winnerClub;

    @Column(name = "protested", nullable = false, columnDefinition = "boolean default false")
    private boolean protested;

}
