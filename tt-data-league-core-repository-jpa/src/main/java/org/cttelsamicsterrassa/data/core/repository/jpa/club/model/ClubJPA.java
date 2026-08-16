package org.cttelsamicsterrassa.data.core.repository.jpa.club.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
        name="club",
        indexes = {
                @Index(name="idx_club_name", columnList="name"),
                @Index(name="idx_club_source_name", columnList="source,name")
        },
        uniqueConstraints = {
                @UniqueConstraint(name="uk_club_source_name", columnNames={"source","name"})
        }
)
public class ClubJPA {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private Source source;

    @Column(name = "name", nullable = true, length = 255)
    private String name;
}
