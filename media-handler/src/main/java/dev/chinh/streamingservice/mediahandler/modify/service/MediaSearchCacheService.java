package dev.chinh.streamingservice.mediahandler.modify.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaSearchCacheService {

    private final StringRedisTemplate redisTemplate;

    public void removeCachedMediaSearchItem(String userId, long id) {
        redisTemplate.delete("media::" + userId + ":" + id);
    }

    public void removeCachedMediaSearchItem(String userId, List<Long> ids) {
        List<String> keys = ids.stream().map(id -> "media::" + userId + ":" + id).toList();
        redisTemplate.delete(keys);
    }

    public void removeCacheGroupOfMedia(String userId, long mediaId) {
        String id = "grouper::" + userId + ":" + mediaId;
        redisTemplate.delete(id);
    }

    public void removeCacheGroupOfMedia(String userId, List<Long> mediaIds) {
        List<String> keys = mediaIds.stream().map(id -> "grouper::" + userId + ":" + id).toList();
        redisTemplate.delete(keys);
    }

    public void deleteAllCacheForMedia(long mediaId) {
        String pattern = "grouper::" + mediaId + ":*";

        // 1. Use the non-deprecated redisTemplate.scan() method
        //    It respects the configured KeySerializer (StringRedisSerializer) and returns String keys.
        try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions()
                .match(pattern)
                .count(1000)
                .build())) {

            // 2. Collect all keys returned by the cursor into a list
            List<String> keysToDelete = new ArrayList<>();
            cursor.forEachRemaining(keysToDelete::add);

            // 3. Delete the keys in a single operation
            if (!keysToDelete.isEmpty()) {
                // redisTemplate.delete(Collection<K> keys) is safe and respects serialization
                redisTemplate.delete(keysToDelete);
            }
        }
        // The try-with-resources block ensures the cursor is closed automatically.
    }
}
