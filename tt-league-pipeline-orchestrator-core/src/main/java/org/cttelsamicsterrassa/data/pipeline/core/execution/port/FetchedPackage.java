package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

public record FetchedPackage(String declaredSha256, StoredArtifact stored) {
}
