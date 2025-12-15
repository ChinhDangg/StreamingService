package dev.chinh.streamingservice.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.MediaMapper;
import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.data.repository.MediaMetaDataRepository;
import dev.chinh.streamingservice.data.entity.MediaDescription;
import dev.chinh.streamingservice.data.service.MediaMetadataService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;

@Service
public class VideoService extends MediaService {

    private final String ffmpegQueueKey = "ffmpeg_video_stream";

    public VideoService(RedisTemplate<String, String> redisStringTemplate,
                        RedisTemplate<String, Object> redisTemplate,
                        ObjectMapper objectMapper,
                        MediaMapper mediaMapper,
                        MediaMetaDataRepository mediaRepository,
                        MinIOService minIOService,
                        MediaMetadataService mediaMetadataService) {
        super(redisStringTemplate, redisTemplate, objectMapper, mediaMapper, mediaRepository, minIOService, mediaMetadataService);
    }

    public String getOriginalVideoUrl(long videoId) throws Exception {
        MediaDescription mediaDescription = getMediaDescription(videoId);
        int extraExpirySeconds = 30 * 60;
        return minIOService.getSignedUrlForHostNginx(mediaDescription.getBucket(), mediaDescription.getPath(),
                mediaDescription.getLength() + extraExpirySeconds); // video duration + 30 minutes extra
    }

    public String getPreviewVideoUrl(long videoId) throws Exception {
        String cacheJobId = getCachePreviewJobId(videoId);
        addCacheVideoLastAccess(cacheJobId, null);
        return addJobToFfmpegQueue(ffmpegQueueKey, cacheJobId, getMediaJobDescription(videoId, cacheJobId, null, "preview"));
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
        if (!mediaDescription.hasKey())
            throw new IllegalArgumentException("Requested video media does not has key with id: " + videoId);
        return mediaDescription;
    }
}

