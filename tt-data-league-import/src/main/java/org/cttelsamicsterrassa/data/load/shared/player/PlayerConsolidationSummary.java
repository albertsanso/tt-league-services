package org.cttelsamicsterrassa.data.load.shared.player;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PlayerConsolidationSummary {
    private final ImportSource source;
    private final int scannedRegistrations;
    private final int exactGroups;
    private final int acceptedFuzzyGroups;
    private final int playersCreated;
    private final int registrationsReassociated;
    private final int alreadyCorrectRegistrations;
    private final List<ConsolidatedPlayer> consolidations;
    private final List<ConsolidationWarning> warnings;
    private final List<ConsolidationWarning> errors;

    private PlayerConsolidationSummary(Builder builder) {
        source = builder.source;
        scannedRegistrations = builder.scannedRegistrations;
        exactGroups = builder.exactGroups;
        acceptedFuzzyGroups = builder.acceptedFuzzyGroups;
        playersCreated = builder.playersCreated;
        registrationsReassociated = builder.registrationsReassociated;
        alreadyCorrectRegistrations = builder.alreadyCorrectRegistrations;
        consolidations = List.copyOf(builder.consolidations);
        warnings = List.copyOf(builder.warnings);
        errors = List.copyOf(builder.errors);
    }

    public static Builder builder(ImportSource source) {
        return new Builder(source);
    }

    public ImportSource source() { return source; }
    public int scannedRegistrations() { return scannedRegistrations; }
    public int exactGroups() { return exactGroups; }
    public int acceptedFuzzyGroups() { return acceptedFuzzyGroups; }
    public int playersCreated() { return playersCreated; }
    public int registrationsReassociated() { return registrationsReassociated; }
    public int alreadyCorrectRegistrations() { return alreadyCorrectRegistrations; }
    public List<ConsolidatedPlayer> consolidations() { return consolidations; }
    public List<ConsolidationWarning> warnings() { return warnings; }
    public List<ConsolidationWarning> errors() { return errors; }

    @Override
    public String toString() {
        return "PlayerConsolidationSummary{source=%s, scanned=%d, exactGroups=%d, fuzzyGroups=%d, created=%d, reassociated=%d, alreadyCorrect=%d, warnings=%d, errors=%d}"
                .formatted(source, scannedRegistrations, exactGroups, acceptedFuzzyGroups, playersCreated,
                        registrationsReassociated, alreadyCorrectRegistrations, warnings.size(), errors.size());
    }

    public static final class Builder {
        private final ImportSource source;
        private int scannedRegistrations;
        private int exactGroups;
        private int acceptedFuzzyGroups;
        private int playersCreated;
        private int registrationsReassociated;
        private int alreadyCorrectRegistrations;
        private final List<ConsolidatedPlayer> consolidations = new ArrayList<>();
        private final List<ConsolidationWarning> warnings = new ArrayList<>();
        private final List<ConsolidationWarning> errors = new ArrayList<>();

        private Builder(ImportSource source) {
            this.source = Objects.requireNonNull(source, "source");
        }

        public Builder scannedRegistrations(int value) { scannedRegistrations = value; return this; }
        public Builder exactGroups(int value) { exactGroups = value; return this; }
        public Builder acceptedFuzzyGroups(int value) { acceptedFuzzyGroups = value; return this; }
        public Builder incrementPlayersCreated() { playersCreated++; return this; }
        public Builder incrementReassociated() { registrationsReassociated++; return this; }
        public Builder incrementAlreadyCorrect() { alreadyCorrectRegistrations++; return this; }
        public Builder consolidation(ConsolidatedPlayer value) { consolidations.add(value); return this; }
        public Builder warning(ConsolidationWarning value) { warnings.add(value); return this; }
        public Builder error(ConsolidationWarning value) { errors.add(value); return this; }
        public PlayerConsolidationSummary build() { return new PlayerConsolidationSummary(this); }
    }
}
