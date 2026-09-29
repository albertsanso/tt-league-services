package org.cttelsamicsterrassa.data.api.runtime.config;

import org.cttelsamicsterrassa.data.core.domain.match.model.OverdueGracePeriod;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Season-calendar configuration (FEAT-00092). The grace period is environment-driven: a non-numeric
 * value fails binding and a negative value is rejected by {@link OverdueGracePeriod} when the bean
 * is built, so there is no silent fallback to the default.
 */
@ConfigurationProperties(prefix = "tt.league.calendar")
public class SeasonCalendarProperties {

    private int overdueGraceDays = 7;

    public OverdueGracePeriod toGracePeriod() {
        return new OverdueGracePeriod(overdueGraceDays);
    }

    public int getOverdueGraceDays() {
        return overdueGraceDays;
    }

    public void setOverdueGraceDays(int overdueGraceDays) {
        this.overdueGraceDays = overdueGraceDays;
    }
}