package org.cttelsamicsterrassa.data.core.domain.settings.event;

import org.albertsanso.commons.event.DomainEvent;
import java.time.ZonedDateTime;

public final class SystemSettingValueChangedEvent extends DomainEvent {
    private final String key;
    private final Object value;
    private final long version;
    private SystemSettingValueChangedEvent(String key, Object value, long version) {
        super(ZonedDateTime.now(), key);
        this.key = key; this.value = value; this.version = version;
    }
    public static SystemSettingValueChangedEvent of(String key, Object value, long version) {
        return new SystemSettingValueChangedEvent(key, value, version);
    }
    public String getKey() { return key; }
    public Object getValue() { return value; }
    public long getVersion() { return version; }
}
