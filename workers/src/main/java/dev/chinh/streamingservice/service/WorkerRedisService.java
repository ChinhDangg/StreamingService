package dev.chinh.streamingservice.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Service
public class WorkerRedisService {

    private final RedisTemplate<String, String> redisTemplate;

    public WorkerRedisService(
            @Qualifier("queueRedisTemplate") RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    private static final DefaultRedisScript<Long> acquireScript;
    private static final DefaultRedisScript<Long> releaseScript;

    static {
        acquireScript = new DefaultRedisScript<>();
        acquireScript.setScriptText(
                "local key = KEYS[1] " +
                        "local current = tonumber(redis.call('GET', key)) " +
                        "if current ~= nil and current > 0 then " +
                        "  redis.call('DECR', key) " +
                        "  return 1 " +
                        "end " +
                        "return 0"
        );
        acquireScript.setResultType(Long.class);

        releaseScript = new DefaultRedisScript<>();
        releaseScript.setScriptText(
                "local key = KEYS[1] " +
                        "redis.call('INCR', key) " +
                        "return 1"
        );
        releaseScript.setResultType(Long.class);
    }

    // Call on startup or via config
    public void initializeTokens(String tokenKey, int maxConcurrentJobs) {
        redisTemplate.opsForValue().set(tokenKey, String.valueOf(maxConcurrentJobs));
    }

    public boolean tryAcquireToken(String tokenKey) {
        Long result = redisTemplate.execute(acquireScript, Collections.singletonList(tokenKey));
        return result == 1L;
    }

    public void releaseToken(String tokenKey) {
        redisTemplate.execute(releaseScript, Collections.singletonList(tokenKey));
    }

    public void updateStatus(String jobId, String status) {
        redisTemplate.opsForHash().put("ffmpeg_job_status:" + jobId, "status", status);
    }

    public void addResultToStatus(String jobId, String resultKey, String result) {
        redisTemplate.opsForHash().put("ffmpeg_job_status:" + jobId, resultKey, result);
    }


    // stream init
    public void createGroupIfAbsent(String stream, String group) {
        try {
            redisTemplate.opsForStream().createGroup(stream, ReadOffset.latest(), group);
        } catch (Exception ignored) {
            // BUSY-GROUP - already exists
        }
    }

    // stream read
    public List<MapRecord<String, Object, Object>> readFromStream(String stream, String group, String consumer, Duration blockTime) {
        return redisTemplate.opsForStream().read(
                Consumer.from(group, consumer),
                StreamReadOptions.empty()
                        .block(blockTime)
                        .count(1),
                StreamOffset.create(stream, ReadOffset.lastConsumed())
        );
    }

    public void ack(String stream, String group, RecordId recordId) {
        redisTemplate.opsForStream().acknowledge(stream, group, recordId);
    }

    public PendingMessages getPendingMessages(String stream, String group, int count) {
        return redisTemplate.opsForStream().pending(
                stream,
                group,
                Range.unbounded(),
                count
        );
    }

    public List<MapRecord<String, Object, Object>> claim(
            String stream,
            String group,
            String consumer,
            Duration minIdleTime,
            List<RecordId> ids
    ) {
        if (ids.isEmpty()) {
            return List.of();
        }

        return redisTemplate.opsForStream().claim(
                stream,
                group,
                consumer,
                minIdleTime,
                ids.toArray(new RecordId[0])
        );
    }
}
