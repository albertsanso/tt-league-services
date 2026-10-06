package org.cttelsamicsterrassa.data.pipeline.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.cttelsamicsterrassa.data.pipeline.core.execution.ExecutionSettings;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollingSettings;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsSettings;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ConflictMode;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class PipelineOrchestratorPropertiesTest {

    private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void bindsWhenAllPropertiesAreValid() {
        runner.withPropertyValues(valid().toArray(String[]::new)).run(context -> {
            assertThat(context).hasNotFailed();
            PipelineOrchestratorProperties properties = context.getBean(PipelineOrchestratorProperties.class);
            assertThat(properties.ingest().apiKey()).isEqualTo("key");
            assertThat(properties.platform().apiKey()).isEqualTo("platform-key");
            assertThat(properties.platform().baseUrl().getPort()).isEqualTo(8080);
            assertThat(properties.artifacts().dir().toString()).isEqualTo("artifacts");
            assertThat(properties.execution().maxConcurrentRuns()).isEqualTo(3);
            assertThat(properties.execution().recoverOnStartup()).isTrue();
        });
    }

    @Test
    void buildsTheCoreExecutionSettings() {
        runner.withPropertyValues(valid().toArray(String[]::new)).run(context -> {
            ExecutionSettings settings = context.getBean(PipelineOrchestratorProperties.class).executionSettings();
            assertThat(settings.retry().maxRetries()).isEqualTo(3);
            assertThat(settings.retry().initialBackoff()).isEqualTo(Duration.ofSeconds(30));
            assertThat(settings.retry().multiplier()).isEqualTo(2.0);
            assertThat(settings.retry().maxBackoff()).isEqualTo(Duration.ofMinutes(5));
            assertThat(settings.timeouts().ingest()).isEqualTo(Duration.ofHours(3));
            assertThat(settings.timeouts().fetchPackage()).isEqualTo(Duration.ofMinutes(10));
            assertThat(settings.timeouts().importJob()).isEqualTo(Duration.ofHours(3));
            assertThat(settings.polls().ingest()).isEqualTo(Duration.ofSeconds(15));
            assertThat(settings.polls().importJob()).isEqualTo(Duration.ofSeconds(10));
        });
    }

    @Test
    void failsWhenIngestApiKeyIsBlank() {
        assertFails(replace("tt.pipeline.ingest.api-key", "   "), "apiKey");
    }

    @Test
    void failsWhenPlatformApiKeyIsMissing() {
        assertFails(without("tt.pipeline.platform.api-key"), "apiKey");
    }

    @Test
    void failsWhenJwtSecretIsTooShort() {
        assertFails(replace("tt.pipeline.security.jwt-secret", "short"), "jwt-secret");
        // the rule counts UTF-8 bytes: 15 two-byte characters are 30 bytes, 16 are 32
        assertFails(replace("tt.pipeline.security.jwt-secret", "é".repeat(15)), "jwt-secret");
    }

    @Test
    void acceptsAMultiByteSecretOfAtLeast32Bytes() {
        runner.withPropertyValues(replace("tt.pipeline.security.jwt-secret", "é".repeat(16))
                .toArray(String[]::new)).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void bindsCorsOriginsTriggersAndEvents() {
        runner.withPropertyValues(replace("tt.pipeline.security.cors-allowed-origins",
                "http://localhost:5173,https://ops.example.org").toArray(String[]::new)).run(context -> {
                    PipelineOrchestratorProperties properties =
                            context.getBean(PipelineOrchestratorProperties.class);
                    assertThat(properties.security().corsAllowedOrigins())
                            .containsExactly("http://localhost:5173", "https://ops.example.org");
                    assertThat(properties.triggers().conflictMode()).isEqualTo(ConflictMode.REJECT);
                    assertThat(properties.events().heartbeatInterval()).isEqualTo(Duration.ofSeconds(15));
                    assertThat(properties.events().emitterTimeout()).isEqualTo(Duration.ofMinutes(30));
                    assertThat(properties.events().maxSubscribers()).isEqualTo(50);
                });
    }

    @Test
    void anEmptyCorsOriginListMeansNoOrigins() {
        runner.withPropertyValues(replace("tt.pipeline.security.cors-allowed-origins", "").toArray(String[]::new))
                .run(context -> assertThat(
                        context.getBean(PipelineOrchestratorProperties.class).security().corsAllowedOrigins())
                                .isEmpty());
    }

    @Test
    void failsOnACorsOriginThatIsNotAnHttpOrigin() {
        assertFails(replace("tt.pipeline.security.cors-allowed-origins", "ftp://example.org"),
                "cors-allowed-origins");
        assertFails(replace("tt.pipeline.security.cors-allowed-origins", "example.org"), "cors-allowed-origins");
        assertFails(replace("tt.pipeline.security.cors-allowed-origins", "https://example.org/app"),
                "cors-allowed-origins");
    }

    @Test
    void failsOnAnUnknownConflictModeOrMissingTriggers() {
        assertFails(replace("tt.pipeline.triggers.conflict-mode", "WAIT"), "conflict-mode");
        assertFails(without("tt.pipeline.triggers.conflict-mode"), "triggers");
    }

    @Test
    void bindsTheStatisticsSettingsWithTheirDefaults() {
        runner.withPropertyValues(valid().toArray(String[]::new)).run(context -> {
            assertThat(context).hasNotFailed();
            StatisticsSettings settings = context.getBean(PipelineOrchestratorProperties.class).statisticsSettings();
            assertThat(settings.zone()).isEqualTo(ZoneId.of("Europe/Madrid"));
            assertThat(settings.dailyAt()).isEqualTo(LocalTime.of(0, 30));
            assertThat(settings.backfillDays()).isEqualTo(31);
        });
        runner.withPropertyValues(replaceAll(valid(), "tt.pipeline.statistics.zone=UTC",
                "tt.pipeline.statistics.daily-at=03:15", "tt.pipeline.statistics.backfill-days=0")
                .toArray(String[]::new)).run(context -> {
            StatisticsSettings settings = context.getBean(PipelineOrchestratorProperties.class).statisticsSettings();
            assertThat(settings.zone()).isEqualTo(ZoneId.of("UTC"));
            assertThat(settings.dailyAt()).isEqualTo(LocalTime.of(3, 15));
            assertThat(settings.backfillDays()).isZero();
        });
    }

    @Test
    void failsWhenTheStatisticsZoneIsMissingBlankOrInvalid() {
        assertFails(without("tt.pipeline.statistics.zone"), "statistics");
        assertFails(replace("tt.pipeline.statistics.zone", "   "), "statistics.zone is required");
        assertFails(replace("tt.pipeline.statistics.zone", "Mars/Olympus"), "statistics.zone is not a valid time zone");
    }

    @Test
    void failsWhenBackfillDaysIsOutOfRange() {
        assertFails(replaceAll(valid(), "tt.pipeline.statistics.backfill-days=-1"), "statistics.backfill-days");
        assertFails(replaceAll(valid(), "tt.pipeline.statistics.backfill-days=367"), "statistics.backfill-days");
    }

    @Test
    void failsOnInvalidEventSettings() {
        assertFails(replace("tt.pipeline.events.heartbeat-interval", "PT0S"), "events.heartbeat-interval");
        assertFails(replace("tt.pipeline.events.emitter-timeout", "-PT1S"), "events.emitter-timeout");
        assertFails(replace("tt.pipeline.events.max-subscribers", "0"), "events.max-subscribers");
        assertFails(replace("tt.pipeline.events.max-subscribers", "1001"), "events.max-subscribers");
    }

    @Test
    void failsWhenPlatformUrlIsMissing() {
        assertFails(without("tt.pipeline.platform.base-url"), "platform");
    }

    @Test
    void failsWhenIngestUrlIsBlank() {
        assertFails(replace("tt.pipeline.ingest.base-url", ""), "baseUrl");
    }

    @Test
    void failsWhenTheArtifactsDirIsMissing() {
        assertFails(without("tt.pipeline.artifacts.dir"), "artifacts");
    }

    @Test
    void failsOnAZeroOrMissingTimeoutOrPollInterval() {
        assertFails(replace("tt.pipeline.platform.connect-timeout", "PT0S"), "platform.connect-timeout");
        assertFails(replace("tt.pipeline.platform.read-timeout", "-PT1S"), "platform.read-timeout");
        assertFails(replace("tt.pipeline.platform.poll-interval", "PT0S"), "platform.poll-interval");
        assertFails(without("tt.pipeline.ingest.connect-timeout"), "ingest.connect-timeout");
        assertFails(replace("tt.pipeline.ingest.read-timeout", "PT0S"), "ingest.read-timeout");
        assertFails(replace("tt.pipeline.ingest.poll-interval", "PT0S"), "ingest.poll-interval");
        assertFails(replace("tt.pipeline.execution.timeouts.ingest", "PT0S"), "timeouts.ingest");
        assertFails(replace("tt.pipeline.execution.timeouts.fetch-package", "PT0S"), "timeouts.fetch-package");
        assertFails(replace("tt.pipeline.execution.timeouts.import-job", "PT0S"), "timeouts.import-job");
        assertFails(replace("tt.pipeline.execution.initial-backoff", "PT0S"), "initial-backoff");
    }

    @Test
    void failsOnInvalidRetrySettings() {
        assertFails(replace("tt.pipeline.execution.max-retries", "-1"), "maxRetries");
        assertFails(replace("tt.pipeline.execution.max-retries", "11"), "maxRetries");
        assertFails(replace("tt.pipeline.execution.backoff-multiplier", "0.5"), "multiplier");
        assertFails(replace("tt.pipeline.execution.max-backoff", "PT10S"), "maxBackoff");
    }

    @Test
    void failsWhenMaxConcurrentRunsIsOutsideOneToThree() {
        assertFails(replace("tt.pipeline.execution.max-concurrent-runs", "0"), "max-concurrent-runs");
        assertFails(replace("tt.pipeline.execution.max-concurrent-runs", "4"), "max-concurrent-runs");
    }

    @Test
    void failsWhenRecoverOnStartupIsMissing() {
        assertFails(without("tt.pipeline.execution.recover-on-startup"), "recover-on-startup");
    }

    @Test
    void trackerSettingsDefaultWhenNothingIsConfigured() {
        runner.withPropertyValues(valid().toArray(String[]::new)).run(context -> {
            PipelineOrchestratorProperties.Tracker tracker =
                    context.getBean(PipelineOrchestratorProperties.class).tracker();
            assertThat(tracker.recomputeInterval()).isEqualTo(Duration.ofHours(1));
            assertThat(tracker.lockAtMostFor()).isEqualTo(Duration.ofMinutes(10));
            assertThat(tracker.lockAtLeastFor()).isEqualTo(Duration.ofSeconds(30));
        });
    }

    @Test
    void bindsTheTrackerSettings() {
        List<String> properties = valid();
        properties.add("tt.pipeline.tracker.recompute-interval=PT15M");
        properties.add("tt.pipeline.tracker.lock-at-most-for=PT5M");
        properties.add("tt.pipeline.tracker.lock-at-least-for=PT10S");

        runner.withPropertyValues(properties.toArray(String[]::new)).run(context -> {
            PipelineOrchestratorProperties.Tracker tracker =
                    context.getBean(PipelineOrchestratorProperties.class).tracker();
            assertThat(tracker.recomputeInterval()).isEqualTo(Duration.ofMinutes(15));
            assertThat(tracker.lockAtMostFor()).isEqualTo(Duration.ofMinutes(5));
            assertThat(tracker.lockAtLeastFor()).isEqualTo(Duration.ofSeconds(10));
        });
    }

    @Test
    void failsWhenATrackerDurationIsNotPositive() {
        assertFails(withTracker("recompute-interval=PT0S"), "tracker.recompute-interval");
        assertFails(withTracker("lock-at-most-for=-PT1M"), "tracker.lock-at-most-for");
        assertFails(withTracker("lock-at-least-for=PT0S"), "tracker.lock-at-least-for");
    }

    @Test
    void failsWhenTheTrackerLockAtLeastExceedsAtMost() {
        assertFails(withTracker("lock-at-most-for=PT10S", "lock-at-least-for=PT1M"),
                "tracker.lock-at-least-for must not exceed");
    }

    @Test
    void nothingIsScheduledWithoutAnyCron() {
        runner.withPropertyValues(valid().toArray(String[]::new)).run(context -> {
            assertThat(context).hasNotFailed();
            PipelineOrchestratorProperties.Schedule schedule =
                    context.getBean(PipelineOrchestratorProperties.class).schedule();
            assertThat(schedule.scheduledSources()).isEmpty();
        });
    }

    @Test
    void blankCronsSeasonAndZoneMeanNothingIsScheduled() {
        List<String> properties = valid();
        properties.addAll(List.of(
                "tt.pipeline.schedule.season=",
                "tt.pipeline.schedule.zone=",
                "tt.pipeline.schedule.lock-at-most-for=PT10M",
                "tt.pipeline.schedule.lock-at-least-for=PT30S",
                "tt.pipeline.schedule.sources.RFETM.cron=",
                "tt.pipeline.schedule.sources.BCNESA.cron=   ",
                "tt.pipeline.schedule.sources.FCTT.cron="));
        runner.withPropertyValues(properties.toArray(String[]::new)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(PipelineOrchestratorProperties.class).schedule().scheduledSources()).isEmpty();
        });
    }

    @Test
    void bindsTheScheduledSourcesOnly() {
        List<String> properties = scheduled();
        properties.add("tt.pipeline.schedule.sources.FCTT.cron= 0 30 21 * * MON-FRI ");
        runner.withPropertyValues(properties.toArray(String[]::new)).run(context -> {
            assertThat(context).hasNotFailed();
            PipelineOrchestratorProperties.Schedule schedule =
                    context.getBean(PipelineOrchestratorProperties.class).schedule();
            assertThat(schedule.scheduledSources()).containsExactly(PipelineSource.RFETM, PipelineSource.FCTT);
            assertThat(schedule.cron(PipelineSource.RFETM)).isEqualTo("0 0 7,22 * * *");
            assertThat(schedule.cron(PipelineSource.FCTT)).isEqualTo("0 30 21 * * MON-FRI");
            assertThat(schedule.season()).isEqualTo("2025-2026");
            assertThat(schedule.zoneId()).isEqualTo(ZoneId.of("Europe/Madrid"));
            assertThat(schedule.lockAtMostFor()).isEqualTo(Duration.ofMinutes(10));
            assertThat(schedule.lockAtLeastFor()).isEqualTo(Duration.ofSeconds(30));
        });
    }

    @Test
    void failsOnAnInvalidCron() {
        assertFails(replaceIn(scheduled(), "tt.pipeline.schedule.sources.RFETM.cron", "every morning"),
                "schedule.sources.RFETM.cron is not a valid cron expression");
        // Unix five-field cron is not the Spring format
        assertFails(replaceIn(scheduled(), "tt.pipeline.schedule.sources.RFETM.cron", "0 7 * * *"),
                "schedule.sources.RFETM.cron");
    }

    @Test
    void failsOnAnUnknownSource() {
        List<String> properties = scheduled();
        properties.add("tt.pipeline.schedule.sources.XYZ.cron=0 0 7 * * *");
        assertFails(properties, "XYZ");
    }

    @Test
    void failsWhenACronIsSetWithoutAValidSeason() {
        assertFails(replaceIn(scheduled(), "tt.pipeline.schedule.season", ""), "schedule.season is required");
        assertFails(withoutIn(scheduled(), "tt.pipeline.schedule.season"), "schedule.season is required");
        assertFails(replaceIn(scheduled(), "tt.pipeline.schedule.season", "2025-2027"), "schedule.season is invalid");
    }

    @Test
    void failsWhenACronIsSetWithoutAValidZone() {
        assertFails(replaceIn(scheduled(), "tt.pipeline.schedule.zone", " "), "schedule.zone is required");
        assertFails(replaceIn(scheduled(), "tt.pipeline.schedule.zone", "Mars/Olympus"),
                "schedule.zone is not a valid time zone");
    }

    @Test
    void failsOnInvalidLockDurations() {
        assertFails(replaceIn(scheduled(), "tt.pipeline.schedule.lock-at-most-for", "PT0S"),
                "schedule.lock-at-most-for");
        assertFails(withoutIn(scheduled(), "tt.pipeline.schedule.lock-at-least-for"), "schedule.lock-at-least-for");
        assertFails(replaceIn(scheduled(), "tt.pipeline.schedule.lock-at-least-for", "PT11M"),
                "schedule.lock-at-least-for must not exceed");
    }

    @Test
    void adaptivePollingIsOffByDefaultWithDocumentedDefaults() {
        runner.withPropertyValues(valid().toArray(String[]::new)).run(context -> {
            assertThat(context).hasNotFailed();
            PipelineOrchestratorProperties.Polling polling =
                    context.getBean(PipelineOrchestratorProperties.class).polling();
            assertThat(polling.sources()).isEmpty();
            assertThat(polling.tickInterval()).isEqualTo(Duration.ofMinutes(5));
            assertThat(polling.lockAtMostFor()).isEqualTo(Duration.ofMinutes(10));
            assertThat(polling.lockAtLeastFor()).isEqualTo(Duration.ofSeconds(30));
            assertThat(polling.defaultSettings()).isEqualTo(PollingSettings.defaults());
            assertThat(polling.defaultSettings().recentMatchDays()).isEqualTo(3);
            assertThat(polling.bcnesaCompetitionNames()).hasSize(16).containsEntry("rtb-segona-a", "Segona _A_");
        });
    }

    @Test
    void bindsTheAdaptiveSourcesAndTheDefaults() {
        List<String> properties = polled("tt.pipeline.polling.sources=FCTT,bcnesa");
        properties.add("tt.pipeline.polling.tick-interval=PT1M");
        properties.add("tt.pipeline.polling.defaults.match-day=PT1H");
        properties.add("tt.pipeline.polling.defaults.overdue-stop-after-days=14");
        properties.add("tt.pipeline.polling.defaults.recent-match-days=5");
        properties.add("tt.pipeline.polling.bcnesa-competition-names.rtb-nova=Nova");
        runner.withPropertyValues(properties.toArray(String[]::new)).run(context -> {
            assertThat(context).hasNotFailed();
            PipelineOrchestratorProperties.Polling polling =
                    context.getBean(PipelineOrchestratorProperties.class).polling();
            assertThat(polling.sources()).containsExactlyInAnyOrder(PipelineSource.FCTT, PipelineSource.BCNESA);
            assertThat(polling.tickInterval()).isEqualTo(Duration.ofMinutes(1));
            PollingSettings settings = polling.defaultSettings();
            assertThat(settings.matchDay()).isEqualTo(Duration.ofHours(1));
            assertThat(settings.overdueStopAfterDays()).isEqualTo(14);
            assertThat(settings.recentMatchDays()).isEqualTo(5);
            assertThat(settings.dayAfter()).isEqualTo(Duration.ofHours(3));
            assertThat(polling.bcnesaCompetitionNames()).containsOnlyKeys("rtb-nova");
        });
    }

    @Test
    void aBlankSourcesListMeansAdaptivePollingIsOff() {
        runner.withPropertyValues(polled("tt.pipeline.polling.sources=").toArray(String[]::new)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(PipelineOrchestratorProperties.class).polling().sources()).isEmpty();
        });
    }

    @Test
    void failsWhenASourceHasBothACronAndAdaptivePolling() {
        List<String> properties = scheduled();
        properties.add("tt.pipeline.polling.sources=RFETM");
        assertFails(properties, "source RFETM has both a cron and adaptive polling");
    }

    @Test
    void adaptivePollingCoexistsWithACronOnAnotherSource() {
        List<String> properties = scheduled();
        properties.add("tt.pipeline.polling.sources=FCTT");
        runner.withPropertyValues(properties.toArray(String[]::new)).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void adaptivePollingNeedsTheScheduleSeasonAndZone() {
        List<String> properties = valid();
        properties.add("tt.pipeline.polling.sources=FCTT");
        assertFails(properties, "schedule.season is required when adaptive polling is enabled");
        properties.add("tt.pipeline.schedule.season=2026-2027");
        assertFails(properties, "schedule.zone is required when adaptive polling is enabled");
        properties.add("tt.pipeline.schedule.zone=Mars/Olympus");
        assertFails(properties, "schedule.zone is not a valid time zone");
    }

    @Test
    void failsOnInvalidPollingDefaults() {
        assertFails(withPolling("defaults.match-day=PT5H", "defaults.day-after=PT3H"), "polling.defaults is invalid");
        assertFails(withPolling("defaults.no-change-threshold=0"), "noChangeThreshold");
        assertFails(withPolling("defaults.recent-match-days=0"), "recentMatchDays must be at least 1");
        assertFails(withPolling("defaults.full-refresh=PT1H"), "polling.defaults is invalid");
    }

    @Test
    void failsOnInvalidPollingTimings() {
        assertFails(withPolling("tick-interval=PT0S"), "polling.tick-interval");
        assertFails(withPolling("lock-at-most-for=-PT1M"), "polling.lock-at-most-for");
        assertFails(withPolling("lock-at-most-for=PT10S", "lock-at-least-for=PT1M"),
                "polling.lock-at-least-for must not exceed");
    }

    @Test
    void failsOnAnUnknownPollingSource() {
        assertFails(withPolling("sources=XYZ"), "XYZ");
    }

    private static List<String> polled(String sources) {
        List<String> properties = valid();
        properties.add("tt.pipeline.schedule.season=2026-2027");
        properties.add("tt.pipeline.schedule.zone=Europe/Madrid");
        properties.add(sources);
        return properties;
    }

    private static List<String> withPolling(String... settings) {
        List<String> properties = valid();
        for (String setting : settings) {
            properties.add("tt.pipeline.polling." + setting);
        }
        return properties;
    }

    private static List<String> scheduled() {
        List<String> properties = valid();
        properties.addAll(List.of(
                "tt.pipeline.schedule.season=2025-2026",
                "tt.pipeline.schedule.zone=Europe/Madrid",
                "tt.pipeline.schedule.lock-at-most-for=PT10M",
                "tt.pipeline.schedule.lock-at-least-for=PT30S",
                "tt.pipeline.schedule.sources.RFETM.cron=0 0 7,22 * * *"));
        return properties;
    }

    private static List<String> replaceIn(List<String> properties, String key, String value) {
        List<String> result = withoutIn(properties, key);
        result.add(key + "=" + value);
        return result;
    }

    private static List<String> withoutIn(List<String> properties, String key) {
        List<String> result = new ArrayList<>(properties);
        result.removeIf(entry -> entry.startsWith(key + "="));
        return result;
    }

    private static List<String> withMail(String... extra) {
        List<String> properties = valid();
        properties.addAll(List.of(
                "tt.pipeline.notifications.mail.host=smtp.example.org",
                "tt.pipeline.notifications.mail.from=pipeline@example.org",
                "tt.pipeline.notifications.mail.to=ops@example.org,admin@example.org"));
        properties.addAll(List.of(extra));
        return properties;
    }

    private static List<String> withMailWithout(String key) {
        List<String> properties = withMail();
        properties.removeIf(entry -> entry.startsWith(key + "="));
        return properties;
    }

    @Test
    void notificationsAreDisabledByDefault() {
        runner.withPropertyValues(valid().toArray(String[]::new)).run(context -> {
            PipelineOrchestratorProperties.Notifications notifications =
                    context.getBean(PipelineOrchestratorProperties.class).notifications();
            assertThat(notifications.enabled()).isFalse();
            assertThat(notifications.mail().host()).isNull();
        });
    }

    @Test
    void aBlankMailHostKeepsNotificationsOffAndIgnoresTheOtherValues() {
        List<String> properties = valid();
        properties.addAll(List.of(
                "tt.pipeline.notifications.mail.host=   ",
                "tt.pipeline.notifications.mail.port=0",
                "tt.pipeline.notifications.mail.from=not-an-address",
                "tt.pipeline.notifications.evaluate-interval=PT0S"));

        runner.withPropertyValues(properties.toArray(String[]::new)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(PipelineOrchestratorProperties.class).notifications().enabled()).isFalse();
        });
    }

    @Test
    void bindsEnabledNotificationsWithTheirDefaults() {
        runner.withPropertyValues(withMail().toArray(String[]::new)).run(context -> {
            assertThat(context).hasNotFailed();
            PipelineOrchestratorProperties.Notifications notifications =
                    context.getBean(PipelineOrchestratorProperties.class).notifications();
            assertThat(notifications.enabled()).isTrue();
            assertThat(notifications.mail().port()).isEqualTo(587);
            assertThat(notifications.mail().starttls()).isTrue();
            assertThat(notifications.mail().subjectPrefix()).isEqualTo("[tt-pipeline]");
            assertThat(notifications.mail().to()).containsExactly("ops@example.org", "admin@example.org");
            assertThat(notifications.evaluateInterval()).isEqualTo(Duration.ofMinutes(15));
            assertThat(notifications.alertSettings().unreportedAfter()).isEqualTo(Duration.ofHours(48));
            assertThat(notifications.alertSettings().noSuccessWindow()).isEqualTo(Duration.ofHours(24));
            assertThat(notifications.alertSettings().closedLookback()).isEqualTo(Duration.ofDays(1));
        });
    }

    @Test
    void unitFailureAlertsCoverEveryUnitUnlessKeysAreListed() {
        runner.withPropertyValues(withMail().toArray(String[]::new)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(PipelineOrchestratorProperties.class).notifications().alertSettings().unitKeys())
                    .isEmpty();
        });
        // a blank PIPELINE_ALERTS_UNIT_KEYS binds as an empty value
        runner.withPropertyValues(withMail("tt.pipeline.notifications.unit-failures.unit-keys=").toArray(String[]::new))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(PipelineOrchestratorProperties.class).notifications()
                            .alertSettings().unitKeys()).isEmpty();
                });
        runner.withPropertyValues(withMail("tt.pipeline.notifications.unit-failures.unit-keys=season," + "ab".repeat(32))
                .toArray(String[]::new)).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(PipelineOrchestratorProperties.class).notifications()
                            .alertSettings().unitKeys()).containsExactlyInAnyOrder("season", "ab".repeat(32));
                });
    }

    @Test
    void unitFailureKeysMustBeOneToSixtyFourCharacters() {
        assertFails(withMail("tt.pipeline.notifications.unit-failures.unit-keys=season," + "x".repeat(65)),
                "notifications.unit-failures.unit-keys");
        assertFails(withMail("tt.pipeline.notifications.unit-failures.unit-keys=season, ,legacy"),
                "notifications.unit-failures.unit-keys");
    }

    @Test
    void enabledNotificationsRequireFromAndRecipients() {
        assertFails(withMailWithout("tt.pipeline.notifications.mail.from"), "notifications.mail.from");
        assertFails(withMailWithout("tt.pipeline.notifications.mail.to"), "notifications.mail.to");
    }

    @Test
    void enabledNotificationsRejectBadAddressesPortsAndCredentialPairs() {
        List<String> badFrom = withMailWithout("tt.pipeline.notifications.mail.from");
        badFrom.add("tt.pipeline.notifications.mail.from=nobody");
        assertFails(badFrom, "notifications.mail.from");

        List<String> badTo = withMailWithout("tt.pipeline.notifications.mail.to");
        badTo.add("tt.pipeline.notifications.mail.to=ops@example.org,broken@");
        assertFails(badTo, "notifications.mail.to");

        assertFails(withMail("tt.pipeline.notifications.mail.port=70000"), "notifications.mail.port");
        assertFails(withMail("tt.pipeline.notifications.mail.port=0"), "notifications.mail.port");
        assertFails(withMail("tt.pipeline.notifications.mail.username=ops"),
                "notifications.mail.username and notifications.mail.password");
        assertFails(withMail("tt.pipeline.notifications.mail.password=s3cret"),
                "notifications.mail.username and notifications.mail.password");
    }

    @Test
    void enabledNotificationsRejectNonPositiveDurations() {
        assertFails(withMail("tt.pipeline.notifications.evaluate-interval=PT0S"), "notifications.evaluate-interval");
        assertFails(withMail("tt.pipeline.notifications.unreported-after=-PT1H"), "notifications.unreported-after");
        assertFails(withMail("tt.pipeline.notifications.no-success-window=PT0S"), "notifications.no-success-window");
        assertFails(withMail("tt.pipeline.notifications.closed-lookback=PT0S"), "notifications.closed-lookback");
    }

    @Test
    void theMailToStringMasksThePassword() {
        runner.withPropertyValues(withMail(
                "tt.pipeline.notifications.mail.username=ops",
                "tt.pipeline.notifications.mail.password=s3cret-value").toArray(String[]::new)).run(context -> {
            PipelineOrchestratorProperties properties = context.getBean(PipelineOrchestratorProperties.class);
            assertThat(properties.notifications().mail().password()).isEqualTo("s3cret-value");
            assertThat(properties.notifications().mail().toString()).doesNotContain("s3cret-value").contains("****");
            assertThat(properties.toString()).doesNotContain("s3cret-value");
        });
    }

    private void assertFails(List<String> properties, String expectedFragment) {
        runner.withPropertyValues(properties.toArray(String[]::new)).run(context -> {
            assertThat(context).hasFailed();
            StringBuilder messages = new StringBuilder();
            for (Throwable t = context.getStartupFailure(); t != null; t = t.getCause()) {
                messages.append(t.getMessage()).append('\n');
            }
            assertThat(messages.toString()).contains(expectedFragment);
        });
    }

    private static List<String> withTracker(String... settings) {
        List<String> properties = valid();
        for (String setting : settings) {
            properties.add("tt.pipeline.tracker." + setting);
        }
        return properties;
    }

    private static List<String> valid() {
        return new ArrayList<>(List.of(
                "tt.pipeline.platform.base-url=http://localhost:8080",
                "tt.pipeline.platform.api-key=platform-key",
                "tt.pipeline.platform.connect-timeout=PT10S",
                "tt.pipeline.platform.read-timeout=PT5M",
                "tt.pipeline.platform.poll-interval=PT10S",
                "tt.pipeline.ingest.base-url=http://localhost:8000",
                "tt.pipeline.ingest.api-key=key",
                "tt.pipeline.ingest.connect-timeout=PT10S",
                "tt.pipeline.ingest.read-timeout=PT1M",
                "tt.pipeline.ingest.poll-interval=PT15S",
                "tt.pipeline.artifacts.dir=artifacts",
                "tt.pipeline.execution.max-retries=3",
                "tt.pipeline.execution.initial-backoff=PT30S",
                "tt.pipeline.execution.backoff-multiplier=2",
                "tt.pipeline.execution.max-backoff=PT5M",
                "tt.pipeline.execution.timeouts.ingest=PT3H",
                "tt.pipeline.execution.timeouts.fetch-package=PT10M",
                "tt.pipeline.execution.timeouts.import-job=PT3H",
                "tt.pipeline.execution.max-concurrent-runs=3",
                "tt.pipeline.execution.recover-on-startup=true",
                "tt.pipeline.statistics.zone=Europe/Madrid",
                "tt.pipeline.security.jwt-secret=" + VALID_SECRET,
                "tt.pipeline.triggers.conflict-mode=REJECT",
                "tt.pipeline.events.heartbeat-interval=PT15S",
                "tt.pipeline.events.emitter-timeout=PT30M",
                "tt.pipeline.events.max-subscribers=50"));
    }

    private static List<String> replaceAll(List<String> base, String... entries) {
        List<String> properties = new ArrayList<>(base);
        for (String entry : entries) {
            String key = entry.substring(0, entry.indexOf('='));
            properties.removeIf(property -> property.startsWith(key + "="));
            properties.add(entry);
        }
        return properties;
    }

    private static List<String> replace(String key, String value) {
        List<String> properties = without(key);
        properties.add(key + "=" + value);
        return properties;
    }

    private static List<String> without(String key) {
        List<String> properties = valid();
        properties.removeIf(entry -> entry.startsWith(key + "="));
        return properties;
    }

    @Configuration
    @EnableConfigurationProperties(PipelineOrchestratorProperties.class)
    static class PropertiesConfig {
    }

    private static List<String> withRetention(String... settings) {
        List<String> properties = valid();
        properties.add("tt.pipeline.retention.cron=0 30 4 * * *");
        properties.add("tt.pipeline.retention.zone=Europe/Madrid");
        properties.add("tt.pipeline.retention.rules.zip.seasons=1");
        properties.add("tt.pipeline.retention.rules.manifest.seasons=1");
        properties.add("tt.pipeline.retention.rules.raw.max-age=P90D");
        properties.add("tt.pipeline.retention.rules.json.max-age=P90D");
        for (String setting : settings) {
            properties.removeIf(entry -> entry.startsWith(setting.substring(0, setting.indexOf('=') + 1)));
            properties.add(setting);
        }
        return properties;
    }

    @Test
    void retentionIsOffWithoutTheBlock() {
        runner.withPropertyValues(valid().toArray(String[]::new)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(PipelineOrchestratorProperties.class).retention()).isNull();
        });
    }

    @Test
    void bindsTheRetentionBlockIntoAPolicy() {
        runner.withPropertyValues(withRetention().toArray(String[]::new)).run(context -> {
            assertThat(context).hasNotFailed();
            PipelineOrchestratorProperties.Retention retention =
                    context.getBean(PipelineOrchestratorProperties.class).retention();
            assertThat(retention.cron()).isEqualTo("0 30 4 * * *");
            assertThat(retention.zoneId()).isEqualTo(ZoneId.of("Europe/Madrid"));
            assertThat(retention.toPolicy().rules())
                    .containsEntry(org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind.ZIP,
                            new org.cttelsamicsterrassa.data.pipeline.core.retention.RetentionRule.Seasons(1))
                    .containsEntry(org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind.RAW,
                            new org.cttelsamicsterrassa.data.pipeline.core.retention.RetentionRule.MaxAge(
                                    Duration.ofDays(90)));
        });
    }

    @Test
    void failsOnAMissingOrInvalidRetentionCronOrZone() {
        List<String> noCron = withRetention();
        noCron.removeIf(entry -> entry.startsWith("tt.pipeline.retention.cron="));
        assertFails(noCron, "retention.cron is required");
        assertFails(withRetention("tt.pipeline.retention.cron=nightly"), "retention.cron is not a valid cron");
        assertFails(withRetention("tt.pipeline.retention.cron=0 30 4 * * *", "tt.pipeline.retention.zone=Mars/Base"),
                "retention.zone is not a valid time zone");
        List<String> noZone = withRetention();
        noZone.removeIf(entry -> entry.startsWith("tt.pipeline.retention.zone="));
        assertFails(noZone, "retention.zone is required");
    }

    @Test
    void failsWhenAnArtifactKindHasNoRuleOrAnInvalidOne() {
        List<String> noJson = withRetention();
        noJson.removeIf(entry -> entry.startsWith("tt.pipeline.retention.rules.json."));
        assertFails(noJson, "retention.rules.json is required");
        assertFails(withRetention("tt.pipeline.retention.rules.zip.max-age=P1D"),
                "retention.rules.zip needs exactly one of max-age or seasons");
        List<String> neither = withRetention();
        neither.removeIf(entry -> entry.startsWith("tt.pipeline.retention.rules.raw."));
        neither.add("tt.pipeline.retention.rules.raw.seasons=");
        assertFails(neither, "retention.rules.raw");
        assertFails(withRetention("tt.pipeline.retention.rules.zip.seasons=11"),
                "retention.rules.zip.seasons must be between 1 and 10");
        assertFails(withRetention("tt.pipeline.retention.rules.zip.seasons=0"),
                "retention.rules.zip.seasons must be between 1 and 10");
        List<String> badAge = withRetention();
        badAge.removeIf(entry -> entry.startsWith("tt.pipeline.retention.rules.raw."));
        badAge.add("tt.pipeline.retention.rules.raw.max-age=PT0S");
        assertFails(badAge, "retention.rules.raw.max-age must be a positive duration");
    }
}
