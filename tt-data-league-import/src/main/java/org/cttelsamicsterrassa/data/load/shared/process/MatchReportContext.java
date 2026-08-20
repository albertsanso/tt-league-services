package org.cttelsamicsterrassa.data.load.shared.process;

import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.parse.Acta;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmClubKey;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Everything a {@link MatchReportProcessor} needs about one match report.
 *
 * <p>Competition identity comes from the directory path
 * ({@code [season]/[league-competition]/[day]/[sex]/acta.json}), the two sides come from the payload
 * via {@link RfetmClubKey}, and {@link #acta()} is that payload in full. The path is preferred for
 * competition identity because it is the more reliable of the two there: {@code temporada} is missing
 * from several hundred reports and {@code competicion} is generic. The report file name is never
 * parsed - it is opaque, and only the payload identifies the teams.</p>
 *
 * @param season            season folder, in {@code YYYY-YYYY} form (for example {@code 2023-2024})
 * @param leagueCompetition league or competition folder (for example {@code super-divisio})
 * @param day               match day folder, a positive integer as text
 * @param sex               {@code masculino} or {@code femenino}
 * @param homeTeam          how the home side is matched to a stored club: its federation id when the
 *                          payload carries one, its scoped name otherwise
 * @param awayTeam          the same for the away side
 * @param matchReportFile   the report file itself
 * @param acta              the parsed report payload
 */
public record MatchReportContext(
        String season,
        String leagueCompetition,
        String day,
        String sex,
        RfetmClubKey homeTeam,
        RfetmClubKey awayTeam,
        Path matchReportFile,
        Acta acta) {

    public MatchReportContext {
        Objects.requireNonNull(season, "season");
        Objects.requireNonNull(leagueCompetition, "leagueCompetition");
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(sex, "sex");
        Objects.requireNonNull(homeTeam, "homeTeam");
        Objects.requireNonNull(awayTeam, "awayTeam");
        Objects.requireNonNull(matchReportFile, "matchReportFile");
    }

    /**
     * The season folder as a domain {@link Season}. This is the single place where the folder form
     * {@code 2023-2024} is turned into a season value, so the rest of the import never has to deal
     * with the {@code 2023/2024} form used inside the payload.
     */
    public Season toSeason() {
        return Season.fromFormatted(season);
    }

    /**
     * The match day as a round number.
     */
    public int round() {
        return Integer.parseInt(day);
    }

    /**
     * Competition identity, composed from the path rather than from the payload's generic
     * {@code competicion} field. Gender has no column of its own in the data model, so it is folded
     * in here (for example {@code super-divisio-masculino}).
     */
    public String competition() {
        return competitionOf(leagueCompetition, sex);
    }

    /**
     * Competition identity from the two path parts it is composed of, for callers that must know it
     * before a context exists - the navigator needs it to scope a club key, and that scope has to be
     * the very same string this context later reports.
     */
    public static String competitionOf(String leagueCompetition, String sex) {
        return "%s-%s".formatted(leagueCompetition, sex);
    }
}
