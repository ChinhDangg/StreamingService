package dev.chinh.streamingservice.backend.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.backend.MediaMapper;
import dev.chinh.streamingservice.common.constant.MediaJobStatus;
import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import dev.chinh.streamingservice.backend.search.service.MediaSearchCacheService;
import dev.chinh.streamingservice.persistence.projection.MediaGroupInfo;
import dev.chinh.streamingservice.persistence.projection.MediaSearchItem;
import dev.chinh.streamingservice.persistence.entity.MediaDescription;
import dev.chinh.streamingservice.persistence.entity.MediaMetaData;
import dev.chinh.streamingservice.persistence.repository.MediaMetaDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collections;
import java.util.Objects;

@RequiredArgsConstructor
public abstract class MediaService {

    private final RedisTemplate<String, String> redisStringTemplate;
    protected final ObjectMapper objectMapper;
    protected final MediaMapper mediaMapper;
    protected final MediaMetaDataRepository mediaRepository;
    protected final MinIOService minIOService;
    protected final MediaSearchCacheService mediaSearchCacheService;

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

    protected MediaJobDescription getMediaJobDescription(MediaDescription mediaDescription, String cacheJobId, Resolution resolution, String jobType) {
        MediaJobDescription mediaJobDescription = mediaMapper.mapToJobDescription(mediaDescription);
        mediaJobDescription.setJobType(jobType);
        mediaJobDescription.setWorkId(cacheJobId);
        mediaJobDescription.setResolution(resolution);
        return mediaJobDescription;
    }

    protected Object getQueueJobStatus(String cacheJobId) {
        return redisStringTemplate.opsForHash().get("ffmpeg_job_status:" + cacheJobId, "status");
    }

    protected Object getQueueJobResult(String cacheJobId, String resultField) {
        return redisStringTemplate.opsForHash().get("ffmpeg_job_status:" + cacheJobId, resultField);
    }

    protected void updateQueueJobStatus(String jobId, String status, String differentField) {
        redisStringTemplate.opsForHash().put("ffmpeg_job_status:" + jobId, Objects.requireNonNullElse(differentField, "status"), status);
    }

    public void addJobToQueue(String queueKey, MediaJobDescription mediaJobDescription) throws JsonProcessingException {
        System.out.println("Adding job to queue: " + mediaJobDescription.getWorkId());
        redisStringTemplate.opsForStream().add(
                StreamRecords.string(
                        Collections.singletonMap("job_description", objectMapper.writeValueAsString(mediaJobDescription))
                ).withStreamKey(queueKey));
    }

    /**
     * @param mediaWorkId: specific media content saved to memory e.g. 1:p360
     */
    protected void addCacheLastAccess(String key, String mediaWorkId, Long expiry) {
        expiry = expiry == null ? System.currentTimeMillis() : expiry;
        redisStringTemplate.opsForZSet().add(key, mediaWorkId, expiry);
    }

    public String getCacheMediaJobId(long mediaId, Resolution res) {
        return mediaId + ":" + res;
    }

    protected MediaDescription getMediaDescription(long mediaId) {
        MediaDescription mediaDescription = mediaSearchCacheService.getCachedMediaSearchItem(mediaId);
        if (mediaDescription == null)
            mediaDescription = findMediaMetaDataAllInfo(mediaId);
        return mediaDescription;
    }

    protected MediaMetaData findMediaMetaDataAllInfo(long id) {
        MediaMetaData mediaMetaData = mediaRepository.findByIdWithAllInfo(id).orElseThrow(() ->
                new IllegalArgumentException("Media not found with id " + id));
        MediaSearchItem mediaSearchItem = mediaMapper.map(mediaMetaData);
        if (mediaMetaData.isGrouper()) {
            mediaSearchItem.setMediaGroupInfo(new MediaGroupInfo(mediaMetaData.getGrouperId(), null, mediaMetaData.getGroupInfo().getNumInfo()));
        } else if (mediaMetaData.getGrouperId() != null) {
            mediaSearchItem.setMediaGroupInfo(
                    new MediaGroupInfo(null, mediaMetaData.getGrouperId(), mediaMetaData.getGroupInfo().getNumInfo()));
        }
        mediaSearchCacheService.cacheMediaSearchItem(mediaSearchItem);
        return mediaMetaData;
    }
}
