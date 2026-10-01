package org.cttelsamicsterrassa.data.load.bcnesa.traverse;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The single home of the BCNESA match-report file-name rules, shared by
 * {@link BcnesaActasDirectoryNavigator} and {@link BcnesaClubIndex} so they cannot drift.
 *
 * <p>Three name generations exist:</p>
 * <ul>
 *   <li>legacy {@code acta_<jornada>_page_<n>.json} (16,310 files, 2020-2021 to 2025-2026) and
 *       {@code acta_<n>.json} (77 files);</li>
 *   <li>{@code acta_<homeId>-<awayId>_<jornada>.json} (the 2,882 files of the earlier, unpublished
 *       2026-2027 export);</li>
 *   <li>current {@code jornada_<NN>_local_team_<localId>_away_team_<awayId>.json} (all 351 files of
 *       the 2026-2027 export, {@code NN} zero-padded). The team ids are never parsed: teams come from
 *       the payload's {@code equipos}.</li>
 * </ul>
 * <p>Any {@code acta*.json} keeps being accepted, as before.</p>
 */
public final class BcnesaReportFileNames {

    private static final Pattern CURRENT =
            Pattern.compile("jornada_(\\d+)_local_team_\\d+_away_team_\\d+\\.json", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGACY_PAGE =
            Pattern.compile("acta_(\\d+)_page_.*\\.json", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGACY_PAIR =
            Pattern.compile("acta_\\d+-\\d+_(\\d+)\\.json", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGACY_ANY = Pattern.compile("acta.*\\.json");

    private BcnesaReportFileNames() {
    }

    /** Whether {@code fileName} is a supported match-report name. */
    public static boolean isMatchReport(String fileName) {
        return CURRENT.matcher(fileName).matches() || LEGACY_ANY.matcher(fileName).matches();
    }

    /**
     * The match day encoded in {@code fileName}, or {@code null} when the name carries none.
     */
    public static Integer roundFromFileName(String fileName) {
        for (Pattern pattern : new Pattern[]{LEGACY_PAGE, LEGACY_PAIR, CURRENT}) {
            Matcher matcher = pattern.matcher(fileName);
            if (matcher.matches()) {
                return Integer.valueOf(matcher.group(1));
            }
        }
        return null;
    }
}
