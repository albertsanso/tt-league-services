package org.cttelsamicsterrassa.data.core.repository.jpa.settings.model;

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
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "setting",
        indexes = @Index(name = "idx_setting_category_name", columnList = "category, name"),
        uniqueConstraints = @UniqueConstraint(name = "uk_setting_category_name", columnNames = {"category", "name"})
)
public class SettingJPA {
    @Id
    private UUID id;

    @Column(name = "category", nullable = false)
    @Enumerated(EnumType.STRING)
    private SettingCategory category;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "\"value\"", nullable = false, length = 255)
    private String value;
}
