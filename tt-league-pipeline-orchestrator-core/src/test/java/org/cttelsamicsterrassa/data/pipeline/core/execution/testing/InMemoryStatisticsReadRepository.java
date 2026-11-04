package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.CorrectionFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.MatchFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.RunFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.StepFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.UnitFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.port.StatisticsReadRepository;

/** Facts added by the test and filtered like the real repository: sources, half-open ranges and the day prefilter. */
public class InMemoryStatisticsReadRepository implements StatisticsReadRepository {

    private final List<RunFacts> runs = new ArrayList<>();
    private final List<StepFacts> steps = new ArrayList<>();
    private final List<UnitFacts> units = new ArrayList<>();
    private final List<MatchFacts> matches = new ArrayList<>();
    private final List<CorrectionFacts> corrections = new ArrayList<>();

    public InMemoryStatisticsReadRepository add(RunFacts run) {
        runs.add(run);
        return this;
    }

    public InMemoryStatisticsReadRepository add(StepFacts step) {
        steps.add(step);
        return this;
    }

    public InMemoryStatisticsReadRepository add(UnitFacts unit) {
        units.add(unit);
        return this;
    }

    public InMemoryStatisticsReadRepository add(MatchFacts match) {
        matches.add(match);
        return this;
    }

    public InMemoryStatisticsReadRepository add(CorrectionFacts correction) {
        corrections.add(correction);
        return this;
    }

    @Override
    public List<RunFacts> terminalRunsFinishedBetween(Instant from, Instant to, Set<PipelineSource> sources) {
        return runs.stream()
                .filter(run -> run.status().isTerminal() && inSources(run.source(), sources))
                .filter(run -> within(run.finishedAt(), from, to))
                .toList();
    }

    @Override
    public List<StepFacts> stepsFinishedBetween(Instant from, Instant to, Set<PipelineSource> sources) {
        return steps.stream()
                .filter(step -> inSources(step.source(), sources) && within(step.finishedAt(), from, to))
                .toList();
    }

    @Override
    public List<UnitFacts> unitsFinishedBetween(
            Instant from, Instant to, Set<PipelineSource> sources, Optional<String> unitKey) {
        return units.stream()
                .filter(unit -> unit.status().isTerminal() && inSources(unit.source(), sources)
                        && within(unit.finishedAt(), from, to)
                        && unitKey.map(unit.unitKey()::equals).orElse(true))
                .toList();
    }

    @Override
    public List<MatchFacts> matchesBySeason(Set<PipelineSource> sources, String season) {
        return matches.stream()
                .filter(match -> inSources(match.source(), sources)
                        && (season == null || season.equals(match.season())))
                .toList();
    }

    @Override
    public List<MatchFacts> matchesForDay(Instant start, Instant end) {
        return matches.stream()
                .filter(match -> within(match.reportedAt(), start, end)
                        || (!match.ignored()
                                && match.matchDateTime() != null
                                && match.matchDateTime().isBefore(end)
                                && (match.reportedAt() == null || !match.reportedAt().isBefore(end))))
                .toList();
    }

    @Override
    public List<CorrectionFacts> importReportsReceivedBetween(
            Instant from, Instant to, Set<PipelineSource> sources) {
        return corrections.stream()
                .filter(fact -> inSources(fact.source(), sources) && within(fact.receivedAt(), from, to))
                .toList();
    }

    private static boolean inSources(PipelineSource source, Set<PipelineSource> sources) {
        return sources.isEmpty() || sources.contains(source);
    }

    private static boolean within(Instant instant, Instant from, Instant to) {
        return instant != null && !instant.isBefore(from) && instant.isBefore(to);
    }
}
