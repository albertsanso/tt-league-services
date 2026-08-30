package org.cttelsamicsterrassa.data.core.repository.jpa.settings.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.settings.model.SettingJPA;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettingRepositoryHelper extends JpaRepository<SettingJPA, String> {
    @Modifying
    @Query("update SettingJPA s set s.type = :type, s.value = :value, s.version = :nextVersion "
            + "where s.key = :key and s.version = :expectedVersion")
    int updateIfVersion(
            @Param("key") String key,
            @Param("type") String type,
            @Param("value") String value,
            @Param("nextVersion") long nextVersion,
            @Param("expectedVersion") long expectedVersion);
}
