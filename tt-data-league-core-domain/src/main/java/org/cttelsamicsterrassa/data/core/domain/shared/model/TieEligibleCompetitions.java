package org.cttelsamicsterrassa.data.core.domain.shared.model;

import java.util.Set;

/**
 * Competitions where a tied match is real and displayed as a draw (FEAT-00066). Every other
 * competition never shows draws: a tied match there is excluded entirely from stats and match
 * records rather than merely relabeled.
 *
 * <p>Matched against {@link org.cttelsamicsterrassa.data.core.domain.match.model.Match#getCompetition()}
 * verbatim. Both RFETM and BCNESA import pipelines already compose competition identity in this
 * lowercase, hyphenated form (see {@code MatchReportContext#competition()} and
 * {@code BcnesaMatchReportContext#competition()}), so no further normalization is applied here
 * beyond trimming.</p>
 */
public final class TieEligibleCompetitions {

    private static final Set<String> COMPETITIONS = Set.of(
            "super-divisio-femenino",
            "super-divisio-masculino",
            "fasc-super-divisio-femenino",
            "fasc-super-divisio-masculino");

    private TieEligibleCompetitions() {
    }

    public static boolean isTieEligible(String competition) {
        return competition != null && COMPETITIONS.contains(competition.strip());
    }
}
