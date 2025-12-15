package dev.chinh.streamingservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MediaUploadService {

    private final RedisTemplate<String, String> queueRedisTemplate;
    private final MinIOService minIOService;

    public Set<ZSetOperations.TypedTuple<String>> getAllUploadSessionCacheLastAccess(long max) {
        return queueRedisTemplate.opsForZSet().rangeByScoreWithScores("upload::lastAccess", 0, max, 0, 50);
    }

    public Map<Object, Object> getUploadSessionObjects(String sessionId) {
        var map = queueRedisTemplate.opsForHash().entries("upload::" + sessionId);
        if (map.isEmpty()) return Map.of();
        map.remove("objectName");
        map.remove("mediaType");
        return map;
    }

    public void removeUploadSessionCacheLastAccess(String sessionId) {
        queueRedisTemplate.opsForZSet().remove("upload::lastAccess", sessionId);
    }

    public void removeCacheMediaSessionRequest(String sessionId) {
        String key = "upload::" + sessionId;
        queueRedisTemplate.delete(key);
    }

    public void removeUploadObject(String object) throws Exception {
        String mediaBucket = "media";
        minIOService.removeFile(mediaBucket, object);
    }
}
