package dev.chinh.streamingservice.workers.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class MediaProcessingConfig {

    @Bean(name = "ffmpegExecutor", destroyMethod = "shutdown")
    public ExecutorService ffmpegExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        return Executors.newFixedThreadPool(Math.max(1, cores / 4));
    }
}
