package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * V11 applied over a V10 database that already holds policy overrides written with the nine settings of V5: they gain
 * {@code recentMatchDays = 3} and keep their version, author and time, so the strict reader accepts them. Plain Flyway
 * against a scratch database of the shared test container, like {@link RunUnitsBackfillMigrationTest}.
 */
class PollingRecentMatchDaysMigrationTest extends AbstractPersistenceTest {

    private static final String NINE_KEYS = "{\"matchDay\":\"PT1H\",\"matchDayStartOffset\":\"PT2H\","
            + "\"dayAfter\":\"PT3H\",\"daysTwoToSeven\":\"PT12H\",\"open\":\"PT24H\",\"overdue\":\"PT24H\","
            + "\"overdueStopAfterDays\":14,\"fullRefresh\":\"P7D\",\"noChangeThreshold\":4}";

    private static Flyway flyway(String url, String username, String password, String target) {
        return Flyway.configure()
                .dataSource(url, username, password)
                .schemas("pipeline")
                .defaultSchema("pipeline")
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    @Autowired
    PollingSettingsJson json;

    @Test
    void storedOverridesGainTheDefaultLookbackAndStayReadable() throws Exception {
        HikariDataSource shared = this.jdbc.getDataSource().unwrap(HikariDataSource.class);
        String database = "polling_lookback_" + UUID.randomUUID().toString().replace("-", "");
        this.jdbc.execute("CREATE DATABASE " + database);
        String url = shared.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
        flyway(url, shared.getUsername(), shared.getPassword(), "10").migrate();
        JdbcTemplate scratch = new JdbcTemplate(new DriverManagerDataSource(url, shared.getUsername(),
                shared.getPassword()));
        Instant at = Instant.parse("2026-10-04T10:00:00Z");
        scratch.update("INSERT INTO pipeline.poll_policy (source, settings, version, updated_by, updated_at) "
                + "VALUES ('FCTT', ?::jsonb, 3, 'alice', ?)", NINE_KEYS, Timestamp.from(at));
        scratch.update("INSERT INTO pipeline.poll_policy (source, settings, version, updated_by, updated_at) "
                + "VALUES ('RFETM', ?::jsonb, 1, 'bob', ?)", NINE_KEYS.replace("}", ",\"recentMatchDays\":5}"),
                Timestamp.from(at));

        flyway(url, shared.getUsername(), shared.getPassword(), "11").migrate();

        Map<String, Object> fctt = scratch.queryForMap(
                "SELECT settings::text AS settings, version, updated_by FROM pipeline.poll_policy WHERE source = 'FCTT'");
        assertThat(json.read((String) fctt.get("settings")).recentMatchDays()).isEqualTo(3);
        assertThat(fctt.get("version")).isEqualTo(3L);
        assertThat(fctt.get("updated_by")).isEqualTo("alice");
        Map<String, Object> rfetm = scratch.queryForMap(
                "SELECT settings::text AS settings FROM pipeline.poll_policy WHERE source = 'RFETM'");
        assertThat(json.read((String) rfetm.get("settings")).recentMatchDays()).isEqualTo(5);
    }
}
