package dev.chinh.streamingservice.workers.task.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.common.constant.MediaJobStatus;
import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import dev.chinh.streamingservice.mediapersistence.projection.MediaGroupInfo;
import dev.chinh.streamingservice.mediapersistence.projection.MediaSearchItem;
import dev.chinh.streamingservice.mediapersistence.entity.MediaDescription;
import dev.chinh.streamingservice.mediapersistence.entity.MediaMetaData;
import dev.chinh.streamingservice.mediapersistence.repository.MediaMetaDataRepository;
import dev.chinh.streamingservice.workers.MediaMapper;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public abstract class TaskMediaService {

    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    protected final ObjectMapper objectMapper;
    protected final MediaMapper mediaMapper;
    protected final MediaMetaDataRepository mediaRepository;

    public record JobStatus(String jobId, String result) {}

    protected String addJobToFfmpegQueue(String queueKey, String cacheJobId, String resultField, MediaJobDescription mediaJobDescription) throws JsonProcessingException {
        Object jobQueueStatus = getQueueJobStatus(cacheJobId);
        if (jobQueueStatus != null) {
            String status = (String) jobQueueStatus;
            if (status.equals(MediaJobStatus.RUNNING.name()) || status.equals(MediaJobStatus.COMPLETED.name())) {
                return getQueueJobResult(cacheJobId, resultField).toString();
            } else if (status.equals(MediaJobStatus.PROCESSING.name())) {
                return status;
            }
        }

        // no status or stopped
        addJobToQueue(queueKey, mediaJobDescription);
        updateQueueJobStatus(cacheJobId, MediaJobStatus.PROCESSING.name(), null);
        return MediaJobStatus.PROCESSING.name();
    }

    protected MediaJobDescription getMediaJobDescription(String userId, MediaDescription mediaDescription, String cacheJobId, Resolution resolution, String jobType) {
        MediaJobDescription mediaJobDescription = mediaMapper.mapToJobDescription(mediaDescription);
        mediaJobDescription.setUserId(userId);
        mediaJobDescription.setJobType(jobType);
        mediaJobDescription.setWorkId(cacheJobId);
        mediaJobDescription.setResolution(resolution);
        return mediaJobDescription;
    }

    protected Object getQueueJobStatus(String cacheJobId) {
        return redisTemplate.opsForHash().get("ffmpeg_job_status:" + cacheJobId, "status");
    }

    protected Object getQueueJobResult(String cacheJobId, String resultField) {
        return redisTemplate.opsForHash().get("ffmpeg_job_status:" + cacheJobId, resultField);
    }

    protected void updateQueueJobStatus(String jobId, String status, String differentField) {
        redisTemplate.opsForHash().put("ffmpeg_job_status:" + jobId, Objects.requireNonNullElse(differentField, "status"), status);
    }

    public void addJobToQueue(String queueKey, MediaJobDescription mediaJobDescription) throws JsonProcessingException {
        System.out.println("Adding job to queue: " + mediaJobDescription.getWorkId());
        redisTemplate.opsForStream().add(
                StreamRecords.string(
                        Collections.singletonMap("job_description", objectMapper.writeValueAsString(mediaJobDescription))
                ).withStreamKey(queueKey));
    }

    /**
     * @param mediaWorkId: specific media content saved to memory e.g. 1:p360
     */
    protected void addCacheLastAccess(String key, String mediaWorkId, Long expiry) {
        expiry = expiry == null ? System.currentTimeMillis() : expiry;
        redisTemplate.opsForZSet().add(key, mediaWorkId, expiry);
    }

    public String getCacheMediaJobId(long mediaId, Resolution res) {
        return mediaId + ":" + res;
    }

    protected MediaDescription getMediaDescription(String userId, long mediaId) {
        MediaDescription mediaDescription = getCachedMediaSearchItem(userId, mediaId);
        if (mediaDescription != null)
            return mediaDescription;

        String lockKey = "media_lock:" + mediaId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                try {
                    // double-check lock
                    mediaDescription = getCachedMediaSearchItem(userId, mediaId);
                    if (mediaDescription != null)
                        return mediaDescription;

                    // query the database and then save the result to cache
                    mediaDescription = findMediaMetaDataAllInfo(userId, mediaId);
                } finally {
                    lock.unlock();
                }
            } else {
                // if can't get the lock, query the database and then save the result to cache
                System.err.println("Failed to get lock for media id: " + mediaId);
                mediaDescription = findMediaMetaDataAllInfo(userId, mediaId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while waiting for lock: " + lockKey + " for media id: " + mediaId);
        }
        return mediaDescription;
    }

    protected MediaMetaData findMediaMetaDataAllInfo(String userId, long id) {
        MediaMetaData mediaMetaData = mediaRepository.findByIdWithAllInfo(Long.parseLong(userId), id).orElseThrow(() ->
                new IllegalArgumentException("Media not found with id " + id));
        MediaSearchItem mediaSearchItem = mediaMapper.map(mediaMetaData);
        if (mediaMetaData.isGrouper()) {
            mediaSearchItem.setMediaGroupInfo(new MediaGroupInfo(mediaMetaData.getGrouperId(), null, mediaMetaData.getGroupInfo().getNumInfo()));
        } else if (mediaMetaData.getGrouperId() != null) {
            mediaSearchItem.setMediaGroupInfo(
                    new MediaGroupInfo(null, mediaMetaData.getGrouperId(), mediaMetaData.getGroupInfo().getNumInfo()));
        }
        cacheMediaSearchItem(userId, mediaSearchItem);
        return mediaMetaData;
    }

    public MediaDescription getCachedMediaSearchItem(String userId, long id) {
        String json = redisTemplate.opsForValue().get(getCacheItemString(userId, id));
        if (json == null)
            return null;
        try {
            return objectMapper.readValue(json, MediaSearchItem.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse json", e);
        }
    }

    public void cacheMediaSearchItem(String userId, MediaSearchItem item) {
        try {
            String json = objectMapper.writeValueAsString(item);
            redisTemplate.opsForValue().set(getCacheItemString(userId, item.getId()), json, Duration.ofHours(1));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse json", e);
        }
    }

    private String getCacheItemString(String userId, long id) {
        return "media::" + userId + ":" + id;
    }
}
