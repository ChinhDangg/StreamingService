package dev.chinh.streamingservice.mediaupload.upload.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaSearchCacheService {

    private final RedisTemplate<String, String> redisStringTemplate;

    public void removeCachedMediaSearchItem(long id) {
        redisStringTemplate.delete("media::" + id);
    }
}
