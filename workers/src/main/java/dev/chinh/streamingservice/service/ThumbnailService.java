package dev.chinh.streamingservice.service;

import dev.chinh.streamingservice.common.constant.Resolution;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private final RedisTemplate<String, String> queueRedisTemplate;

    public static final String thumbnailBucket = "thumbnail";
    private static final Resolution thumbnailResolution = Resolution.p360;

    public Set<ZSetOperations.TypedTuple<String>> getAllThumbnailCacheLastAccess(long max) {
        return queueRedisTemplate.opsForZSet()
                .rangeByScoreWithScores("thumbnail-cache", 0, max, 0, 50);
    }

    public void removeThumbnailLastAccess(String thumbnailFileName) {
        queueRedisTemplate.opsForZSet().remove("thumbnail-cache", thumbnailFileName);
    }

    public static String getThumbnailParentPath() {
        return "/thumbnail-cache/" + thumbnailResolution;
    }
}
