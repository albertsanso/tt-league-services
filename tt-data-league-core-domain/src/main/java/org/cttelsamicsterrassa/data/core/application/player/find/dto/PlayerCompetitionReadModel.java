package org.cttelsamicsterrassa.data.core.application.player.find.dto;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

public record PlayerCompetitionReadModel(String name, ImportSource source, Season season, int matchCount) {
}
