package org.cttelsamicsterrassa.data.pipeline.runtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PipelineOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(PipelineOrchestratorApplication.class, args);
    }
}
