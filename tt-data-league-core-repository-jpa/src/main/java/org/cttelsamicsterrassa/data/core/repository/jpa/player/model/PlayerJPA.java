package org.cttelsamicsterrassa.data.core.repository.jpa.player.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;

import java.util.UUID;

@Entity
@Getter
@Setter
@RequiredArgsConstructor
@Table(
        name = "player",
        indexes = {
                @Index(name="idx_player_name", columnList="name"),
                @Index(name="idx_player_source_name", columnList="source,name")
        }
)
public class PlayerJPA {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private Source source;

    @Column(name = "name", nullable = false, length = 255)
    private String name;
}
