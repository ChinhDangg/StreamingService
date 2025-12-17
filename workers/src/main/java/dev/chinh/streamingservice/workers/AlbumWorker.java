package dev.chinh.streamingservice.workers;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.service.WorkerRedisService;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import dev.chinh.streamingservice.service.AlbumService;
import org.springframework.context.annotation.Scope;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Scope("prototype")
public class AlbumWorker extends Worker {

    private final AlbumService albumService;

    public static final String STREAM = "ffmpeg_album_stream";
    public static final String GROUP = "album_workers";
    public static final String TOKEN_KEY = "ffmpeg_album_tokens";
    public static final String DLQ_STREAM = "ffmpeg_album_dlq";

    public AlbumWorker(WorkerRedisService workerRedisService,
                       RedisTemplate<String, String> queueRedisTemplate,
                       ObjectMapper objectMapper,
                       AlbumService albumService) {
        super(workerRedisService, queueRedisTemplate, objectMapper);
        this.albumService = albumService;
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
    public void performJob(MediaJobDescription description) {
        albumService.handleJob(TOKEN_KEY, description);
    }
}
