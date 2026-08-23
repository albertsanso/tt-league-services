package org.cttelsamicsterrassa.data.load.shared.club.consolidate;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ClubConsolidationSummary {

    private final ImportSource source;
    private final int scannedRegistrations;
    private final int exactGroups;
    private final int acceptedFuzzyGroups;
    private final int clubsCreated;
    private final int canonicalLinksCreated;
    private final int registrationsReassociated;
    private final int alreadyCorrectRegistrations;
    private final List<ConsolidatedClub> consolidations;
    private final List<ConsolidationWarning> warnings;
    private final List<ConsolidationWarning> errors;

    private ClubConsolidationSummary(Builder builder) {
        this.source = builder.source;
        this.scannedRegistrations = builder.scannedRegistrations;
        this.exactGroups = builder.exactGroups;
        this.acceptedFuzzyGroups = builder.acceptedFuzzyGroups;
        this.clubsCreated = builder.clubsCreated;
        this.canonicalLinksCreated = builder.canonicalLinksCreated;
        this.registrationsReassociated = builder.registrationsReassociated;
        this.alreadyCorrectRegistrations = builder.alreadyCorrectRegistrations;
        this.consolidations = List.copyOf(builder.consolidations);
        this.warnings = List.copyOf(builder.warnings);
        this.errors = List.copyOf(builder.errors);
    }

    public static ClubConsolidationSummary disabled(ImportSource source, String reason) {
        return builder(source)
                .error(new ConsolidationWarning(source, reason, List.of(), List.of()))
                .build();
    }

    public static Builder builder(ImportSource source) {
        return new Builder(source);
    }

    public ImportSource source() {
        return source;
    }

    public int scannedRegistrations() {
        return scannedRegistrations;
    }

    public int exactGroups() {
        return exactGroups;
    }

    public int acceptedFuzzyGroups() {
        return acceptedFuzzyGroups;
    }

    public int clubsCreated() {
        return clubsCreated;
    }

    public int registrationsReassociated() {
        return registrationsReassociated;
    }

    public int canonicalLinksCreated() {
        return canonicalLinksCreated;
    }

    public int alreadyCorrectRegistrations() {
        return alreadyCorrectRegistrations;
    }

    public List<ConsolidatedClub> consolidations() {
        return consolidations;
    }

    public List<ConsolidationWarning> warnings() {
        return warnings;
    }

    public List<ConsolidationWarning> errors() {
        return errors;
    }

    @Override
    public String toString() {
        return "ClubConsolidationSummary{source=%s, scanned=%d, exactGroups=%d, fuzzyGroups=%d, created=%d, canonicalLinks=%d, reassociated=%d, alreadyCorrect=%d, warnings=%d, errors=%d}"
                .formatted(source, scannedRegistrations, exactGroups, acceptedFuzzyGroups, clubsCreated,
                        canonicalLinksCreated, registrationsReassociated, alreadyCorrectRegistrations,
                        warnings.size(), errors.size());
    }

    public static final class Builder {
        private final ImportSource source;
        private int scannedRegistrations;
        private int exactGroups;
        private int acceptedFuzzyGroups;
        private int clubsCreated;
        private int canonicalLinksCreated;
        private int registrationsReassociated;
        private int alreadyCorrectRegistrations;
        private final List<ConsolidatedClub> consolidations = new ArrayList<>();
        private final List<ConsolidationWarning> warnings = new ArrayList<>();
        private final List<ConsolidationWarning> errors = new ArrayList<>();

        private Builder(ImportSource source) {
            this.source = Objects.requireNonNull(source, "source");
        }

        public Builder scannedRegistrations(int scannedRegistrations) {
            this.scannedRegistrations = scannedRegistrations;
            return this;
        }

        public Builder exactGroups(int exactGroups) {
            this.exactGroups = exactGroups;
            return this;
        }

        public Builder acceptedFuzzyGroups(int acceptedFuzzyGroups) {
            this.acceptedFuzzyGroups = acceptedFuzzyGroups;
            return this;
        }

        public Builder incrementClubsCreated() {
            this.clubsCreated++;
            return this;
        }

        public Builder incrementCanonicalLinksCreated() {
            this.canonicalLinksCreated++;
            return this;
        }

        public Builder incrementReassociated() {
            this.registrationsReassociated++;
            return this;
        }

        public Builder incrementAlreadyCorrect() {
            this.alreadyCorrectRegistrations++;
            return this;
        }

        public Builder consolidation(ConsolidatedClub consolidation) {
            this.consolidations.add(consolidation);
            return this;
        }

        public Builder warning(ConsolidationWarning warning) {
            this.warnings.add(warning);
            return this;
        }

        public Builder error(ConsolidationWarning error) {
            this.errors.add(error);
            return this;
        }

        public ClubConsolidationSummary build() {
            return new ClubConsolidationSummary(this);
        }
    }
}
