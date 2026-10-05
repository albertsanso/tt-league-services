package org.cttelsamicsterrassa.data.pipeline.runtime.config;

import org.cttelsamicsterrassa.data.pipeline.core.alert.AlertSettings;
import org.cttelsamicsterrassa.data.pipeline.core.execution.ExecutionSettings;
import org.cttelsamicsterrassa.data.pipeline.core.execution.PollIntervals;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RetryPolicy;
import org.cttelsamicsterrassa.data.pipeline.core.execution.StepTimeouts;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollingSettings;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.BcnesaCompetitionNames;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ConflictMode;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.validation.annotation.Validated;

/**
 * Orchestrator configuration. Durations must be positive and the retry settings valid: violations fail in the compact
 * constructors, so binding (and startup) fails with the offending setting named.
 */
@Validated
@ConfigurationProperties("tt.pipeline")
public record PipelineOrchestratorProperties(
        @Valid @NotNull Platform platform,
        @Valid @NotNull Ingest ingest,
        @Valid @NotNull Artifacts artifacts,
        @Valid @NotNull Execution execution,
        @Valid @NotNull Security security,
        @Valid @NotNull Triggers triggers,
        @Valid @NotNull Events events,
        Schedule schedule,
        Tracker tracker,
        Polling polling,
        Notifications notifications) {

    public PipelineOrchestratorProperties {
        schedule = schedule == null ? Schedule.none() : schedule;
        tracker = tracker == null ? Tracker.defaults() : tracker;
        polling = polling == null ? Polling.none() : polling;
        notifications = notifications == null ? Notifications.disabled() : notifications;
        for (PipelineSource source : polling.sources()) {
            if (schedule.sources().containsKey(source)) {
                throw new IllegalArgumentException(
                        "source " + source + " has both a cron and adaptive polling; configure only one");
            }
        }
        if (!polling.sources().isEmpty()) {
            schedule.requireSeasonAndZone("adaptive polling is enabled");
        }
    }

    /** Platform REST API; {@code apiKey} is a service credential with {@code imports:write}. */
    public record Platform(
            @NotNull URI baseUrl,
            @NotBlank String apiKey,
            Duration connectTimeout,
            Duration readTimeout,
            Duration pollInterval) {

        public Platform {
            positive(connectTimeout, "platform.connect-timeout");
            positive(readTimeout, "platform.read-timeout");
            positive(pollInterval, "platform.poll-interval");
        }
    }

    public record Ingest(
            @NotNull URI baseUrl,
            @NotBlank String apiKey,
            Duration connectTimeout,
            Duration readTimeout,
            Duration pollInterval) {

        public Ingest {
            positive(connectTimeout, "ingest.connect-timeout");
            positive(readTimeout, "ingest.read-timeout");
            positive(pollInterval, "ingest.poll-interval");
        }
    }

    public record Artifacts(@NotNull Path dir) {
    }

    public record Execution(
            Integer maxRetries,
            Duration initialBackoff,
            Double backoffMultiplier,
            Duration maxBackoff,
            @Valid @NotNull Timeouts timeouts,
            Integer maxConcurrentRuns,
            Boolean recoverOnStartup) {

        public Execution {
            required(maxRetries, "execution.max-retries");
            required(backoffMultiplier, "execution.backoff-multiplier");
            required(recoverOnStartup, "execution.recover-on-startup");
            required(maxConcurrentRuns, "execution.max-concurrent-runs");
            if (maxConcurrentRuns < 1 || maxConcurrentRuns > 3) {
                throw new IllegalArgumentException(
                        "execution.max-concurrent-runs must be between 1 and 3: " + maxConcurrentRuns);
            }
            positive(initialBackoff, "execution.initial-backoff");
            positive(maxBackoff, "execution.max-backoff");
            // validates 0 <= maxRetries <= 10, multiplier >= 1 and maxBackoff >= initialBackoff
            retryPolicy(maxRetries, initialBackoff, backoffMultiplier, maxBackoff);
        }

        public record Timeouts(Duration ingest, Duration fetchPackage, Duration importJob) {

            public Timeouts {
                positive(ingest, "execution.timeouts.ingest");
                positive(fetchPackage, "execution.timeouts.fetch-package");
                positive(importJob, "execution.timeouts.import-job");
            }
        }
    }

    /**
     * {@code jwtSecret} is the platform's {@code security.jwt.secret}: at least 32 UTF-8 bytes, like the platform
     * checks. {@code corsAllowedOrigins} are absolute http(s) origins; empty means no CORS headers.
     */
    public record Security(@NotBlank String jwtSecret, List<String> corsAllowedOrigins) {

        public Security {
            required(jwtSecret, "security.jwt-secret");
            if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalArgumentException("security.jwt-secret must be at least 32 UTF-8 bytes");
            }
            corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : List.copyOf(corsAllowedOrigins);
            corsAllowedOrigins.forEach(Security::validOrigin);
        }

        private static void validOrigin(String origin) {
            URI uri;
            try {
                uri = new URI(origin);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(
                        "security.cors-allowed-origins must hold absolute http(s) origins: " + origin, e);
            }
            boolean httpScheme = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
            boolean bareOrigin = uri.getHost() != null && uri.getUserInfo() == null && uri.getQuery() == null
                    && uri.getFragment() == null && (uri.getPath() == null || uri.getPath().isEmpty());
            if (!httpScheme || !bareOrigin) {
                throw new IllegalArgumentException(
                        "security.cors-allowed-origins must hold absolute http(s) origins: " + origin);
            }
        }
    }

    /** What a manual trigger does when its source already has an active run. */
    public record Triggers(ConflictMode conflictMode) {

        public Triggers {
            required(conflictMode, "triggers.conflict-mode");
        }
    }

    /** Server-Sent Events stream limits. */
    public record Events(Duration heartbeatInterval, Duration emitterTimeout, Integer maxSubscribers) {

        public Events {
            positive(heartbeatInterval, "events.heartbeat-interval");
            positive(emitterTimeout, "events.emitter-timeout");
            required(maxSubscribers, "events.max-subscribers");
            if (maxSubscribers < 1 || maxSubscribers > 1000) {
                throw new IllegalArgumentException("events.max-subscribers must be between 1 and 1000");
            }
        }
    }

    /**
     * Fixed-schedule trigger. A source is scheduled only when its {@code cron} (Spring six-field format) is set;
     * blank means never. As soon as one source is scheduled, {@code season} and {@code zone} are required and the
     * ShedLock durations must be positive with {@code lockAtLeastFor <= lockAtMostFor}. {@code sources} keeps only
     * the scheduled sources, in source order.
     */
    public record Schedule(
            String season,
            String zone,
            Duration lockAtMostFor,
            Duration lockAtLeastFor,
            Map<PipelineSource, SourceSchedule> sources) {

        public record SourceSchedule(String cron) {
        }

        public Schedule {
            Map<PipelineSource, SourceSchedule> scheduled = new EnumMap<>(PipelineSource.class);
            if (sources != null) {
                sources.forEach((source, value) -> {
                    if (value != null && value.cron() != null && !value.cron().isBlank()) {
                        String cron = value.cron().trim();
                        validCron(source, cron);
                        scheduled.put(source, new SourceSchedule(cron));
                    }
                });
            }
            sources = Collections.unmodifiableMap(scheduled);
            if (lockAtMostFor != null) {
                positive(lockAtMostFor, "schedule.lock-at-most-for");
            }
            if (lockAtLeastFor != null) {
                positive(lockAtLeastFor, "schedule.lock-at-least-for");
            }
            if (!sources.isEmpty()) {
                validSeason(season, "a source schedule is set");
                validZone(zone, "a source schedule is set");
                positive(lockAtMostFor, "schedule.lock-at-most-for");
                positive(lockAtLeastFor, "schedule.lock-at-least-for");
                if (lockAtLeastFor.compareTo(lockAtMostFor) > 0) {
                    throw new IllegalArgumentException(
                            "schedule.lock-at-least-for must not exceed schedule.lock-at-most-for");
                }
            }
        }

        static Schedule none() {
            return new Schedule(null, null, null, null, Map.of());
        }

        public List<PipelineSource> scheduledSources() {
            return List.copyOf(sources.keySet());
        }

        public String cron(PipelineSource source) {
            SourceSchedule value = sources.get(source);
            if (value == null) {
                throw new IllegalArgumentException("Source " + source + " has no schedule");
            }
            return value.cron();
        }

        /** Only valid when a source is scheduled. */
        public ZoneId zoneId() {
            return ZoneId.of(zone.trim());
        }

        private static void validCron(PipelineSource source, String cron) {
            try {
                CronExpression.parse(cron);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "schedule.sources." + source + ".cron is not a valid cron expression: " + cron, e);
            }
        }

        /** Adaptive polling reuses the schedule season and zone instead of having its own. */
        void requireSeasonAndZone(String context) {
            validSeason(season, context);
            validZone(zone, context);
        }

        private static void validSeason(String season, String context) {
            if (season == null || season.isBlank()) {
                throw new IllegalArgumentException("schedule.season is required when " + context);
            }
            try {
                PipelineRun.requireValidSeason(season);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("schedule.season is invalid: " + e.getMessage(), e);
            }
        }

        private static void validZone(String zone, String context) {
            if (zone == null || zone.isBlank()) {
                throw new IllegalArgumentException("schedule.zone is required when " + context);
            }
            try {
                ZoneId.of(zone.trim());
            } catch (DateTimeException e) {
                throw new IllegalArgumentException("schedule.zone is not a valid time zone: " + zone, e);
            }
        }
    }

    /**
     * Match-day tracker. {@code recomputeInterval} is the fixed delay of the periodic recompute; the ShedLock
     * durations bound its lock ({@code lockAtLeastFor <= lockAtMostFor}). All are tuning values, so each has a
     * default and a missing value is not an error.
     */
    public record Tracker(Duration recomputeInterval, Duration lockAtMostFor, Duration lockAtLeastFor) {

        public Tracker {
            recomputeInterval = recomputeInterval == null ? Duration.ofHours(1) : recomputeInterval;
            lockAtMostFor = lockAtMostFor == null ? Duration.ofMinutes(10) : lockAtMostFor;
            lockAtLeastFor = lockAtLeastFor == null ? Duration.ofSeconds(30) : lockAtLeastFor;
            positive(recomputeInterval, "tracker.recompute-interval");
            positive(lockAtMostFor, "tracker.lock-at-most-for");
            positive(lockAtLeastFor, "tracker.lock-at-least-for");
            if (lockAtLeastFor.compareTo(lockAtMostFor) > 0) {
                throw new IllegalArgumentException(
                        "tracker.lock-at-least-for must not exceed tracker.lock-at-most-for");
            }
        }

        static Tracker defaults() {
            return new Tracker(null, null, null);
        }
    }

    /**
     * Adaptive polling. {@code sources} are the sources polled adaptively (empty means adaptive polling is off); a
     * source cannot also have a fixed cron, and the season and zone come from {@code schedule}. {@code tickInterval}
     * is the fixed delay of the tick and the ShedLock durations bound its per-source lock. {@code defaults} are the
     * policy values every source uses unless an admin stored an override. {@code bcnesaCompetitionNames} maps an
     * {@code rtb-*} export folder to the stored competition name (default: the table of the import). All are tuning
     * values, so each has a default and a missing value is not an error.
     */
    public record Polling(
            Set<PipelineSource> sources,
            Duration tickInterval,
            Duration lockAtMostFor,
            Duration lockAtLeastFor,
            Defaults defaults,
            Map<String, String> bcnesaCompetitionNames) {

        public Polling {
            sources = sources == null || sources.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(sources));
            tickInterval = tickInterval == null ? Duration.ofMinutes(5) : tickInterval;
            lockAtMostFor = lockAtMostFor == null ? Duration.ofMinutes(10) : lockAtMostFor;
            lockAtLeastFor = lockAtLeastFor == null ? Duration.ofSeconds(30) : lockAtLeastFor;
            defaults = defaults == null ? Defaults.none() : defaults;
            bcnesaCompetitionNames = bcnesaCompetitionNames == null || bcnesaCompetitionNames.isEmpty()
                    ? BcnesaCompetitionNames.defaultEntries() : Map.copyOf(bcnesaCompetitionNames);
            positive(tickInterval, "polling.tick-interval");
            positive(lockAtMostFor, "polling.lock-at-most-for");
            positive(lockAtLeastFor, "polling.lock-at-least-for");
            if (lockAtLeastFor.compareTo(lockAtMostFor) > 0) {
                throw new IllegalArgumentException(
                        "polling.lock-at-least-for must not exceed polling.lock-at-most-for");
            }
            defaults.toSettings();
            try {
                new BcnesaCompetitionNames(bcnesaCompetitionNames);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("polling.bcnesa-competition-names is invalid: " + e.getMessage(),
                        e);
            }
        }

        /** The policy values; a null value takes the documented default. */
        public record Defaults(
                Duration matchDay,
                Duration matchDayStartOffset,
                Duration dayAfter,
                Duration daysTwoToSeven,
                Duration open,
                Duration overdue,
                Integer overdueStopAfterDays,
                Duration fullRefresh,
                Integer noChangeThreshold) {

            static Defaults none() {
                return new Defaults(null, null, null, null, null, null, null, null, null);
            }

            /** Validates through {@link PollingSettings}; the failure names the offending setting. */
            public PollingSettings toSettings() {
                PollingSettings base = PollingSettings.defaults();
                try {
                    return new PollingSettings(
                            matchDay == null ? base.matchDay() : matchDay,
                            matchDayStartOffset == null ? base.matchDayStartOffset() : matchDayStartOffset,
                            dayAfter == null ? base.dayAfter() : dayAfter,
                            daysTwoToSeven == null ? base.daysTwoToSeven() : daysTwoToSeven,
                            open == null ? base.open() : open,
                            overdue == null ? base.overdue() : overdue,
                            overdueStopAfterDays == null ? base.overdueStopAfterDays() : overdueStopAfterDays,
                            fullRefresh == null ? base.fullRefresh() : fullRefresh,
                            noChangeThreshold == null ? base.noChangeThreshold() : noChangeThreshold);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("polling.defaults is invalid: " + e.getMessage(), e);
                }
            }
        }

        static Polling none() {
            return new Polling(null, null, null, null, null, null);
        }

        public PollingSettings defaultSettings() {
            return defaults.toSettings();
        }
    }

    /**
     * Operator notifications by SMTP e-mail. They are enabled exactly when {@code mail.host} is not blank; when
     * disabled every other value is ignored. When enabled, {@code mail.from} and at least one {@code mail.to} are
     * required addresses, the port is 1-65535, {@code username} and {@code password} are both set or both blank and
     * every duration is positive. {@code evaluateInterval} is the fixed delay of the alert evaluation;
     * {@code unreportedAfter}, {@code noSuccessWindow} and {@code closedLookback} are the alert thresholds.
     */
    public record Notifications(
            Mail mail,
            Duration evaluateInterval,
            Duration unreportedAfter,
            Duration noSuccessWindow,
            Duration closedLookback) {

        public Notifications {
            mail = mail == null ? Mail.blank() : mail;
            evaluateInterval = evaluateInterval == null ? Duration.ofMinutes(15) : evaluateInterval;
            unreportedAfter = unreportedAfter == null ? Duration.ofHours(48) : unreportedAfter;
            noSuccessWindow = noSuccessWindow == null ? Duration.ofHours(24) : noSuccessWindow;
            closedLookback = closedLookback == null ? Duration.ofDays(1) : closedLookback;
            if (mail.enabled()) {
                mail.validate();
                positive(evaluateInterval, "notifications.evaluate-interval");
                positive(unreportedAfter, "notifications.unreported-after");
                positive(noSuccessWindow, "notifications.no-success-window");
                positive(closedLookback, "notifications.closed-lookback");
            }
        }

        static Notifications disabled() {
            return new Notifications(null, null, null, null, null);
        }

        public boolean enabled() {
            return mail.enabled();
        }

        public AlertSettings alertSettings() {
            return new AlertSettings(unreportedAfter, noSuccessWindow, closedLookback);
        }
    }

    /**
     * SMTP settings. {@code toString} masks the password, because records print every component. Blank means not
     * set; {@code port} defaults to 587, {@code starttls} to true and {@code subjectPrefix} to
     * {@code [tt-pipeline]}.
     */
    public record Mail(
            String host,
            Integer port,
            String username,
            String password,
            Boolean starttls,
            String from,
            List<String> to,
            String subjectPrefix) {

        public Mail {
            host = blankToNull(host);
            port = port == null ? Integer.valueOf(587) : port;
            username = blankToNull(username);
            password = blankToNull(password);
            starttls = starttls == null ? Boolean.TRUE : starttls;
            from = blankToNull(from);
            to = to == null ? List.of() : to.stream().map(String::trim).filter(value -> !value.isEmpty()).toList();
            subjectPrefix = subjectPrefix == null ? "[tt-pipeline]" : subjectPrefix.trim();
        }

        static Mail blank() {
            return new Mail(null, null, null, null, null, null, null, null);
        }

        public boolean enabled() {
            return host != null;
        }

        void validate() {
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("notifications.mail.port must be between 1 and 65535: " + port);
            }
            if ((username == null) != (password == null)) {
                throw new IllegalArgumentException(
                        "notifications.mail.username and notifications.mail.password must be set together");
            }
            if (from == null) {
                throw new IllegalArgumentException("notifications.mail.from is required when the mail host is set");
            }
            requireAddress(from, "notifications.mail.from");
            if (to.isEmpty()) {
                throw new IllegalArgumentException("notifications.mail.to needs at least one address");
            }
            to.forEach(address -> requireAddress(address, "notifications.mail.to"));
        }

        private static void requireAddress(String address, String name) {
            try {
                new InternetAddress(address, true).validate();
            } catch (AddressException e) {
                throw new IllegalArgumentException(name + " is not a valid e-mail address: " + address);
            }
        }

        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }

        @Override
        public String toString() {
            return "Mail[host=" + host + ", port=" + port + ", username=" + username + ", password="
                    + (password == null ? "null" : "****") + ", starttls=" + starttls + ", from=" + from + ", to="
                    + to + ", subjectPrefix=" + subjectPrefix + "]";
        }
    }

    /** The core settings: retry and timeouts from {@code execution}, poll intervals from the two services. */
    public ExecutionSettings executionSettings() {
        return new ExecutionSettings(
                retryPolicy(execution.maxRetries(), execution.initialBackoff(), execution.backoffMultiplier(),
                        execution.maxBackoff()),
                new StepTimeouts(execution.timeouts().ingest(), execution.timeouts().fetchPackage(),
                        execution.timeouts().importJob()),
                new PollIntervals(ingest.pollInterval(), platform.pollInterval()));
    }

    private static RetryPolicy retryPolicy(int maxRetries, Duration initial, double multiplier, Duration max) {
        return new RetryPolicy(maxRetries, initial, multiplier, max);
    }

    private static void required(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static void positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be a positive duration");
        }
    }
}
