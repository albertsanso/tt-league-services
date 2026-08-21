package org.cttelsamicsterrassa.data.load.bcnesa.process;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalises BCNESA club names for lookup, shared by {@link BcnesaTeamImportProcessor} and
 * {@link BcnesaMatchImportProcessor} so both resolve the same row.
 *
 * <p>BCNESA carries no team id, so clubs resolve by name, and the export spells the same team's name
 * several ways around its A/B/C team-letter suffix: {@code CLUB ARIEL "A"}, {@code CLUB ARIEL ''A''}
 * and {@code CLUB ARIEL A} are one team. This strips the quoting around a trailing single-letter
 * suffix so those collapse to one form. It deliberately leaves everything else alone: apostrophes
 * that are part of a real word ({@code L'HOSPITALET}, {@code CA L'ELVIRA}) never sit at the very end
 * of the name paired with a lone letter, so they are never touched, and distinct team letters
 * ({@code CLUB ARIEL A} vs {@code CLUB ARIEL B}) are never merged - only the quoting around one
 * letter is.</p>
 */
final class BcnesaTeamNames {

    private static final Pattern TEAM_LETTER_SUFFIX = Pattern.compile("[\"']{1,2}\\s*([A-Za-z])\\s*[\"']{1,2}$");

    private BcnesaTeamNames() {
    }

    static String normalize(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.strip().replaceAll("\\s+", " ");
        Matcher matcher = TEAM_LETTER_SUFFIX.matcher(normalized);
        if (matcher.find()) {
            normalized = normalized.substring(0, matcher.start()).stripTrailing() + " " + matcher.group(1).toUpperCase();
        }
        return normalized;
    }
}
