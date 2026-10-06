package org.cttelsamicsterrassa.data.pipeline.core.alert;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayKey;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import java.time.Instant;
import java.util.List;

/**
 * Titles and details of the alerts. Plain text only: no URL, key or token, and a run or unit failure is described by
 * its {@code RunError.code}, never its message.
 */
final class AlertTexts {

    private AlertTexts() {
    }

    static String dayLabel(MatchDayKey key) {
        StringBuilder label = new StringBuilder(key.competition());
        if (key.groupNumber() != null) {
            label.append(" group ").append(key.groupNumber());
        }
        if (key.phase() != null) {
            label.append(' ').append(key.phase());
        }
        return label.append(" round ").append(key.round()).toString();
    }

    static String closedTitle(MatchDay day) {
        MatchDayKey key = day.key();
        return title(key.source() + " " + key.season() + ": match day closed, " + dayLabel(key));
    }

    static String closedDetail(MatchDay day) {
        MatchDayKey key = day.key();
        return "Match day closed.\n"
                + dayLines(key)
                + "\nClose reason: " + day.closeReason()
                + "\nClosed by: " + day.closedBy()
                + "\nClosed at: " + day.closedAt();
    }

    static String failuresTitle(PipelineSource source) {
        return source + ": two consecutive runs failed";
    }

    static String failuresDetail(PipelineSource source, List<PipelineRun> runs) {
        StringBuilder detail = new StringBuilder("The two newest finished runs of " + source + " failed.\n");
        for (PipelineRun run : runs) {
            detail.append("\nRun ").append(run.id())
                    .append(" (").append(run.trigger()).append(", season ").append(run.season()).append(")")
                    .append("\n  Finished at: ").append(run.finishedAt())
                    .append("\n  Error code: ").append(run.error().code());
        }
        return detail.toString();
    }

    static String unitFailuresTitle(PipelineSource source, RunUnit unit) {
        return title(source + ": unit " + unit.label() + " failed twice in a row");
    }

    static String unitFailuresDetail(PipelineSource source, List<RunUnit> units) {
        StringBuilder detail = new StringBuilder("The two newest finished occurrences of unit "
                + units.get(0).label() + " of " + source + " failed.\n");
        for (RunUnit unit : units) {
            detail.append("\nRun ").append(unit.runId())
                    .append("\n  Finished at: ").append(unit.finishedAt())
                    .append("\n  Error code: ").append(unit.error().code());
        }
        return detail.toString();
    }

    static String unreportedTitle(MatchDay day, MatchTracking match) {
        return title(day.key().source() + " " + day.key().season() + ": match unreported, "
                + teams(match) + " (" + dayLabel(day.key()) + ")");
    }

    static String unreportedDetail(MatchDay day, MatchTracking match) {
        return "A match is still unreported.\n"
                + dayLines(day.key())
                + "\nMatch: " + teams(match)
                + "\nMatch date: " + match.matchDateTime()
                + "\nStatus: " + match.status();
    }

    static String noSuccessTitle(PipelineSource source) {
        return source + ": no successful run during an open match day";
    }

    static String noSuccessDetail(PipelineSource source, Instant reference, Instant newestSuccess, long openDays) {
        return "No run of " + source + " succeeded while match days were open.\n"
                + "\nOpen match days: " + openDays
                + "\nNewest successful run finished at: " + (newestSuccess == null ? "never" : newestSuccess)
                + "\nWaiting since: " + reference;
    }

    private static String dayLines(MatchDayKey key) {
        StringBuilder lines = new StringBuilder()
                .append("Source: ").append(key.source())
                .append("\nSeason: ").append(key.season())
                .append("\nCompetition: ").append(key.competition());
        if (key.groupNumber() != null) {
            lines.append("\nGroup: ").append(key.groupNumber());
        }
        if (key.phase() != null) {
            lines.append("\nPhase: ").append(key.phase());
        }
        return lines.append("\nRound: ").append(key.round()).toString();
    }

    private static String teams(MatchTracking match) {
        String home = match.homeTeamName() == null ? "?" : match.homeTeamName();
        String away = match.awayTeamName() == null ? "?" : match.awayTeamName();
        return home + " - " + away;
    }

    private static String title(String text) {
        return text.length() <= 255 ? text : text.substring(0, 252) + "...";
    }
}
