package org.cttelsamicsterrassa.data.load.shared.preview;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaClassification;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The preview of one fixture (FEAT-00088): its scope within the season, the incoming acta
 * classification and the {@link PreviewChange} the shared lifecycle planner projects, plus the
 * context an operator or the projection needs (the stored round for an upgrade, whether the teams
 * are pending registration, a human-readable reason and the file location). Purely informational.
 *
 * <p>{@code existingRound} is the stored match's round and is only meaningful for an
 * {@link PreviewChange#UPGRADE}, so the projected progress moves the right row. {@code
 * teamsPendingRegistration} is {@code true} when a team is not registered for the season yet, in
 * which case {@code change} is the creation the real run would perform after its team processor
 * registers the teams.</p>
 */
public record FixturePreview(
        ImportSource source,
        String competition,
        Integer groupNumber,
        String phase,
        int round,
        String sourceFixtureId,
        String homeTeamName,
        String awayTeamName,
        ActaClassification classification,
        PreviewChange change,
        Integer existingRound,
        boolean teamsPendingRegistration,
        String reason,
        Path location) {

    public FixturePreview {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(change, "change");
    }
}
