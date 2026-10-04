package org.cttelsamicsterrassa.data.api.runtime.config;

import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Import jobs configuration (FEAT-00100): how long a job sleeps between checks and how long it may wait while
 * another import is active. A malformed duration fails binding and a zero or negative one is rejected by
 * {@link ImportJobSettings} when the bean is built, so there is no silent fallback to the default.
 */
@ConfigurationProperties(prefix = "tt.league.import.jobs")
public class ImportJobProperties {

    private Duration busyRetryInterval = Duration.ofSeconds(10);
    private Duration busyTimeout = Duration.ofHours(2);

    public ImportJobSettings toSettings() {
        return new ImportJobSettings(busyRetryInterval, busyTimeout);
    }

    public Duration getBusyRetryInterval() {
        return busyRetryInterval;
    }

    public void setBusyRetryInterval(Duration busyRetryInterval) {
        this.busyRetryInterval = busyRetryInterval;
    }

    public Duration getBusyTimeout() {
        return busyTimeout;
    }

    public void setBusyTimeout(Duration busyTimeout) {
        this.busyTimeout = busyTimeout;
    }
}
