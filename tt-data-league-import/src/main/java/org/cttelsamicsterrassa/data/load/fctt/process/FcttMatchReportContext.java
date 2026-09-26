package org.cttelsamicsterrassa.data.load.fctt.process;

import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportContext;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportRunContext;

import java.nio.file.Path;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything an FCTT processor needs about one match report.
 *
 * <p>The directory supplies the season, gender, competition, and optional group. The report payload
 * supplies the round, phase, and teams; filename components are opaque and carry no business
 * meaning.</p>
 *
 * @param season            season folder, in {@code YYYY-YYYY} form
 * @param gender            {@code male} or {@code female}, from the folder
 * @param leagueCompetition competition folder
 * @param group             group folder, or {@code null} when the competition has no group folder
 * @param round             match day from the payload's {@code jornada}
 * @param matchReportFile   report file
 * @param acta              parsed report payload
 */
public record FcttMatchReportContext(
        String season,
        String gender,
        String leagueCompetition,
        String group,
        int round,
        Path matchReportFile,
        Acta acta,
        ImportRunContext runContext) {

    public FcttMatchReportContext(String season, String gender, String leagueCompetition, String group, int round,
                                  Path matchReportFile, Acta acta) {
        this(season, gender, leagueCompetition, group, round, matchReportFile, acta,
                new ImportRunContext(org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource.FCTT, season));
    }

    private static final Pattern GROUP_NUMBER_PATTERN = Pattern.compile("G?(\\d+)");

    public FcttMatchReportContext {
        Objects.requireNonNull(season, "season");
        Objects.requireNonNull(gender, "gender");
        Objects.requireNonNull(leagueCompetition, "leagueCompetition");
        Objects.requireNonNull(matchReportFile, "matchReportFile");
        Objects.requireNonNull(acta, "acta");
        Objects.requireNonNull(runContext, "runContext");
    }

    /**
     * The season folder as a domain {@link Season}.
     */
    public Season toSeason() {
        return Season.fromFormatted(season);
    }

    /**
     * {@code masculino} or {@code femenino}, the same vocabulary RFETM uses, mapped from the folder's
     * {@code male}/{@code female} value.
     */
    public String sex() {
        return "male".equals(gender) ? "masculino" : "femenino";
    }

    /**
     * Competition identity from the authoritative directory context, with gender folded in following
     * the RFETM convention (for example {@code tercera-nacional-masculino}).
     */
    public String competition() {
        return MatchReportContext.competitionOf(leagueCompetition, sex());
    }

    /**
     * The match phase from the payload's {@code fase}, or {@code null} when absent.
     */
    public String phase() {
        String phase = acta.phase();
        return phase == null || phase.isBlank() ? null : phase.trim();
    }

    /**
     * Whether the competition folder has a group subfolder, as opposed to reports sitting directly in
     * the competition folder.
     */
    public boolean hasGroupFolder() {
        return group != null;
    }

    /**
     * Parses the group folder as either {@code G<number>} or a bare number. Empty when there is no
     * group folder at all.
     *
     * <p>Other folder names are not coerced to a number. The navigator logs and skips those reports
     * before they reach persistence processors, because {@code MATCH} requires an integer group
     * number in its natural key when a group folder is present.</p>
     */
    public OptionalInt groupNumber() {
        if (group == null) {
            return OptionalInt.empty();
        }
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
