package org.cttelsamicsterrassa.data.core.application.match.calendar.mark;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchOverdueMarkRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Clears a manual overdue mark (FEAT-00092). Idempotent and allowed on a PLAYED match so a leftover
 * mark can be removed; only an unknown match fails.
 */
@Named
public class ClearMatchOverdueMarkCommandHandler extends DomainCommandHandler<ClearMatchOverdueMarkCommand> {

    private final MatchRepository matches;
    private final MatchOverdueMarkRepository marks;

    @Inject
    public ClearMatchOverdueMarkCommandHandler(MatchRepository matches, MatchOverdueMarkRepository marks) {
        this.matches = matches;
        this.marks = marks;
    }

    @Override
    public DomainCommandResponse handle(ClearMatchOverdueMarkCommand command) {
        if (matches.findMatchById(command.getMatchId()).isEmpty()) {
            return DomainCommandResponse.failResponse("Match not found: " + command.getMatchId());
        }
        boolean removed = marks.deleteByMatchId(command.getMatchId());
        return DomainCommandResponse.successResponse(removed);
    }
}