package org.cttelsamicsterrassa.data.pipeline.core.run;

import java.util.List;

/** One page of runs, newest first. */
public record RunPage(List<PipelineRun> items, int page, int size, long totalItems) {

    public RunPage {
        items = List.copyOf(Checks.required(items, "items"));
    }

    public int totalPages() {
        return (int) ((totalItems + size - 1) / size);
    }
}
