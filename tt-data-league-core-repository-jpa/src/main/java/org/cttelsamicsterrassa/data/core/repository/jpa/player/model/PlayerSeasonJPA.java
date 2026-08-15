package org.cttelsamicsterrassa.data.core.repository.jpa.player.model;

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
@AllArgsConstructor
@NoArgsConstructor
@Table(
        name = "player_season",
        indexes = {
                @Index(name = "idx_player_season_name", columnList = "name"),
                @Index(name = "idx_player_season_season_license", columnList = "season,license"),
                @Index(name = "idx_player_season_player_id", columnList = "player_id")
        },
        uniqueConstraints = {
                // Scoped by source: RFETM and BCNESA number licences independently, so the same
                // licence value in each names a different player. NOTE: on a database created before
                // this column existed, ddl-auto=update will add the column but will not widen this
                // constraint automatically - the old (season, license) constraint must be dropped and
                // this one created manually as part of that migration.
                @UniqueConstraint(name = "uk_player_season_source_season_license",
                        columnNames = {"source", "season", "license"})
        }
)
public class PlayerSeasonJPA {
    @Id
    private UUID id;

    /**
     * Which federation this registration was imported from; part of the natural key together with
     * {@code season} and {@code license}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private Source source;

    @Column(name = "name", nullable = false, length = 255)
    private String name;
    @Column(name = "license", nullable = false, length = 20)
    private String license;
    @Column(length = 10)
    private String season;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = true)
    private PlayerJPA player;
}
