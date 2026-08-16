package dev.chinh.streamingservice.workers.task.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import dev.chinh.streamingservice.mediapersistence.entity.MediaDescription;
import dev.chinh.streamingservice.mediapersistence.repository.MediaMetaDataRepository;
import dev.chinh.streamingservice.workers.MediaMapper;
import dev.chinh.streamingservice.workers.service.MinIOService;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskVideoService extends TaskMediaService {

    public final String ffmpegQueueKey = ContentMetaData.FFMPEG_VIDEO_QUEUE_KEY;
    private final MinIOService minIOService;

    public TaskVideoService(StringRedisTemplate redisTemplate,
                            RedissonClient redissonClient,
                            ObjectMapper objectMapper,
                            MediaMapper mediaMapper,
                            MediaMetaDataRepository mediaRepository,
                            MinIOService minIOService) {
        super(redisTemplate, redissonClient, objectMapper, mediaMapper, mediaRepository);
        this.minIOService = minIOService;
    }

    public JobStatus getOriginalVideoUrl(String userId, long videoId) {
        MediaDescription mediaDescription = getMediaDescription(userId, videoId);
        String url = minIOService.getObjectUrl(mediaDescription.getBucket(), ContentMetaData.removeUserIdDirFromObjectKey(userId, mediaDescription.getKey()));
        return new JobStatus(getCacheMediaJobId(videoId, Resolution.original), url);
    }

    @Transactional
    public JobStatus getPreviewVideoUrl(String userId, long videoId) throws Exception {
        MediaDescription mediaDescription = getMediaDescription(userId, videoId);
        String jobId = getCachePreviewJobId(videoId);
        if (mediaDescription.getPreview() != null) {
            return new JobStatus(jobId, minIOService.getObjectUrl(ContentMetaData.PREVIEW, ContentMetaData.removeUserIdDirFromObjectKey(userId, mediaDescription.getPreview())));
        }
        addCacheVideoLastAccess(jobId, null);
        String previewName = mediaDescription.getUserId() + "/preview_"  + ContentMetaData.removeUserIdDirFromObjectKey(String.valueOf(mediaDescription.getUserId()), mediaDescription.getKey());
        MediaJobDescription jobDescription = getMediaJobDescription(userId, mediaDescription, jobId, null, "preview");
        jobDescription.setPreview(previewName);
        return new JobStatus(jobId, addJobToFfmpegQueue(ffmpegQueueKey, jobId, "result", jobDescription));
    }

    public JobStatus getPartialVideoUrl(String userId, long videoId, Resolution res) throws Exception {
        String jobId = getCacheMediaJobId(videoId, res);
        if (res == Resolution.original)
            return getOriginalVideoUrl(userId, videoId);

        MediaDescription mediaDescription = getMediaDescription(userId, videoId);

        addCacheVideoLastAccess(jobId, null);
        return new JobStatus(jobId, addJobToFfmpegQueue(ffmpegQueueKey, jobId, "result", getMediaJobDescription(userId, mediaDescription, jobId, res, "partial")));
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

