package org.cttelsamicsterrassa.data.core.repository.jpa.resource.model;

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
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;

import java.nio.file.Path;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "resource",
        indexes = @Index(name = "idx_resource_type_name", columnList = "type, name"),
        uniqueConstraints = @UniqueConstraint(name = "uk_resource_type_name", columnNames = {"type", "name"})
)
public class ResourceJPA {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ResourceType type;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "logic_path", nullable = false, length = 2000)
    private String logicPath;

    @Column(name = "physical_path", nullable = false, length = 2000)
    private String physicalPath;
}
