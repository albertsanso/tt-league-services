package org.cttelsamicsterrassa.data.api.rest.importjob;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.port.ImportSourcesPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
public class ImportJobConfig {
    @Bean
    public ImportSourcesPort importSourcesPort(
            @Value("${tt.league.import.sources:RFETM,BCNESA,FCTT}") String configuredSources) {
        List<ImportSource> sources = Arrays.stream(configuredSources.split(","))
                .map(String::trim).filter(value -> !value.isEmpty())
                .map(value -> ImportSource.valueOf(value.toUpperCase()))
                .toList();
        return () -> sources;
    }

}
