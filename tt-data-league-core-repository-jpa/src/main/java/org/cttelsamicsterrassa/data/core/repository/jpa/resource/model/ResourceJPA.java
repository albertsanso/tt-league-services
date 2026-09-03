package org.cttelsamicsterrassa.data.core.repository.jpa.resource.model;

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
        name = "resource",
        indexes = @Index(name = "idx_resource_logic_path_name", columnList = "logic_path, name"),
        uniqueConstraints = @UniqueConstraint(name = "uk_resource_logic_path_name", columnNames = {"logic_path", "name"})
)
public class ResourceJPA {
    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "logic_path", nullable = false, length = 2000)
    private String logicPath;

    @Column(name = "physical_path", nullable = false, length = 2000)
    private String physicalPath;
}
