package org.cttelsamicsterrassa.data.load.bcnesa;

import java.util.regex.Pattern;

/**
 * Identifies the BCNESA Veterans "Other" group that Part B/C of FEAT-00040 handle differently from
 * every other fixture: the {@code <Group>} folder is literally named {@code "Other"} (not
 * {@code G<n>}), and holds multiple {@code <Phase>} subfolders with an open set of literal names -
 * {@code Play Off}, {@code ASCENS}, {@code DESCENS}, {@code Finals}, etc. (see FEATURES.md's
 * description of FEAT-00040). Every fixture under it must have its parsed {@code Match.groupNumber}
 * be {@code null}, regardless of which phase subfolder it sits under.
 *
 * <p>Veterans competitions are recognised by their {@code <Competition>} folder name starting with
 * {@code "Vet "} (e.g. {@code "Vet 1a"}, {@code "Vet 2a"} - confirmed against a real BCNESA export),
 * or, for robustness, containing the substring "veteran" (covers "Veterans"/"Veteranos" spellings
 * used in tests and possibly in other exports). If neither matches, the competition keeps today's
 * behavior (fail closed) rather than silently mis-handling it.</p>
 */
public final class BcnesaVeteransPhases {

    private static final String OTHER_GROUP = "Other";
    private static final Pattern VETERANS_COMPETITION =
            Pattern.compile("^Vet\\s.*|.*veteran.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private BcnesaVeteransPhases() {
    }

    public static boolean isVeteransCompetition(String leagueCompetition) {
        return leagueCompetition != null && VETERANS_COMPETITION.matcher(leagueCompetition).matches();
    }

    /**
     * Whether {@code group} is the literal "Other" group folder of a Veterans competition - the
     * folder that holds every playoff/promotion/relegation/finals phase and whose fixtures carry no
     * numbered group.
     */
    public static boolean isOtherGroup(String leagueCompetition, String group) {
        return isVeteransCompetition(leagueCompetition) && OTHER_GROUP.equalsIgnoreCase(group);
    }
}
