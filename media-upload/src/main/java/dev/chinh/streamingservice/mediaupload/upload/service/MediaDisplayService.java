package dev.chinh.streamingservice.mediaupload.upload.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaDisplayService {

    private final RedisTemplate<String, String> redisStringTemplate;

    public void removeCacheGroupOfMedia(long mediaId) {
        String id = "grouper::" + mediaId;
        redisStringTemplate.delete(id);
    }

    public void deleteAllCacheForMedia(long mediaId) {
        String pattern = "grouper::" + mediaId + ":*";

        // 1. Use the non-deprecated redisTemplate.scan() method
        //    It respects the configured KeySerializer (StringRedisSerializer) and returns String keys.
        try (Cursor<String> cursor = redisStringTemplate.scan(ScanOptions.scanOptions()
                .match(pattern)
                .count(1000)
                .build())) {

            // 2. Collect all keys returned by the cursor into a list
            List<String> keysToDelete = new ArrayList<>();
            cursor.forEachRemaining(keysToDelete::add);

            // 3. Delete the keys in a single operation
            if (!keysToDelete.isEmpty()) {
                // redisTemplate.delete(Collection<K> keys) is safe and respects serialization
                redisStringTemplate.delete(keysToDelete);
            }
        }
        // The try-with-resources block ensures the cursor is closed automatically.
    }

}
