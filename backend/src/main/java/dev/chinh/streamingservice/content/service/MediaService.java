package dev.chinh.streamingservice.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.MediaMapper;
import dev.chinh.streamingservice.common.constant.MediaJobStatus;
import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import dev.chinh.streamingservice.data.repository.MediaMetaDataRepository;
import dev.chinh.streamingservice.data.entity.MediaDescription;
import dev.chinh.streamingservice.data.entity.MediaMetaData;
import dev.chinh.streamingservice.data.service.MediaMetadataService;
import dev.chinh.streamingservice.search.data.MediaGroupInfo;
import dev.chinh.streamingservice.search.data.MediaSearchItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collections;
import java.util.Map;

@RequiredArgsConstructor
public abstract class MediaService {

    private final RedisTemplate<String, String> redisStringTemplate;
    protected final RedisTemplate<String, Object> redisTemplate;
    protected final ObjectMapper objectMapper;
    protected final MediaMapper mediaMapper;
    private final MediaMetaDataRepository mediaRepository;
    protected final MinIOService minIOService;
    protected final MediaMetadataService mediaMetadataService;

    protected String addJobToFfmpegQueue(String queueKey, String cacheJobId, MediaJobDescription mediaJobDescription) throws JsonProcessingException {
        Map<Object, Object> jobQueueStatus = getQueueJobStatus(cacheJobId);
        if (!jobQueueStatus.isEmpty()) {
            String status = (String) jobQueueStatus.get("status");
            if (status.equals(MediaJobStatus.COMPLETED.name())) {
                return jobQueueStatus.get("result").toString();
            } else if (status.equals(MediaJobStatus.RUNNING.name())) {
                return MediaJobStatus.RUNNING.name();
            }
        }

        addJobToQueue(queueKey, mediaJobDescription);
        return MediaJobStatus.RUNNING.name();
    }

    protected MediaJobDescription getMediaJobDescription(long mediaId, String cacheJobId, Resolution resolution, String jobType) {
        MediaDescription mediaDescription = getMediaDescription(mediaId);
        MediaJobDescription mediaJobDescription = mediaMapper.mapToJobDescription(mediaDescription);
        mediaJobDescription.setJobType(jobType);
        mediaJobDescription.setWorkId(cacheJobId);
        mediaJobDescription.setResolution(resolution);
        return mediaJobDescription;
    }

    protected Map<Object, Object> getQueueJobStatus(String cacheJobId) {
        return redisStringTemplate.opsForHash().entries("ffmpeg_job_status:" + cacheJobId);
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
        redisTemplate.opsForZSet().add(key, mediaWorkId, expiry);
    }

    public String getCacheMediaJobId(long mediaId, Resolution res) {
        return mediaId + ":" + res;
    }

    protected MediaDescription getMediaDescription(long mediaId) {
        MediaDescription mediaDescription = mediaMetadataService.getCachedMediaSearchItem(mediaId);
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
        mediaMetadataService.cacheMediaSearchItem(mediaSearchItem);
        return mediaMetaData;
    }
}
