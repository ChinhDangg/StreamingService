package dev.chinh.streamingservice.workers.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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

    public abstract void handleJob(String tokenKey, MediaJobDescription jobDescription);

    public boolean isJobWithinHandleWindow(String tokenKey, MediaJobDescription jobDescription) {
        Instant scheduledTime = jobDescription.getScheduledTime();
        Instant fiveMinutesAgo = Instant.now().minus(5, ChronoUnit.MINUTES);
        return !scheduledTime.isBefore(fiveMinutesAgo); // has passed 5 minutes then don't rehandle
    }

    protected final String masterFileName = "/master.m3u8";

    protected Double getCacheLastAccess(String key, String mediaWorkId) {
        return redisTemplate.opsForZSet().score(key, mediaWorkId);
    }

    protected void removeCacheLastAccess(String key, String mediaWorkId) {
        redisTemplate.opsForZSet().remove(key, mediaWorkId);
    }

    protected void removeJobStatus(String jobId) {
        workerRedisService.removeStatus(jobId);
    }

    public List<String> getLogsFromInputStream(InputStream inputStream) {
        List<String> logs = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                logs.add("[ffmpeg] " + line);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return logs;
    }

    /**
     * Already sorted by default. Get oldest one first
     * @return mediaJobId in batch of 50.
     */
    protected Set<ZSetOperations.TypedTuple<String>> getAllCacheLastAccess(String key, long max) {
        return redisTemplate.opsForZSet()
                .rangeByScoreWithScores(key, 0, max, 0, 50);
    }

    protected String getCacheMediaJobIdString(long mediaId, Resolution res) {
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
