package org.cttelsamicsterrassa.data.core.domain.shared.model;

public record ImportJobRequest(ImportSource source, String mappingVersion, boolean preview) {
}
