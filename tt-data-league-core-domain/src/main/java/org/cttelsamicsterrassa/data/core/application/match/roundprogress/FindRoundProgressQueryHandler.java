package org.cttelsamicsterrassa.data.core.application.match.roundprogress;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.match.roundprogress.dto.JornadaProgressReadModel;
import org.cttelsamicsterrassa.data.core.application.match.roundprogress.dto.RoundProgressGroupReadModel;
import org.cttelsamicsterrassa.data.core.application.match.roundprogress.dto.RoundProgressReadModel;
import org.cttelsamicsterrassa.data.core.domain.match.model.JornadaProgress;
import org.cttelsamicsterrassa.data.core.domain.match.model.JornadaProgressCalculator;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchCalendarEntry;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchOverdueMark;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.model.OverdueGracePeriod;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchOverdueMarkRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds the per-jornada round progress (FEAT-00102) from the whole-season {@code findRoundProgress},
 * one slim {@code findCalendarEntries} read and one manual-mark lookup. The jornada rule and the
 * calendar state are never re-implemented here: this handler only reuses
 * {@link JornadaProgressCalculator}. Filters are applied after computing, so group headers are never
 * affected by them.
 *
 * <p>Declared as a {@code @Bean} in the API runtime (not {@code @Named}) so the import runtime never
 * needs the {@link OverdueGracePeriod} configuration.</p>
 */
public class FindRoundProgressQueryHandler
        extends DomainQueryHandler<FindRoundProgressQuery, RoundProgressReadModel> {

    private final MatchRepository matches;
    private final MatchOverdueMarkRepository marks;
    private final OverdueGracePeriod grace;
    private final Clock clock;

    public FindRoundProgressQueryHandler(MatchRepository matches, MatchOverdueMarkRepository marks,
                                         OverdueGracePeriod grace, Clock clock) {
        this.matches = matches;
        this.marks = marks;
        this.grace = grace;
        this.clock = clock;
    }

    @Override
    public DomainQueryResponse<RoundProgressReadModel> handle(FindRoundProgressQuery query) {
        try {
            return DomainQueryResponse.sucessResponse(build(query));
        } catch (IllegalArgumentException exception) {
            return DomainQueryResponse.failResponse(null);
        }
    }

    private RoundProgressReadModel build(FindRoundProgressQuery query) {
        List<RoundProgress> progress = matches.findRoundProgress(query.getSource(), query.getSeason());
        List<MatchCalendarEntry> entries = matches.findCalendarEntries(query.getSource(), query.getSeason());

        Set<UUID> markedIds = marks.findByMatchIds(entries.stream()
                        .filter(entry -> entry.status() == MatchStatus.SCHEDULED)
                        .map(MatchCalendarEntry::matchId)
                        .toList())
                .stream()
                .map(MatchOverdueMark::matchId)
                .collect(Collectors.toSet());

        LocalDate today = LocalDate.now(clock.withZone(Match.COMPETITION_ZONE));
        List<JornadaProgress> jornadas =
                JornadaProgressCalculator.compute(progress, entries, markedIds, today, grace);

        List<RoundProgressGroupReadModel> groups = new ArrayList<>();
        for (RoundProgress row : progress) {
            if (query.getCompetition() != null && !query.getCompetition().equals(row.competition())) {
                continue;
            }
            List<JornadaProgressReadModel> rounds = jornadas.stream()
                    .filter(j -> Objects.equals(j.competition(), row.competition())
                            && Objects.equals(j.groupNumber(), row.groupNumber())
                            && Objects.equals(j.phase(), row.phase()))
                    .map(j -> toReadModel(j, j.isOpen(today, grace)))
                    .filter(j -> !query.isOnlyOpen() || j.open())
                    .toList();
            if (query.isOnlyOpen() && rounds.isEmpty()) {
                continue;
            }
            groups.add(new RoundProgressGroupReadModel(row.competition(), row.groupNumber(), row.phase(),
                    row.currentRound(), row.lastCompleteRound(), rounds));
        }
        return new RoundProgressReadModel(query.getSource(), query.getSeason(), query.getCompetition(),
                query.isOnlyOpen(), today, grace.days(), groups);
    }

    private static JornadaProgressReadModel toReadModel(JornadaProgress j, boolean open) {
        return new JornadaProgressReadModel(j.round(), j.firstDate(), j.lastDate(), j.scheduledMatches(),
                j.playedMatches(), j.postponedMatches(), j.overdueMatches(), j.awaitingResultMatches(),
                j.undatedMatches(), j.complete(), j.current(), open);
    }
}
