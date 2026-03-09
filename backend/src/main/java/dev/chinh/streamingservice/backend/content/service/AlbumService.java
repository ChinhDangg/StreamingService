package dev.chinh.streamingservice.backend.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.backend.MediaMapper;
import dev.chinh.streamingservice.common.constant.MediaJobStatus;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import dev.chinh.streamingservice.backend.search.service.MediaSearchCacheService;
import dev.chinh.streamingservice.mediapersistence.entity.MediaDescription;
import dev.chinh.streamingservice.mediapersistence.repository.MediaMetaDataRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

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

    private final String albumLastAccessKey = "cache:lastAccess:album";
    public void addCacheAlbumLastAccess(String albumJobId) {
        long now = System.currentTimeMillis() + expirySeconds * 1000;
        addCacheLastAccess(albumLastAccessKey, albumJobId, now);
    }

    public void addCacheAlbumVideoLastAccess(long albumId, String albumVidJobId, Resolution albumRes) {
        long now = System.currentTimeMillis() + expirySeconds * 1000;
        videoService.addCacheVideoLastAccess(albumVidJobId, now);
        addCacheLastAccess(albumLastAccessKey, getCacheMediaJobId(albumId, albumRes), now);
    }

    public String getAlbumContent(long albumId, Resolution resolution, int page, int batch,
                                  HttpServletRequest request) throws Exception {
        MediaDescription album = getMediaDescription(albumId);

        String albumJobId = getCacheMediaJobId(albumId, resolution);
        addCacheAlbumLastAccess(albumJobId);

        int maxPage = (int) Math.ceil(album.getLength() / (double) batch);
        if (page > maxPage)
            page = maxPage;

        Object result = getQueueJobResult(albumJobId, "page::" + page);
        if (result != null) {
            try {
                MediaJobStatus status = MediaJobStatus.valueOf(result.toString());
                if (status == MediaJobStatus.PROCESSING)
                    return status.name();
            } catch (Exception ignored) {
                return result.toString();
            }
        }

        MediaJobDescription jobDescription = getMediaJobDescription(album, albumJobId, resolution, "albumUrlList");
        jobDescription.setOffset(page);
        jobDescription.setBatch(batch);
        jobDescription.setWidth(album.getWidth());
        jobDescription.setHeight(album.getHeight());
        jobDescription.setAcceptHeader(request.getHeader("Accept"));
        addJobToQueue(ffmpegQueueKey, jobDescription);
        updateQueueJobStatus(albumJobId, MediaJobStatus.PROCESSING.name(), "page::" + page);
        return MediaJobStatus.PROCESSING.name();
    }

    public String getAlbumVidCacheJobIdString(long albumId, String objectName, Resolution resolution) {
        return albumId + ":" + resolution + ":" + objectName;
    }

    public String getAlbumPartialVideoUrl(long albumId, Resolution albumRes, String objectName, Resolution res,
                                          HttpServletRequest request) throws Exception {
        MediaDescription album = getMediaDescription(albumId);
        final String albumVidJobId = getAlbumVidCacheJobIdString(albumId, objectName, res);

        MediaJobDescription jobDescription = getMediaJobDescription(album, albumVidJobId, albumRes, "albumVideoUrl");
        jobDescription.setKey(objectName);
        jobDescription.setVidResolution(res);
        jobDescription.setAcceptHeader(request.getHeader("Accept"));
        addCacheAlbumVideoLastAccess(albumId, albumVidJobId, albumRes);
        return addJobToFfmpegQueue(ffmpegQueueKey, albumVidJobId, "result", jobDescription);
    }

    @Override
    protected MediaDescription getMediaDescription(long albumId) {
        MediaDescription mediaDescription = super.getMediaDescription(albumId);
        if (mediaDescription.getMediaType() != MediaType.ALBUM)
            throw new IllegalArgumentException("Not an album, individual media found: " + albumId);
        return mediaDescription;
    }

    public MediaDescription getMediaDescriptionGeneral(long mediaId) {
        return super.getMediaDescription(mediaId);
    }
}
