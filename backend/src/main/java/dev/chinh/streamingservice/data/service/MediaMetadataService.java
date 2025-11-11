package dev.chinh.streamingservice.data.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.search.data.MediaSearchItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class MediaMetadataService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public void cacheMediaSearchItem(MediaSearchItem item) {
        String id = "media::" + item.getId();
        redisTemplate.opsForValue().set(id, item, Duration.ofHours(1));
    }

    public void cacheMediaSearchItem(MediaSearchItem item, Duration duration) {
        String id = "media::" + item.getId();
        redisTemplate.opsForValue().set(id, item, duration);
    }

    public MediaSearchItem getCachedMediaSearchItem(String id) {
        return objectMapper.convertValue(redisTemplate.opsForValue().get("media::" + id), MediaSearchItem.class);
    }
}
