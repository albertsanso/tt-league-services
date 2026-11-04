package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.util.List;

public record TriggerResponse(List<TriggerResultDto> results) {
}
