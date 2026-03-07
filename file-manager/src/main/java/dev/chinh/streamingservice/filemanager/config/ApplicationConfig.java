package dev.chinh.streamingservice.filemanager.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import dev.chinh.streamingservice.filemanager.service.FileService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableRetry
public class ApplicationConfig {

    @Bean
    public Cache<String, String> DirectoryIdCache(FileService fileService) {
        return Caffeine.newBuilder()
                .expireAfterAccess(15, TimeUnit.MINUTES)
                .removalListener((String key, String value, RemovalCause cause) -> {
                    fileService.removeFileStatus(value);
                })
                .build();
    }
}
