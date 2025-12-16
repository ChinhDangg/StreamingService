package dev.chinh.streamingservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.common.constant.Resolution;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Set;

public abstract class MediaService {

    protected final RedisTemplate<String, String> redisTemplate;
    protected final ObjectMapper objectMapper;
    protected final MinIOService minIOService;
    protected final WorkerRedisService workerRedisService;

    public MediaService(@Qualifier("queueRedisTemplate") RedisTemplate<String, String> redisTemplate,
                        ObjectMapper objectMapper,
                        MinIOService minIOService,
                        WorkerRedisService workerRedisService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.minIOService = minIOService;
        this.workerRedisService = workerRedisService;
    }

    protected final String masterFileName = "/master.m3u8";

    /**
     * @param mediaWorkId: specific media content saved to memory e.g. 1:p360
     */
    protected void addCacheLastAccess(String key, String mediaWorkId, Long expiry) {
        expiry = expiry == null ? System.currentTimeMillis() : expiry;
        redisTemplate.opsForZSet().add(key, mediaWorkId, expiry);
    }

    protected Double getCacheLastAccess(String key, String mediaWorkId) {
        return redisTemplate.opsForZSet().score(key, mediaWorkId);
    }

    protected void removeCacheLastAccess(String key, String mediaWorkId) {
        redisTemplate.opsForZSet().remove(key, mediaWorkId);
    }

    protected void removeJobStatus(String jobId) {
        workerRedisService.removeStatus(jobId);
    }

    /**
     * Already sorted by default. Get oldest one first
     * @return mediaJobId in batch of 50.
     */
    protected Set<ZSetOperations.TypedTuple<String>> getAllCacheLastAccess(String key, long max) {
        return redisTemplate.opsForZSet()
                .rangeByScoreWithScores(key, 0, max, 0, 50);
    }

    public String getCacheMediaJobIdString(long mediaId, Resolution res) {
        return mediaId + ":" + res;
    }

    protected String getNginxVideoStreamUrl(String videoDir) {
        return "/stream/" + videoDir + masterFileName;
    }

    protected boolean checkSrcSmallerThanTarget(int width, int height, int target) {
        if (width >= height) { // Landscape
            return height <= target;
        } else { // Portrait
            return width <= target;
        }
    }
}
