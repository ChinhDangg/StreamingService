package dev.chinh.streamingservice.backend.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import javax.sql.DataSource;

@Configuration
@EnableJpaRepositories(
        basePackages = "dev.chinh.streamingservice.persistence.repository"
)
@EntityScan(
        basePackages = "dev.chinh.streamingservice.persistence.entity"
)
public class PersistenceJpaConfig {

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl("jdbc:postgresql://localhost:5432/media_db");
        config.setUsername("myuser");
        config.setPassword("secret");
        config.setDriverClassName("org.postgresql.Driver");

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setPoolName("media-db-pool");
        return new HikariDataSource(config);
    }
}
