package org.cttelsamicsterrassa.data.core.repository.jpa.settings;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

@Component
public class SystemSettingsSchemaMigration {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public SystemSettingsSchemaMigration(JdbcTemplate jdbc,
                                         org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @PostConstruct
    public void migrateLegacySchema() {
        transaction.executeWithoutResult(status -> migrate());
    }

    private void migrate() {
        Integer legacy = jdbc.queryForObject(
                "select count(*) from INFORMATION_SCHEMA.TABLES where upper(TABLE_NAME) = 'SYSTEMSETTING'",
                Integer.class);
        Integer current = jdbc.queryForObject(
                "select count(*) from INFORMATION_SCHEMA.TABLES where upper(TABLE_NAME) = 'SYSTEM_SETTINGS'",
                Integer.class);
        if (legacy != null && legacy == 1 && (current == null || current == 0)) {
            jdbc.execute("alter table SystemSetting rename to system_settings");
            jdbc.execute("alter table system_settings rename column setting_key to \"key\"");
        } else if (legacy != null && legacy == 1) {
            jdbc.execute("insert into system_settings (\"key\", setting_type, setting_value, version) "
                    + "select setting_key, setting_type, setting_value, version from SystemSetting");
            jdbc.execute("drop table SystemSetting");
        }
    }
}
