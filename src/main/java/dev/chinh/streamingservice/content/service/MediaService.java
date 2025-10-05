package dev.chinh.streamingservice.content.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

@RequiredArgsConstructor
public abstract class MediaService {

    protected final RedisTemplate<String, Object> redisTemplate;
    protected final MinIOService minIOService;
}
