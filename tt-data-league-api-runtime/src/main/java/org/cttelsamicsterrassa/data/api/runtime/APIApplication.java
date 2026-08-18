package org.cttelsamicsterrassa.data.api.runtime;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "org.cttelsamicsterrassa", "org.albertsanso.commons"
})
@EnableJpaRepositories(basePackages = "org.cttelsamicsterrassa")
@EntityScan(basePackages = "org.cttelsamicsterrassa")
public class APIApplication implements CommandLineRunner {
    @Override
    public void run(String... args) throws Exception {
        System.out.println("API Application started successfully.");
    }

    public static void main(String[] args) {
        SpringApplication.run(APIApplication.class, args);
    }
}
