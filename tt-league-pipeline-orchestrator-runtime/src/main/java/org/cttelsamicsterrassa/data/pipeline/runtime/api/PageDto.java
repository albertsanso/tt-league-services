package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.util.List;

public record PageDto<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
}
