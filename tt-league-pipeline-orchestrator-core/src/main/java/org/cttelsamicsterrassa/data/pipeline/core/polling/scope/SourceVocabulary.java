package org.cttelsamicsterrassa.data.pipeline.core.polling.scope;

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
        return row.matchDay() == day.round() && platformKey(row)
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

    /** {@code <category>-<masculino|femenino>} from the {@code male}/{@code female} gender; phase not compared. */
    final class Fctt implements SourceVocabulary {

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
            return number(FLEXIBLE_GROUP, row.group())
                    .map(group -> new PlatformGroupKey(row.category() + "-" + sex, group, null));
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

    /** Competition folder (or the legacy name of an {@code rtb-*} folder), {@code G<n>} group, phase folder. */
    final class Bcnesa implements SourceVocabulary {

        private static final String OTHER_GROUP = "Other";

        private final BcnesaCompetitionNames names;

        Bcnesa(BcnesaCompetitionNames names) {
            this.names = names;
        }

        @Override
        public Optional<PlatformGroupKey> platformKey(IngestStatusRow row) {
            if (row.category() == null || row.group() == null) {
                return Optional.empty();
            }
            Optional<String> competition = names.storedName(row.category());
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
