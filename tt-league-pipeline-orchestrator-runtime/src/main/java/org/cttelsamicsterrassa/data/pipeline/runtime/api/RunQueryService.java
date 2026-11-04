package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.ImportReport;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunPage;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunQuery;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ImportReportRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineStepRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunArtifactRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunUnitRepository;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ReplayRules;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.UnitRetryEligibility;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.UnitRetryRules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the runs API; uses the core ports only, in one read-only transaction per call. */
@Service
@Transactional(readOnly = true)
public class RunQueryService {

    private final PipelineRunRepository runs;
    private final RunUnitRepository units;
    private final PipelineStepRepository steps;
    private final RunArtifactRepository artifacts;
    private final ImportReportRepository reports;
    private final RunDtoMapper mapper;

    RunQueryService(PipelineRunRepository runs, RunUnitRepository units, PipelineStepRepository steps,
            RunArtifactRepository artifacts, ImportReportRepository reports, RunDtoMapper mapper) {
        this.runs = runs;
        this.units = units;
        this.steps = steps;
        this.artifacts = artifacts;
        this.reports = reports;
        this.mapper = mapper;
    }

    public PageDto<RunSummaryDto> list(RunQuery query) {
        RunPage page = runs.find(query);
        List<UUID> ids = page.items().stream().map(PipelineRun::id).toList();
        var stepsByRun = steps.findByRunIds(ids);
        var unitsByRun = units.findByRunIds(ids);
        List<RunSummaryDto> items = page.items().stream()
                .map(run -> mapper.summary(run, stepsByRun.getOrDefault(run.id(), List.of()),
                        unitsByRun.getOrDefault(run.id(), List.of())))
                .toList();
        return new PageDto<>(items, page.page(), page.size(), page.totalItems(), page.totalPages());
    }

    public Optional<RunDetailDto> detail(UUID id) {
        return runs.findById(id).map(run -> {
            List<RunUnit> runUnits = units.findByRunId(run.id());
            List<PipelineStep> runSteps = steps.findByRunId(run.id());
            List<RunArtifact> runArtifacts = artifacts.findByRunId(run.id());
            List<ImportReport> runReports = reports.findByRunId(run.id());
            Optional<PipelineRun> active = runs.findActiveBySource(run.source());
            Map<UUID, UnitRetryEligibility> retry = new HashMap<>();
            Map<UUID, List<PipelineRun>> retriedBy = new HashMap<>();
            for (RunUnit unit : runUnits) {
                retry.put(unit.id(), UnitRetryRules.check(run, unit, active));
                retriedBy.put(unit.id(), runs.findRetriesOfUnit(unit.id()));
            }
            return mapper.detail(run, runUnits, runSteps, runArtifacts, runReports,
                    ReplayRules.check(run, runArtifacts), retry, retriedBy);
        });
    }
}
