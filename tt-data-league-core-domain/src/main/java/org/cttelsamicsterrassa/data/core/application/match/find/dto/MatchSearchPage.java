package org.cttelsamicsterrassa.data.core.application.match.find.dto;

import java.util.List;

public record MatchSearchPage(
        List<MatchSearchReadModel> matches,
        long total,
        int page,
        int pageSize,
        boolean hasNext) {
}
