package org.cttelsamicsterrassa.data.core.domain.match.model;

import java.time.ZonedDateTime;

/**
 * The schedule fields of a fixture (FEAT-00080): date/time, place and referee. All components are
 * nullable because the model already permits them; {@code MatchRepository.updateSchedule} replaces
 * the five stored values as given, and the caller decides whether anything changed.
 */
public record MatchSchedule(
        ZonedDateTime dateTime,
        String city,
        String venue,
        String refereeName,
        String refereeLicense) {
}
