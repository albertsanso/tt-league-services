package org.cttelsamicsterrassa.data.core.repository.jpa.player.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
        name = "federated_player",
        indexes = {
                @Index(name="idx_federated_player_name", columnList="name"),
                @Index(name="idx_federated_player_source_name", columnList="source,name"),
                @Index(name="idx_federated_player_source_license", columnList="source,license_id"),
                @Index(name="idx_federated_player_player_id", columnList="player_id")
        }
)
public class FederatedPlayerJPA {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private Source source;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "license_id", length = 20)
    private String licenseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = true)
    private PlayerJPA player;
}
