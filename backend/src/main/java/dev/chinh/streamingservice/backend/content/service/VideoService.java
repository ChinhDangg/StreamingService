package dev.chinh.streamingservice.backend.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.backend.MediaMapper;
import dev.chinh.streamingservice.common.constant.MediaJobStatus;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.backend.search.service.MediaSearchCacheService;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import dev.chinh.streamingservice.mediapersistence.entity.MediaDescription;
import dev.chinh.streamingservice.mediapersistence.repository.MediaMetaDataRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Service
public class VideoService extends MediaService {

    public final String ffmpegQueueKey = ContentMetaData.FFMPEG_VIDEO_QUEUE_KEY;

    public VideoService(RedisTemplate<String, String> redisStringTemplate,
                        ObjectMapper objectMapper,
                        MediaMapper mediaMapper,
                        MediaMetaDataRepository mediaRepository,
                        MinIOService minIOService,
                        MediaSearchCacheService mediaSearchCacheService) {
        super(redisStringTemplate, objectMapper, mediaMapper, mediaRepository, minIOService, mediaSearchCacheService);
    }

    public String getOriginalVideoUrl(String userId, long videoId) {
        MediaDescription mediaDescription = getMediaDescription(userId, videoId);
        return minIOService.getObjectUrl(mediaDescription.getBucket(), mediaDescription.getKey().substring(mediaDescription.getKey().indexOf("/") + 1));
    }

    @Transactional
    public String getPreviewVideoUrl(String userId, long videoId) throws Exception {
        MediaDescription mediaDescription = getMediaDescription(userId, videoId);
        if (mediaDescription.getPreview() != null) {
            return minIOService.getObjectUrl(ContentMetaData.PREVIEW, mediaDescription.getPreview());
        }
        String cacheJobId = getCachePreviewJobId(videoId);
        addCacheVideoLastAccess(cacheJobId, null);
        String previewName = "preview_" + mediaDescription.getKey();
        MediaJobDescription jobDescription = getMediaJobDescription(userId, mediaDescription, cacheJobId, null, "preview");
        jobDescription.setPreview(previewName);
        String status = addJobToFfmpegQueue(ffmpegQueueKey, cacheJobId, "result", jobDescription);
        if (Arrays.stream(MediaJobStatus.values()).noneMatch(s -> s.name().equals(status))) {
            mediaRepository.updateMediaPreview(Long.parseLong(userId), mediaDescription.getId(), previewName);
        }
        return status;
    }

    public String getPartialVideoUrl(String userId, long videoId, Resolution res) throws Exception {
        if (res == Resolution.original)
            return getOriginalVideoUrl(userId, videoId);

        MediaDescription mediaDescription = getMediaDescription(userId, videoId);

        String cacheJobId = getCacheMediaJobId(videoId, res);
        addCacheVideoLastAccess(cacheJobId, null);
        return addJobToFfmpegQueue(ffmpegQueueKey, cacheJobId, "result", getMediaJobDescription(userId, mediaDescription, cacheJobId, res, "partial"));
    }

    public void addCacheVideoLastAccess(String videoId, Long expiry) {
        String videoLastAccessKey = "cache:lastAccess:video";
        addCacheLastAccess(videoLastAccessKey, videoId, expiry);
    }

    private String getCachePreviewJobId(long videoId) {
        return videoId + ":preview";
    }

    @Override
    protected MediaDescription getMediaDescription(String userId, long videoId) {
        MediaDescription mediaDescription = super.getMediaDescription(userId, videoId);
        if (mediaDescription.getMediaType() != MediaType.VIDEO)
            throw new IllegalArgumentException("Requested video media does not has key with id: " + videoId);
        return mediaDescription;
    }
}

