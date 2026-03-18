package dev.chinh.streamingservice.filemanager.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import dev.chinh.streamingservice.filemanager.data.FileItemField;
import dev.chinh.streamingservice.filemanager.data.FileSystemItem;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.retry.annotation.EnableRetry;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableRetry
public class ApplicationConfig {

    public interface EntryCached {}
    public record UserDirUsing(Set<String> dirUserUsing) implements EntryCached {}
    public record DirectoryCached(String dirId, Set<String> userUsing) implements EntryCached {}

    @Bean
    public Cache<String, EntryCached> DirectoryIdCache(MongoTemplate mongoTemplate, ObjectProvider<Cache<String, EntryCached>> cacheProvider) {
        return Caffeine.newBuilder()
                .expireAfterAccess(15, TimeUnit.MINUTES)
                .removalListener((String key, EntryCached value, RemovalCause cause) -> {
                    Cache<String, EntryCached> cache = cacheProvider.getIfAvailable();

                    if (cause.wasEvicted()) { // if come from the expiry and not by a manual remove
                        if (value instanceof UserDirUsing(Set<String> dirUserUsing)) {
                            for (String dirId : dirUserUsing) {
                                if (cache != null)
                                    cleanupDirAccessCache(cache, dirId, key, mongoTemplate);
                            }
                        } else if (value instanceof DirectoryCached directoryCached) {
                            removeFileStatus(mongoTemplate, directoryCached.dirId());
                        }
                    }
                })
                .build();
    }

    private void cleanupDirAccessCache(Cache<String, EntryCached> cache, String dirKey, String userId, MongoTemplate mongoTemplate) {
        cache.asMap().computeIfPresent(dirKey, (_, v) -> {
            DirectoryCached directoryCached = (DirectoryCached) v;
            directoryCached.userUsing().remove(userId);
            if (directoryCached.userUsing().isEmpty()) {
                removeFileStatus(mongoTemplate, directoryCached.dirId());
                return null;
            }
            return directoryCached;
        });
    }

    private void removeFileStatus(MongoTemplate mongoTemplate, String fileId) {
        Query query = new Query(Criteria.where("id").is(fileId));
        Update update = new Update().unset(FileItemField.STATUS_CODE);
        mongoTemplate.updateFirst(query, update, FileSystemItem.class);
    }
}
