package dev.chinh.streamingservice.backend.content.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.backend.MediaMapper;
import dev.chinh.streamingservice.common.constant.MediaJobStatus;
import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import dev.chinh.streamingservice.backend.search.service.MediaSearchCacheService;
import dev.chinh.streamingservice.persistence.entity.MediaDescription;
import dev.chinh.streamingservice.persistence.repository.MediaMetaDataRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.coyote.BadRequestException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AlbumService extends MediaService {

    private final VideoService videoService;

    public AlbumService(RedisTemplate<String, String> redisStringTemplate,
                        ObjectMapper objectMapper,
                        MediaMapper mediaMapper,
                        MediaMetaDataRepository mediaRepository,
                        MinIOService minIOService,
                        MediaSearchCacheService mediaSearchCacheService,
                        VideoService videoService) {
        super(redisStringTemplate, objectMapper, mediaMapper, mediaRepository, minIOService, mediaSearchCacheService);
        this.videoService = videoService;
    }

    private final int expirySeconds = 60 * 60; // 1 hour
    private final String ffmpegQueueKey = "ffmpeg_album_stream";

    public String getAllMediaUrlInAnAlbum(Long albumId, Resolution resolution, HttpServletRequest request) throws Exception {
        String albumCreationId = getCacheMediaJobId(albumId, resolution);
        addCacheAlbumLastAccess(albumId, albumCreationId);
        MediaJobDescription mediaJobDescription = getMediaJobDescription(albumId, albumCreationId, resolution, "albumUrlList");
        mediaJobDescription.setAcceptHeader(request.getHeader("Accept"));
        return addJobToFfmpegQueue(ffmpegQueueKey, albumCreationId, mediaJobDescription);
    }

    private final String albumLastAccessKey = "cache:lastAccess:album";
    public void addCacheAlbumLastAccess(long albumId, String albumCreationId) {
        long now = System.currentTimeMillis() + expirySeconds * 1000;
        addCacheLastAccess(albumLastAccessKey, albumCreationId, now);
        addCacheLastAccess(albumLastAccessKey, getAlbumCacheHashId(albumId), now);
    }

    public void addCacheAlbumVideoLastAccess(long albumId, String albumVidCacheJobId, Resolution albumRes) {
        long now = System.currentTimeMillis() + expirySeconds * 1000;
        videoService.addCacheVideoLastAccess(albumVidCacheJobId, now);
        addCacheLastAccess(albumLastAccessKey, getCacheMediaJobId(albumId, albumRes), now);
        addCacheLastAccess(albumLastAccessKey, getAlbumCacheHashId(albumId), now);
    }

    private String getAlbumCacheHashId(long albumId) {
        return albumId + ":album";
    }

    public String processResizedAlbumImagesInBatch(long albumId, Resolution resolution, int offset, int batch,
                                                 HttpServletRequest request) throws Exception {
        if (resolution == Resolution.original)
            throw new BadRequestException("Cannot process original images in batch");

        String albumJobId = getCacheMediaJobId(albumId, resolution);
        addCacheAlbumLastAccess(albumId, albumJobId);

        Map<Object, Object> jobQueueStatus = getQueueJobStatus(albumJobId);
        if (!jobQueueStatus.isEmpty()) {
            String status = (String) jobQueueStatus.get("status");
            if (status.equals(MediaJobStatus.COMPLETED.name())) {
                List<Integer> offsetPair = objectMapper.readValue(jobQueueStatus.get("offset").toString(), new TypeReference<>() {});
                int previousProcessedOffset = offsetPair.getFirst();
                int size = offsetPair.get(1);
                if (previousProcessedOffset >= offset + batch) {
                    return String.valueOf(previousProcessedOffset);
                }
                if (offset >= size) {
                    return String.valueOf(size);
                }
            } else if (status.equals(MediaJobStatus.RUNNING.name()) || status.equals(MediaJobStatus.PROCESSING.name())) {
                return MediaJobStatus.PROCESSING.name();
            }
        }

        MediaJobDescription jobDescription = getMediaJobDescription(albumId, albumJobId, resolution, "checkResized");
        jobDescription.setOffset(offset);
        jobDescription.setBatch(batch);
        jobDescription.setAcceptHeader(request.getHeader("Accept"));
        addJobToQueue(ffmpegQueueKey, jobDescription);
        updateQueueJobStatus(albumJobId, MediaJobStatus.PROCESSING.name());
        return MediaJobStatus.PROCESSING.name();
    }

    public String getAlbumVidCacheJobIdString(long albumId, int vidNum, Resolution resolution) {
        return albumId + ":" + vidNum + ":" + resolution;
    }

    public String getAlbumPartialVideoUrl(long albumId, Resolution albumRes, int vidNum, Resolution res,
                                          HttpServletRequest request) throws Exception {
        final String albumVidCacheJobId = getAlbumVidCacheJobIdString(albumId, vidNum, res);

        MediaJobDescription jobDescription = getMediaJobDescription(albumId, albumVidCacheJobId, albumRes, "albumVideoUrl");
        jobDescription.setVidNum(vidNum);
        jobDescription.setVidResolution(res);
        jobDescription.setAcceptHeader(request.getHeader("Accept"));
        addCacheAlbumVideoLastAccess(albumId, albumVidCacheJobId, albumRes);
        return addJobToFfmpegQueue(ffmpegQueueKey, albumVidCacheJobId, jobDescription);
    }

    @Override
    protected MediaDescription getMediaDescription(long albumId) {
        MediaDescription mediaDescription = super.getMediaDescription(albumId);
        if (mediaDescription.hasKey())
            throw new IllegalArgumentException("Not an album, individual media found: " + albumId);
        return mediaDescription;
    }

    public MediaDescription getMediaDescriptionGeneral(long mediaId) {
        return super.getMediaDescription(mediaId);
    }
}
