package org.cttelsamicsterrassa.data.core.repository.jpa.club.model;

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
        name = "club",
        indexes = @Index(name = "idx_club_name", columnList = "name"),
        uniqueConstraints = @UniqueConstraint(name = "uk_club_name", columnNames = "name")
)
public class ClubJPA {
    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;
}
