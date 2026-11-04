package org.cttelsamicsterrassa.data.api.runtime.config;

import org.cttelsamicsterrassa.data.core.application.match.calendar.FindSeasonCalendarQueryHandler;
import org.cttelsamicsterrassa.data.core.application.match.calendar.range.FindCalendarRangeQueryHandler;
import org.cttelsamicsterrassa.data.core.application.match.roundprogress.FindRoundProgressQueryHandler;
import org.cttelsamicsterrassa.data.core.domain.match.model.OverdueGracePeriod;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchOverdueMarkRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the season-calendar query handler (FEAT-00092). The handler is declared here as a
 * {@code @Bean} rather than {@code @Named}, so the import runtime — which scans every package but
 * has no calendar configuration — never needs the grace-period value.
 */
@Configuration
@EnableConfigurationProperties(SeasonCalendarProperties.class)
public class SeasonCalendarConfiguration {

    @Bean
    OverdueGracePeriod overdueGracePeriod(SeasonCalendarProperties properties) {
        return properties.toGracePeriod();
    }

    @Bean
    FindSeasonCalendarQueryHandler findSeasonCalendarQueryHandler(
            MatchRepository matchRepository,
            MatchOverdueMarkRepository markRepository,
            OverdueGracePeriod gracePeriod) {
        return new FindSeasonCalendarQueryHandler(matchRepository, markRepository, gracePeriod,
                Clock.systemDefaultZone());
    }

    @Bean
    FindRoundProgressQueryHandler findRoundProgressQueryHandler(
            MatchRepository matchRepository,
            MatchOverdueMarkRepository markRepository,
            OverdueGracePeriod gracePeriod) {
        return new FindRoundProgressQueryHandler(matchRepository, markRepository, gracePeriod,
                Clock.systemDefaultZone());
    }

    @Bean
    FindCalendarRangeQueryHandler findCalendarRangeQueryHandler(
            MatchRepository matchRepository,
            MatchOverdueMarkRepository markRepository,
            OverdueGracePeriod gracePeriod) {
        return new FindCalendarRangeQueryHandler(matchRepository, markRepository, gracePeriod,
                Clock.systemDefaultZone());
    }
}