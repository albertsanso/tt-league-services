package org.cttelsamicsterrassa.data.core.repository.jpa.player.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "player",
        indexes = @Index(name = "idx_player_name", columnList = "name"),
        uniqueConstraints = @UniqueConstraint(name = "uk_player_name", columnNames = "name")
)
public class PlayerJPA {
    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;
}
