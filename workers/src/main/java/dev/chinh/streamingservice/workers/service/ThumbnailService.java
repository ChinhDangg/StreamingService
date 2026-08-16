package dev.chinh.streamingservice.workers.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private final StringRedisTemplate redisTemplate;

    public Set<ZSetOperations.TypedTuple<String>> getAllThumbnailCacheLastAccess(long max) {
        return redisTemplate.opsForZSet()
                .rangeByScoreWithScores("thumbnail-cache", 0, max, 0, 50);
    }

    public void removeThumbnailLastAccess(String thumbnailFileName) {
        redisTemplate.opsForZSet().remove("thumbnail-cache", thumbnailFileName);
    }

    public static String getThumbnailParentPath() {
        return "/thumbnail-cache";
    }
}
