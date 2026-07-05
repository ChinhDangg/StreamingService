package dev.chinh.streamingservice.filemanager.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import dev.chinh.streamingservice.filemanager.data.FileSystemItem;
import dev.chinh.streamingservice.filemanager.service.FileLockService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableRetry
public class ApplicationConfig {

    public interface EntryCached {}
    public record UserDirUsing(Set<String> dirUserUsing) implements EntryCached {}
    public record DirectoryCached(String dirId, Set<String> userUsing) implements EntryCached {}

    @Bean
    public Cache<String, EntryCached> DirectoryIdCache(FileLockService fileLockService, ObjectProvider<Cache<String, EntryCached>> cacheProvider, Cache<String, FileSystemItem> fileCache) {
        return Caffeine.newBuilder()
                .expireAfterAccess(15, TimeUnit.MINUTES)
                .removalListener((String key, EntryCached value, RemovalCause cause) -> {
                    if (cause.wasEvicted()) { // if come from the expiry and not by a manual remove
                        // Offload cache mutations to a separate thread to prevent
                        // ConcurrentHashMap Recursive Update exceptions
                        CompletableFuture.runAsync(() -> {
                            Cache<String, EntryCached> cache = cacheProvider.getIfAvailable();
                            if (cache != null) {
                                if (value instanceof UserDirUsing(Set<String> dirUserUsing)) {
                                    for (String dirId : dirUserUsing) {
                                        cleanupDirAccessCache(cache, dirId, key, fileLockService, fileCache);
                                    }
                                } else if (value instanceof DirectoryCached directoryCached) {
                                    removeFileStatus(fileLockService, directoryCached.dirId(), fileCache);
                                }
                            }
                        });

                    }
                })
                .build();
    }

    private void cleanupDirAccessCache(Cache<String, EntryCached> cache, String dirKey, String userId, FileLockService fileLockService, Cache<String, FileSystemItem> fileCache) {
        cache.asMap().computeIfPresent(dirKey, (_, v) -> {
            DirectoryCached directoryCached = (DirectoryCached) v;
            directoryCached.userUsing().remove(userId);
            if (directoryCached.userUsing().isEmpty()) {
                removeFileStatus(fileLockService, directoryCached.dirId(), fileCache);
                return null;
            }
            return directoryCached;
        });
    }

    private void removeFileStatus(FileLockService fileLockService, String fileId, Cache<String, FileSystemItem> fileCache) {
        fileLockService.releaseLockedFileItem(Set.of(fileId));
        fileCache.invalidate(fileId);
    }

    @Bean
    public Cache<String, FileSystemItem> FileCache() {
        return Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterAccess(15, TimeUnit.MINUTES)
                .build();
    }
}
