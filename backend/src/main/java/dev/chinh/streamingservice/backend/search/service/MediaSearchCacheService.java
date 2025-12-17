package dev.chinh.streamingservice.backend.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.backend.search.data.MediaSearchItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

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
            redisStringTemplate.opsForValue().set(id, json, duration);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse json", e);
        }
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
