package dev.chinh.streamingservice.backend.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.mediapersistence.projection.MediaSearchItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class MediaSearchCacheService {

    private final RedisTemplate<String, String> redisStringTemplate;
    private final ObjectMapper objectMapper;

    public void cacheMediaSearchItem(MediaSearchItem item) {
        String id = "media::" + item.getId();
        try {
            String json = objectMapper.writeValueAsString(item);
            redisStringTemplate.opsForValue().set(id, json, Duration.ofHours(1));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse json", e);
        }
    }

    public void cacheMediaSearchItem(MediaSearchItem item, Duration duration) {
        String id = "media::" + item.getId();
        try {
            String json = objectMapper.writeValueAsString(item);
            setOrRefreshTtl(id, json, duration.getSeconds());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse json", e);
        }
    }

    public void setOrRefreshTtl(String key, String value, long timeoutInSeconds) {
        String luaScript =
                "if redis.call('EXISTS', KEYS[1]) == 1 then " +
                "    return redis.call('EXPIRE', KEYS[1], ARGV[2]) " + // If exists, just update TTL
                "else " +
                "    return redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2]) " + // If not, set value + TTL
                "end";
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(luaScript, Long.class);

        redisStringTemplate.execute(script,
                Collections.singletonList(key),
                value,
                String.valueOf(timeoutInSeconds)
        );
    }

    public MediaSearchItem getCachedMediaSearchItem(long id) {
        String json = redisStringTemplate.opsForValue().get("media::" + id);
        if (json == null)
            return null;
        try {
            return objectMapper.readValue(json, MediaSearchItem.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse json", e);
        }
    }

    public void removeCachedMediaSearchItem(long id) {
        redisStringTemplate.delete("media::" + id);
    }
}
