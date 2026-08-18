package org.cttelsamicsterrassa.data.load.shared.club.consolidate;

import java.util.List;

public record ClubNameParts(
        List<String> foldedTokens,
        List<String> identityTokens,
        List<ClubNameRule> appliedRules
) {
    public ClubNameParts {
        foldedTokens = List.copyOf(foldedTokens);
        identityTokens = List.copyOf(identityTokens);
        appliedRules = List.copyOf(appliedRules);
    }

    public String identityKey() {
        return String.join(" ", identityTokens);
    }
}
