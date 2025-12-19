package dev.chinh.streamingservice.searchindexer.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(
        basePackages = "dev.chinh.streamingservice.persistence.repository"
)
@EntityScan(
        basePackages = "dev.chinh.streamingservice.persistence.entity"
)
public class PersistenceJpaConfig {
}
