package org.cttelsamicsterrassa.data.pipeline.core.polling.scope;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayKey;

/**
 * Per-source join between ingest status rows (folder vocabulary) and tracker match days (platform identity). It
 * mirrors the import's path-to-identity rules; a change to those rules must update this class in the same change.
 */
public sealed interface SourceVocabulary {

    Pattern FLEXIBLE_GROUP = Pattern.compile("G?(\\d+)");
    Pattern PREFIXED_GROUP = Pattern.compile("G(\\d+)");

    /** The platform group key of a row, or empty when the row cannot be mapped. */
    Optional<PlatformGroupKey> platformKey(IngestStatusRow row);

    /** The row as a scope identity restricted to the fields the source's scopes support. */
    PollUnit unit(IngestStatusRow row);

    /** Whether the key's phase takes part in the join (only BCNESA carries the phase in the platform key). */
    boolean comparesPhase();

    /** Whether the row is the status row of the match day: same group key and same round. */
    default boolean matches(IngestStatusRow row, MatchDayKey day) {
        return row.matchDay() == day.round() && matchesGroup(row, day);
    }

    /** Whether the row belongs to the match day's group, whatever its round. */
    default boolean matchesGroup(IngestStatusRow row, MatchDayKey day) {
        return platformKey(row)
                .filter(key -> key.competition().equals(day.competition())
                        && Objects.equals(key.groupNumber(), day.groupNumber())
                        && (!comparesPhase() || Objects.equals(key.phase(), day.phase())))
                .isPresent();
    }

    static SourceVocabulary of(PipelineSource source, BcnesaCompetitionNames names) {
        return switch (source) {
            case RFETM -> new Rfetm();
            case FCTT -> new Fctt();
            case BCNESA -> new Bcnesa(Objects.requireNonNull(names, "names is required"));
        };
    }

    /** {@code <category>-<sex>} competition, group from the {@code grupo_N} page; scopes carry only the category. */
    final class Rfetm implements SourceVocabulary {

        @Override
        public Optional<PlatformGroupKey> platformKey(IngestStatusRow row) {
            if (row.category() == null || row.gender() == null) {
                return Optional.empty();
            }
            return number(FLEXIBLE_GROUP, row.group())
                    .map(group -> new PlatformGroupKey(row.category() + "-" + row.gender(), group, null));
        }

        @Override
        public PollUnit unit(IngestStatusRow row) {
            return new PollUnit(row.category(), null, null, null, null);
        }

        @Override
        public boolean comparesPhase() {
            return false;
        }
    }

    /**
     * {@code <category>-<masculino|femenino>} from the {@code male}/{@code female} gender; phase not compared. Mirrors
     * {@code FcttActasDirectoryNavigator}: category folders with a legacy competition name are aliased
     * ({@code tdm} to {@code tercera-nacional}) and the group folder is matched case-insensitively ({@code g1}).
     */
    final class Fctt implements SourceVocabulary {

        private static final Pattern GROUP = Pattern.compile("(?i)G?(\\d+)");
        private static final Map<String, String> CATEGORY_ALIASES = Map.of("tdm", "tercera-nacional");

        @Override
        public Optional<PlatformGroupKey> platformKey(IngestStatusRow row) {
            if (row.category() == null || row.gender() == null) {
                return Optional.empty();
            }
            String sex = switch (row.gender()) {
                case "male" -> "masculino";
                case "female" -> "femenino";
                default -> null;
            };
            if (sex == null) {
                return Optional.empty();
            }
            String competition = CATEGORY_ALIASES.getOrDefault(row.category(), row.category());
            return number(GROUP, row.group())
                    .map(group -> new PlatformGroupKey(competition + "-" + sex, group, null));
        }

        @Override
        public PollUnit unit(IngestStatusRow row) {
            return new PollUnit(row.category(), row.group(), row.phase(), row.territory(), row.gender());
        }

        @Override
        public boolean comparesPhase() {
            return false;
        }
    }

    /**
     * Competition folder (or the legacy name of an {@code rtb-*} folder), {@code G<n>} group, phase folder. Status
     * rows carry the downloaded category folder ({@code RTT PREFERENT}); the parser exports it as the kebab-case
     * folder the import reads ({@code rtt-preferent}), so the category is converted the same way first.
     */
    final class Bcnesa implements SourceVocabulary {

        private static final String OTHER_GROUP = "Other";
        private static final Pattern NON_SPACING_MARKS = Pattern.compile("\\p{Mn}+");
        private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

        private final BcnesaCompetitionNames names;

        Bcnesa(BcnesaCompetitionNames names) {
            this.names = names;
        }

        @Override
        public Optional<PlatformGroupKey> platformKey(IngestStatusRow row) {
            if (row.category() == null || row.group() == null) {
                return Optional.empty();
            }
            Optional<String> competition = names.storedName(exportFolder(row.category()));
            if (competition.isEmpty()) {
                return Optional.empty();
            }
            if (OTHER_GROUP.equalsIgnoreCase(row.group())) {
                return Optional.of(new PlatformGroupKey(competition.get(), null, row.phase()));
            }
            return number(PREFIXED_GROUP, row.group())
                    .map(group -> new PlatformGroupKey(competition.get(), group, row.phase()));
        }

        @Override
        public PollUnit unit(IngestStatusRow row) {
            return new PollUnit(row.category(), row.group(), row.phase(), row.territory(), null);
        }

        @Override
        public boolean comparesPhase() {
            return true;
        }

        /** Mirror of the BCNESA parser's {@code kebab_case}: {@code RTB VETERANS 1a} to {@code rtb-veterans-1a}. */
        static String exportFolder(String category) {
            String decomposed = Normalizer.normalize(category.replace("ª", "a"), Normalizer.Form.NFD);
            String plain = NON_SPACING_MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
            String kebab = NON_ALPHANUMERIC.matcher(plain).replaceAll("-").replaceAll("^-+|-+$", "");
            return kebab.isEmpty() ? "unknown" : kebab;
        }
    }

    private static Optional<Integer> number(Pattern pattern, String value) {
        if (value == null) {
            return Optional.empty();
        }
        Matcher matcher = pattern.matcher(value);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int number = Integer.parseInt(matcher.group(1));
        return number < 1 ? Optional.empty() : Optional.of(number);
    }
}
