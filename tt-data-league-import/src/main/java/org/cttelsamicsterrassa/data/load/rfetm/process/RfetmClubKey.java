package org.cttelsamicsterrassa.data.load.rfetm.process;

import org.cttelsamicsterrassa.data.load.shared.process.MatchReportContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * How one side of an RFETM match report is matched to a stored club.
 *
 * <p>The export identifies a team in one of two ways, and which one it uses is not a property of the
 * match but of the extraction: {@code equipos.*.id} carries the federation's team id in 16,106 of the
 * 20,619 reports, and is simply absent from the other 4,513 - among them the whole of
 * {@code 2025-2026}, all 4,017 files of it. Keying only on the id would leave a full season
 * unimportable, so a report without one falls back to the team name.</p>
 *
 * <h2>Why the fallback is scoped, and scoped exactly this way</h2>
 * <p>A club name is not a global identifier: the A, B and C teams of one club normally share a single
 * name, so keying on the name alone would collapse teams that must stay separate. Measured over the
 * 1,852 (scope, name) pairs the id-carrying reports let us check:</p>
 *
 * <table>
 *   <caption>Name-key granularity against known ids</caption>
 *   <tr><th>scope</th><th>names covering more than one team</th></tr>
 *   <tr><td>{@code season + name}</td><td>249</td></tr>
 *   <tr><td>{@code season + sex + name}</td><td>178</td></tr>
 *   <tr><td>{@code season + competition + sex + name}</td><td><b>0</b></td></tr>
 * </table>
 *
 * <p>Competition-and-sex is what separates a club's teams - they play in different divisions - so
 * scoping the name to {@code (season, competition)} (where competition already folds in sex, see
 * {@link MatchReportContext#competition()}) is exact: across all 290 scopes, name and federation id
 * agree in both directions, no name covering two ids and no id appearing under two names. Adding the
 * group changes nothing, so it is left out.</p>
 *
 * <h2>Namespaces never mix</h2>
 * <p>{@link #value()} is what gets stored and looked up. A derived key is prefixed {@code nm:} and a
 * federation id never is, so the two can never collide and derived rows stay recognisable. Because
 * the scope includes the season, a team that carries an id in one season and not in the next produces
 * two club rows rather than one; that is the same discontinuity the ids themselves already have (one
 * name in this export is spread over six different ids), and deliberately not papered over here -
 * merging clubs across seasons is a separate decision that must not be taken silently by an
 * identity fallback.</p>
 */
public record RfetmClubKey(String value, String rfetmId, String name) {

    /** Marks a key derived from the team name, keeping it out of the federation id namespace. */
    private static final String DERIVED_PREFIX = "nm:";

    /** 8 bytes of digest: short enough for the 20-char id column, wide enough to never collide here. */
    private static final int DERIVED_HASH_BYTES = 8;

    public RfetmClubKey {
        Objects.requireNonNull(value, "value");
    }

    /**
     * A key on the federation's own team id, used whenever the payload carries one.
     */
    public static RfetmClubKey ofFederationId(String rfetmId, String name) {
        Objects.requireNonNull(rfetmId, "rfetmId");
        return new RfetmClubKey(rfetmId, rfetmId, name);
    }

    /**
     * A key derived from the team name, scoped to the season and competition it was read in. Used
     * only when the payload carries no federation id.
     */
    public static RfetmClubKey ofName(String season, String competition, String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Cannot derive a club key from a blank team name");
        }
        String scoped = "%s|%s|%s".formatted(normalize(season), normalize(competition), normalized);
        return new RfetmClubKey(DERIVED_PREFIX + hash(scoped), null, name);
    }

    /**
     * Builds the key for one side of a report: the federation id when there is one, the scoped name
     * otherwise, or {@code null} when the payload offers neither and the side cannot be identified
     * at all.
     */
    public static RfetmClubKey of(String rfetmId, String name, String season, String competition) {
        if (rfetmId != null && !rfetmId.isBlank()) {
            return ofFederationId(rfetmId, name);
        }
        if (name == null || normalize(name).isEmpty()) {
            return null;
        }
        return ofName(season, competition, name);
    }

    /** Whether this key is the federation's own id rather than one derived from the name. */
    public boolean isFederationId() {
        return rfetmId != null;
    }

    /**
     * Case and surrounding whitespace vary between reports; internal spacing does not carry meaning
     * either, so both sides of a comparison are folded the same way before hashing.
     */
    private static String normalize(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static String hash(String scoped) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(scoped.getBytes(StandardCharsets.UTF_8));
            byte[] shortened = new byte[DERIVED_HASH_BYTES];
            System.arraycopy(digest, 0, shortened, 0, DERIVED_HASH_BYTES);
            return HexFormat.of().formatHex(shortened);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required to derive club keys", e);
        }
    }

    @Override
    public String toString() {
        return isFederationId() ? value : "%s (\"%s\")".formatted(value, name);
    }
}
