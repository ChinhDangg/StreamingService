package dev.chinh.streamingservice.workers;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.service.ThumbnailService;
import dev.chinh.streamingservice.service.WorkerRedisService;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import dev.chinh.streamingservice.service.VideoService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope("prototype") // new instance for each request
public class VideoWorker extends Worker {

    private final VideoService videoService;
    private final ThumbnailService thumbnailService;

    public static final String STREAM = "ffmpeg_video_stream";
    public static final String GROUP = "video_workers";
    public static final String TOKEN_KEY = "ffmpeg_video_tokens";

    public VideoWorker(WorkerRedisService workerRedisService,
                       ObjectMapper objectMapper,
                       VideoService videoService,
                       ThumbnailService thumbnailService) {
        super(workerRedisService, objectMapper);
        this.videoService = videoService;
        this.thumbnailService = thumbnailService;
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
    public void performJob(MediaJobDescription mediaJobDescription) throws Exception{
        String url = switch (mediaJobDescription.getJobType()) {
            case "preview" -> videoService.getPreviewVideoUrl(mediaJobDescription);
            case "partial" -> videoService.getPartialVideoUrl(mediaJobDescription, mediaJobDescription.getResolution());
            case "videoThumbnail" -> thumbnailService.generateThumbnailFromVideo(mediaJobDescription);
            case null, default -> throw new IllegalArgumentException("Unknown job type");
        };
        workerRedisService.addResultToStatus(mediaJobDescription.getWorkId(), "result", url);
    }
}
