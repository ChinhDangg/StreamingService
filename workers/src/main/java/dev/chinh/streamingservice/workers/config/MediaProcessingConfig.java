package dev.chinh.streamingservice.workers.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class MediaProcessingConfig {

    @Bean(name = "ffmpegExecutor")
    public ExecutorService ffmpegExecutor() {
        // Returns an Executor that starts a new virtual thread for each task.
        // No need for a destroyMethod="shutdown" because virtual threads
        // are closed automatically when using try-with-resources or
        // when the application shuts down.
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
