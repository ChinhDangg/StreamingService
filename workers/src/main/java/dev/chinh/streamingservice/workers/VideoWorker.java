package dev.chinh.streamingservice.workers;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.service.WorkerRedisService;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import dev.chinh.streamingservice.service.VideoService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope("prototype") // new instance for each request
public class VideoWorker extends Worker {

    private final VideoService videoService;

    public static final String STREAM = "ffmpeg_video_stream";
    public static final String GROUP = "video_workers";
    public static final String TOKEN_KEY = "ffmpeg_video_tokens";

    public VideoWorker(WorkerRedisService workerRedisService,
                       ObjectMapper objectMapper,
                       VideoService videoService) {
        super(workerRedisService, objectMapper);
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
    public void performJob(MediaJobDescription mediaJobDescription) throws Exception{
        String url;
        if ("preview".equals(mediaJobDescription.getJobType())) {
            url = videoService.getPreviewVideoUrl(mediaJobDescription);
        } else if ("partial".equals(mediaJobDescription.getJobType())) {
            url = videoService.getPartialVideoUrl(
                    mediaJobDescription,
                    mediaJobDescription.getResolution()
            );
        } else {
            throw new IllegalArgumentException("Unknown job type");
        }
        workerRedisService.addResultToStatus(mediaJobDescription.getWorkId(), "result", url);
    }
}
