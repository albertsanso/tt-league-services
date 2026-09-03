package org.cttelsamicsterrassa.data.core.repository.jpa.load.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResourceStatus;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.cttelsamicsterrassa.data.core.repository.jpa.resource.model.ResourceJPA;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "import_resource",
        indexes = @Index(name = "idx_import_resource_resource_id", columnList = "resource_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_import_resource_resource_season_source", columnNames = {"resource_id", "season", "source"})
)
public class ImportResourceJPA {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id", nullable = true)
    private ResourceJPA resource;

    @Column(name = "valid")
    private Boolean valid;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ResourceType type;

    @Column(name = "created", nullable = false)
    private ZonedDateTime created;

    @Column(name = "last_processed_date")
    private ZonedDateTime lastProcessedDate;

    @Column(name = "season", nullable = false)
    private String season;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private Source source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ImportResourceStatus status;

}
