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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name="federated_club",
        indexes = {
                @Index(name="idx_federated_club_name", columnList="name"),
                @Index(name="idx_federated_club_source_name", columnList="source,name"),
                @Index(name="idx_federated_club_club_id", columnList="club_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name="uk_federated_club_source_name", columnNames={"source","name"})
        }
)
public class FederatedClubJPA {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private Source source;

    @Column(name = "name", nullable = true, length = 255)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = true)
    private ClubJPA club;

    public FederatedClubJPA(UUID id, Source source, String name) {
        this(id, source, name, null);
    }

    public FederatedClubJPA(UUID id, Source source, String name, ClubJPA club) {
        this.id = id;
        this.source = source;
        this.name = name;
        this.club = club;
    }
}
