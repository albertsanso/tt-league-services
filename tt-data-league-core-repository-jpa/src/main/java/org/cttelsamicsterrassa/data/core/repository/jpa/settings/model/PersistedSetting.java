package org.cttelsamicsterrassa.data.core.repository.jpa.settings.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SystemSetting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingType;

@Entity
@Table(name="system_settings")
@Getter @Setter @NoArgsConstructor
public class PersistedSetting {
    @Id @Column(name="`key`", nullable=false, length=120) private String key;
    @Column(name="setting_type", nullable=false, length=20) private String type;
    @Column(name="setting_value", nullable=false, length=2000) private String value;
    @Column(name="version", nullable=false) private long version;
    public PersistedSetting(String key, SettingType type, String value, long version) {
        this.key=key; this.type=type.name(); this.value=value; this.version=version;
    }
    public PersistedSetting(SystemSetting setting) {
        this(setting.getKey(), setting.getType(), String.valueOf(setting.getValue()), setting.getVersion());
    }
}
