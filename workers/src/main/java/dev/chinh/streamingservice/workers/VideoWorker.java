package dev.chinh.streamingservice.workers;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.workers.service.WorkerRedisService;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import dev.chinh.streamingservice.workers.service.VideoService;
import org.springframework.context.annotation.Scope;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Scope("prototype") // new instance for each request
public class VideoWorker extends Worker {

    private final VideoService videoService;

    public static final String STREAM = "ffmpeg_video_stream";
    public static final String GROUP = "video_workers";
    public static final String TOKEN_KEY = "ffmpeg_video_tokens";
    public static final String DLQ_STREAM = "ffmpeg_video_dlq";

    public VideoWorker(WorkerRedisService workerRedisService,
                       RedisTemplate<String, String> queueRedisTemplate,
                       ObjectMapper objectMapper,
                       VideoService videoService) {
        super(workerRedisService, queueRedisTemplate, objectMapper);
        this.videoService = videoService;
    }

    @Override
    protected String streamKey() {
        return STREAM;
    }

    @Override
    protected String groupName() {
        return GROUP;
    }

    @Override
    protected String tokenKey() {
        return TOKEN_KEY;
    }

    @Override
    protected String streamKeyDLQ() {
        return DLQ_STREAM;
    }

    @Override
    public void performJob(MediaJobDescription mediaJobDescription) {
        videoService.handleJob(TOKEN_KEY, mediaJobDescription);
    }
}
