package dev.chinh.streamingservice.backend.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.backend.MediaMapper;
import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.constant.MediaJobStatus;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.backend.search.service.MediaSearchCacheService;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import dev.chinh.streamingservice.persistence.entity.MediaDescription;
import dev.chinh.streamingservice.persistence.repository.MediaMetaDataRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.UUID;

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

    public String getOriginalVideoUrl(long videoId) {
        MediaDescription mediaDescription = getMediaDescription(videoId);
        return minIOService.getRedirectObjectUrl(mediaDescription.getBucket(), mediaDescription.getPath());
    }

    @Transactional
    public String getPreviewVideoUrl(long videoId) throws Exception {
        MediaDescription mediaDescription = getMediaDescription(videoId);
        if (mediaDescription.getPreview() != null) {
            return minIOService.getObjectUrl(ContentMetaData.PREVIEW, mediaDescription.getPreview());
        }
        String cacheJobId = getCachePreviewJobId(videoId);
        addCacheVideoLastAccess(cacheJobId, null);
        String previewName = OSUtil.normalizePath(mediaDescription.getParentPath(), (mediaDescription.getId() + "_" + UUID.randomUUID() + "_preview.mp4"));
        MediaJobDescription jobDescription = getMediaJobDescription(videoId, cacheJobId, null, "preview");
        jobDescription.setPreview(previewName);
        String status = addJobToFfmpegQueue(ffmpegQueueKey, cacheJobId, jobDescription);
        if (Arrays.stream(MediaJobStatus.values()).noneMatch(s -> s.name().equals(status))) {
            mediaRepository.updateMediaPreview(mediaDescription.getId(), previewName);
        }
        return status;
    }

    public String getPartialVideoUrl(long videoId, Resolution res) throws Exception {
        if (res == Resolution.original)
            return getOriginalVideoUrl(videoId);

        String cacheJobId = getCacheMediaJobId(videoId, res);
        addCacheVideoLastAccess(cacheJobId, null);
        return addJobToFfmpegQueue(ffmpegQueueKey, cacheJobId, getMediaJobDescription(videoId, cacheJobId, res, "partial"));
    }

    public void addCacheVideoLastAccess(String videoId, Long expiry) {
        String videoLastAccessKey = "cache:lastAccess:video";
        addCacheLastAccess(videoLastAccessKey, videoId, expiry);
    }

    private String getCachePreviewJobId(long videoId) {
        return videoId + ":preview";
    }

    @Override
    protected MediaDescription getMediaDescription(long videoId) {
        MediaDescription mediaDescription = super.getMediaDescription(videoId);
        if (mediaDescription.getMediaType() != MediaType.VIDEO)
            throw new IllegalArgumentException("Requested video media does not has key with id: " + videoId);
        return mediaDescription;
    }
}

