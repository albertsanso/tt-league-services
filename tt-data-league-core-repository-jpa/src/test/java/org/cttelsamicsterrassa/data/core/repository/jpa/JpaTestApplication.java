package org.cttelsamicsterrassa.data.core.repository.jpa;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Boots the persistence adapter alone, against H2, so repository behaviour and schema constraints
 * can be tested without the import runtime.
 */
@SpringBootApplication(scanBasePackages = "org.cttelsamicsterrassa")
@EnableJpaRepositories(basePackages = "org.cttelsamicsterrassa")
@EntityScan(basePackages = "org.cttelsamicsterrassa")
public class JpaTestApplication {
}
