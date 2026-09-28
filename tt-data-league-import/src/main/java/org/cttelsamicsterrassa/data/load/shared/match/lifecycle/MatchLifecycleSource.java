package org.cttelsamicsterrassa.data.load.shared.match.lifecycle;

import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;

import java.util.UUID;

/**
 * The per-source callbacks {@link MatchLifecycleWriter} needs to store one fixture
 * (FEAT-00081). Each match processor implements this for its own acta shape; only the
 * create/upgrade/reschedule/skip decision is shared.
 */
public interface MatchLifecycleSource {

    /**
     * The SCHEDULED header of this fixture: competition, season, group, round, phase, date/time,
     * city, venue, teams and head referee name. It must never read the acta's
     * {@code resultado_final}, set a winner, or set game/set counts - the domain builder rejects a
     * SCHEDULED match that does.
     *
     * @param id the id to build with: a fresh one when creating, the stored one when the writer
     *           only needs the incoming schedule for the reschedule rule
     */
    Match buildScheduledMatch(UUID id);

    /**
     * The full PLAYED content of this fixture: header, lineups, games, set scores and doubles
     * pairs, built but not saved. The header must carry the status {@code PLAYED}.
     *
     * @param id       a fresh id when creating, the stored match's id when upgrading
     * @param existing {@code true} when the id belongs to an already stored match, so the header
     *                 is built with {@code createExisting()} instead of {@code createNew()}
     */
    MatchContent buildPlayedContent(UUID id, boolean existing);
}
