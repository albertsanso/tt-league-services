package org.cttelsamicsterrassa.data.api.rest.stats;

import org.cttelsamicsterrassa.data.core.application.stats.find.dto.CommunityStatisticsReadModel;

public record CommunityStatsDto(CountDto players, CountDto clubs, CountDto matches, SeasonDto season) {

    static CommunityStatsDto from(CommunityStatisticsReadModel value) {
        return new CommunityStatsDto(
                CountDto.from(value.players()),
                CountDto.from(value.clubs()),
                CountDto.from(value.matches()),
                SeasonDto.from(value.season()));
    }

    public record CountDto(long total) {
        static CountDto from(CommunityStatisticsReadModel.CountSummary value) {
            return new CountDto(value.total());
        }
    }

    public record SeasonDto(String name, String status) {
        static SeasonDto from(CommunityStatisticsReadModel.CurrentSeasonSummary value) {
            return new SeasonDto(value.name(), value.status().name());
        }
    }
}
