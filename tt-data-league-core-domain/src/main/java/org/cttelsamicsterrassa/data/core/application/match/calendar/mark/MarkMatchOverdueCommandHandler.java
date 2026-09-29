package org.cttelsamicsterrassa.data.core.application.match.calendar.mark;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchOverdueMark;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchOverdueMarkRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Records a manual overdue mark on a SCHEDULED match (FEAT-00092). Idempotent: re-marking returns
 * the original author and time. A PLAYED match is rejected rather than marked.
 */
@Named
public class MarkMatchOverdueCommandHandler extends DomainCommandHandler<MarkMatchOverdueCommand> {

    private final MatchRepository matches;
    private final MatchOverdueMarkRepository marks;
    private final Clock clock;

    @Inject
    public MarkMatchOverdueCommandHandler(MatchRepository matches, MatchOverdueMarkRepository marks) {
        this(matches, marks, Clock.systemDefaultZone());
    }

    public MarkMatchOverdueCommandHandler(MatchRepository matches, MatchOverdueMarkRepository marks, Clock clock) {
        this.matches = matches;
        this.marks = marks;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public DomainCommandResponse handle(MarkMatchOverdueCommand command) {
        Optional<Match> match = matches.findMatchById(command.getMatchId());
        if (match.isEmpty()) {
            return DomainCommandResponse.failResponse("Match not found: " + command.getMatchId());
        }
        if (match.get().getStatus() == MatchStatus.PLAYED) {
            return DomainCommandResponse.failResponse("Only scheduled matches can be marked overdue");
        }

        Optional<MatchOverdueMark> existing = marks.findByMatchId(command.getMatchId());
        if (existing.isPresent()) {
            return DomainCommandResponse.successResponse(existing.get());
        }

        MatchOverdueMark mark = new MatchOverdueMark(command.getMatchId(),
                ZonedDateTime.now(clock.withZone(Match.COMPETITION_ZONE)), command.getMarkedBy());
        marks.save(mark);
        return DomainCommandResponse.successResponse(mark);
    }
}