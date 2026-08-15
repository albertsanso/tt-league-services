package org.cttelsamicsterrassa.data.load.fctt.process;

import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.parse.Acta;

import java.nio.file.Path;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything an FCTT processor needs about one match report.
 *
 * <p>The directory supplies the season, competition, and group. The report payload supplies the
 * round and teams; filename components are opaque and carry no business meaning.</p>
 *
 * @param season            season folder, in {@code YYYY-YYYY} form
 * @param leagueCompetition competition folder
 * @param group             group folder
 * @param round             match day from the payload's {@code jornada}
 * @param matchReportFile   report file
 * @param acta              parsed report payload
 */
public record FcttMatchReportContext(
        String season,
        String leagueCompetition,
        String group,
        int round,
        Path matchReportFile,
        Acta acta) {

    private static final Pattern GROUP_NUMBER_PATTERN = Pattern.compile("G?(\\d+)");

    public FcttMatchReportContext {
        Objects.requireNonNull(season, "season");
        Objects.requireNonNull(leagueCompetition, "leagueCompetition");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(matchReportFile, "matchReportFile");
        Objects.requireNonNull(acta, "acta");
    }

    /**
     * The season folder as a domain {@link Season}.
     */
    public Season toSeason() {
        return Season.fromFormatted(season);
    }

    /**
     * Competition identity from the authoritative directory context.
     */
    public String competition() {
        return leagueCompetition;
    }

    /**
     * Parses the group folder as either {@code G<number>} or a bare number.
     *
     * <p>Other folder names are not coerced to a number. The navigator logs and skips those reports
     * before they reach persistence processors, because {@code MATCH} requires an integer group
     * number in its natural key.</p>
     */
    public OptionalInt groupNumber() {
        Matcher matcher = GROUP_NUMBER_PATTERN.matcher(group);
        if (!matcher.matches()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }
}
