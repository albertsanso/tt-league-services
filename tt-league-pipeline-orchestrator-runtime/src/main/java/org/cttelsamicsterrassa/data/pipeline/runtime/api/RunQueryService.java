package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunPage;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunQuery;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ImportReportRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineStepRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunArtifactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the runs API; uses the core ports only, in one read-only transaction per call. */
@Service
@Transactional(readOnly = true)
public class RunQueryService {

    private final PipelineRunRepository runs;
    private final PipelineStepRepository steps;
    private final RunArtifactRepository artifacts;
    private final ImportReportRepository reports;
    private final RunDtoMapper mapper;

    RunQueryService(PipelineRunRepository runs, PipelineStepRepository steps, RunArtifactRepository artifacts,
            ImportReportRepository reports, RunDtoMapper mapper) {
        this.runs = runs;
        this.steps = steps;
        this.artifacts = artifacts;
        this.reports = reports;
        this.mapper = mapper;
    }

    public PageDto<RunSummaryDto> list(RunQuery query) {
        RunPage page = runs.find(query);
        var stepsByRun = steps.findByRunIds(page.items().stream().map(PipelineRun::id).toList());
        List<RunSummaryDto> items = page.items().stream()
                .map(run -> mapper.summary(run, stepsByRun.getOrDefault(run.id(), List.of())))
                .toList();
        return new PageDto<>(items, page.page(), page.size(), page.totalItems(), page.totalPages());
    }

    public Optional<RunDetailDto> detail(UUID id) {
        return runs.findById(id).map(run -> {
            List<PipelineStep> runSteps = steps.findByRunId(run.id());
            return mapper.detail(run, runSteps, artifacts.findByRunId(run.id()),
                    reports.findByRunId(run.id()).orElse(null));
        });
    }
}
