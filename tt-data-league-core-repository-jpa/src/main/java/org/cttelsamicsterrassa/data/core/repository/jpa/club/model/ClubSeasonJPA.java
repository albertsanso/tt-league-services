package org.cttelsamicsterrassa.data.core.repository.jpa.club.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "club_season",
        indexes = {
                @Index(name = "idx_club_season_name_season", columnList = "name,season"),
                @Index(name = "idx_club_season_club_id", columnList = "club_id")
        },
        uniqueConstraints = {
                // Keyed by club rather than by name: within a single season several distinct teams
                // share a display name, so (name, season) is ambiguous and rejects valid rows.
                @UniqueConstraint(name = "uk_club_season_club_season", columnNames = {"club_id", "season"})
        }
)
public class ClubSeasonJPA {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = true, length = 20)
    private Source source;

    @Column(name = "name", nullable = true, length = 255)
    private String name;

    @Column(length = 10)
    private String season;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = true)
    private ClubJPA club;
}
