package org.cttelsamicsterrassa.data.core.domain.settings.service;

import org.cttelsamicsterrassa.data.core.domain.settings.model.*;
import org.cttelsamicsterrassa.data.core.domain.settings.repository.SettingsRepository;
import java.util.*;

public class SystemSettingsService {
    private final SettingsRepository repository;
    private final SystemSettingCatalog catalog;
    public SystemSettingsService(SettingsRepository repository) { this(repository, new SystemSettingCatalog()); }
    public SystemSettingsService(SettingsRepository repository, SystemSettingCatalog catalog) {
        this.repository = repository; this.catalog = catalog;
    }
    public synchronized List<SystemSetting> list(SettingCategory category, String search) {
        Map<String,SystemSetting> persisted = persisted();
        String needle = search == null ? "" : search.trim().toLowerCase();
        return catalog.defaults().stream().filter(s -> category == null || s.getCategory() == category)
                .filter(s -> needle.isEmpty() || s.getKey().toLowerCase().contains(needle) || s.getLabel().toLowerCase().contains(needle))
                .map(s -> persisted.getOrDefault(s.getKey(), s)).toList();
    }
    public synchronized SystemSetting update(String key, Object value, long expectedVersion) {
        Object valid = catalog.validate(key, value);
        SystemSetting current = persisted().get(key);
        long actual = current == null ? 0 : current.getVersion();
        if (actual != expectedVersion) throw new SettingConflictException(key);
        SystemSetting next = current == null ? catalog.defaults().stream().filter(s -> s.getKey().equals(key)).findFirst().orElseThrow()
                : current;
        next.changeValue(valid);
        repository.save(next, expectedVersion);
        return next;
    }
    public synchronized Map<String,String> validate(Map<String,Object> changes) {
        if (changes == null || changes.isEmpty()) throw new SettingValidationException("At least one setting is required");
        Map<String,String> errors = new LinkedHashMap<>();
        changes.forEach((k,v) -> { try { catalog.validate(k,v); } catch (SettingsException e) { errors.put(k,e.getMessage()); }});
        return Collections.unmodifiableMap(errors);
    }
    public synchronized List<SystemSetting> bulkUpdate(Map<String,Object> changes, Map<String,Long> expectedVersions) {
        Map<String,String> errors=validate(changes); if(!errors.isEmpty()) throw new SettingValidationException(errors.toString());
        Map<String,SystemSetting> current = persisted();
        Map<String,SystemSetting> next=new LinkedHashMap<>(current);
        for(var e:changes.entrySet()) {
            SystemSetting existing=next.get(e.getKey()); long actual=existing==null?0:existing.getVersion();
            long expected=expectedVersions==null||!expectedVersions.containsKey(e.getKey())?actual:expectedVersions.get(e.getKey());
            if(actual!=expected) throw new SettingConflictException(e.getKey());
            SystemSetting setting=existing==null?catalog.defaults().stream().filter(s->s.getKey().equals(e.getKey())).findFirst().orElseThrow():existing;
            setting.changeValue(catalog.validate(e.getKey(),e.getValue())); next.put(e.getKey(),setting);
        }
        Map<String, Long> versions = new LinkedHashMap<>();
        current.values().forEach(s -> versions.put(s.getKey(), s.getVersion()));
        repository.replaceAll(next, versions); return list(null,null);
    }
    public synchronized List<SystemSetting> preview(Map<String,Object> changes) {
        Map<String,String> errors=validate(changes); if(!errors.isEmpty()) throw new SettingValidationException(errors.toString());
        Map<String,SystemSetting> current=persisted(); List<SystemSetting> out=new ArrayList<>();
        for(SystemSetting d:catalog.defaults()) if(changes.containsKey(d.getKey())) {
            SystemSetting base=current.get(d.getKey()); long v=base==null?0:base.getVersion();
            out.add(SystemSetting.createExisting(d.getKey(),d.getCategory(),d.getType(),catalog.validate(d.getKey(),changes.get(d.getKey())),d.getDefaultValue(),v,d.getLabel(),d.getDescription(),d.getAllowedValues(),d.getMinimum(),d.getMaximum()));
        } return List.copyOf(out);
    }
    public synchronized Map<String,Object> backup(){Map<String,Object> out=new LinkedHashMap<>();list(null,null).forEach(s->out.put(s.getKey(),s.getValue()));return Collections.unmodifiableMap(out);}
    public synchronized void restore(Map<String,Object> values){
        Map<String,String> errors=validate(values); if(values.size()!=catalog.defaults().size()) throw new SettingValidationException("Backup must contain the complete supported catalog");
        if(!errors.isEmpty()) throw new SettingValidationException(errors.toString());
        Map<String,SystemSetting> replacement=new LinkedHashMap<>(); Map<String,SystemSetting> old=persisted();
        for(SystemSetting d:catalog.defaults()){
            SystemSetting existing=old.get(d.getKey());
            SystemSetting restored=SystemSetting.createExisting(d.getKey(),d.getCategory(),d.getType(),
                    existing == null ? d.getValue() : existing.getValue(),d.getDefaultValue(),
                    existing == null ? 0 : existing.getVersion(),d.getLabel(),d.getDescription(),
                    d.getAllowedValues(),d.getMinimum(),d.getMaximum());
            Object value = catalog.validate(d.getKey(), values.get(d.getKey()));
            if (Objects.equals(restored.getValue(), value)) {
                restored = SystemSetting.createExisting(d.getKey(), d.getCategory(), d.getType(), value,
                        d.getDefaultValue(), restored.getVersion() + 1, d.getLabel(), d.getDescription(),
                        d.getAllowedValues(), d.getMinimum(), d.getMaximum());
            } else {
                restored.changeValue(value);
            }
            replacement.put(d.getKey(), restored);
        }
        repository.replaceAll(replacement);
    }
    private Map<String,SystemSetting> persisted(){Map<String,SystemSetting> out=new LinkedHashMap<>();repository.findAll().forEach(s->out.put(s.getKey(),s));return out;}
}
