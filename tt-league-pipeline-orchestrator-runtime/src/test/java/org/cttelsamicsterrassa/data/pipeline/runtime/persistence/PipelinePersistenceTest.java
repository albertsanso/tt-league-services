package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Full-context test against a real PostgreSQL; skipped when Docker is not available. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "tt.pipeline.platform.base-url=http://localhost:8080",
            "tt.pipeline.ingest.base-url=http://localhost:8000",
            "tt.pipeline.ingest.api-key=test-ingest-key",
            "tt.pipeline.security.jwt-secret=0123456789abcdef0123456789abcdef"
        })
@Import(PostgresTestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
public @interface PipelinePersistenceTest {
}
