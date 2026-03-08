package dev.chinh.streamingservice.filemanager.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import dev.chinh.streamingservice.filemanager.service.FileService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableRetry
public class ApplicationConfig {

    public interface EntryCached {}
    public record UserDirUsing(Set<String> dirUserUsing) implements EntryCached {}
    public record DirectoryCached(String dirId, Set<String> userUsing) implements EntryCached {}

    @Bean
    public Cache<String, EntryCached> DirectoryIdCache(FileService fileService, ObjectProvider<Cache<String, EntryCached>> cacheProvider) {
        return Caffeine.newBuilder()
                .expireAfterAccess(15, TimeUnit.MINUTES)
                .removalListener((String key, EntryCached value, RemovalCause cause) -> {
                    Cache<String, EntryCached> cache = cacheProvider.getIfAvailable();

                    if (cause.wasEvicted()) { // if come from the expiry and not by a manual remove
                        if (value instanceof UserDirUsing(Set<String> dirUserUsing)) {
                            for (String dirId : dirUserUsing) {
                                if (cache != null)
                                    cleanupDirAccessCache(cache, dirId, key, fileService);
                            }
                        } else if (value instanceof DirectoryCached directoryCached) {
                            fileService.removeFileStatus(directoryCached.dirId());
                        }
                    }
                })
                .build();
    }

    private void cleanupDirAccessCache(Cache<String, EntryCached> cache, String dirKey, String userId, FileService fileService) {
        cache.asMap().computeIfPresent(dirKey, (_, v) -> {
            DirectoryCached directoryCached = (DirectoryCached) v;
            directoryCached.userUsing().remove(userId);
            if (directoryCached.userUsing().isEmpty()) {
                fileService.removeFileStatus(directoryCached.dirId());
                return null;
            }
            return directoryCached;
        });
    }
}
