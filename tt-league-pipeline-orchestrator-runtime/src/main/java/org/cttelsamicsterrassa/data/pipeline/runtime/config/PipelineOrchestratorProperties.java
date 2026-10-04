package org.cttelsamicsterrassa.data.pipeline.runtime.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("tt.pipeline")
public record PipelineOrchestratorProperties(
        @Valid @NotNull Platform platform,
        @Valid @NotNull Ingest ingest,
        @Valid @NotNull Security security) {

    public record Platform(@NotNull URI baseUrl) {
    }

    public record Ingest(@NotNull URI baseUrl, @NotBlank String apiKey) {
    }

    public record Security(@NotBlank @Size(min = 32) String jwtSecret) {
    }
}
