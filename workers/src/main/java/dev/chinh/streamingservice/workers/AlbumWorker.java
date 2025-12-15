package dev.chinh.streamingservice.workers;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.service.WorkerRedisService;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import dev.chinh.streamingservice.service.AlbumService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Scope("prototype")
public class AlbumWorker extends Worker {

    private final AlbumService albumService;

    public static final String STREAM = "ffmpeg_album_stream";
    public static final String GROUP = "album_workers";
    public static final String TOKEN_KEY = "ffmpeg_album_tokens";

    public AlbumWorker(WorkerRedisService workerRedisService, ObjectMapper objectMapper,
                       AlbumService albumService) {
        super(workerRedisService, objectMapper);
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
    public void performJob(MediaJobDescription description) throws Exception{
        switch (description.getJobType()) {
            case "albumUrlList" -> {
                var mediaUrlList = albumService.getAllMediaUrlInAnAlbum(description);
                String mediaUrlListString = objectMapper.writeValueAsString(mediaUrlList);
                workerRedisService.addResultToStatus(description.getWorkId(), "result", mediaUrlListString);
                workerRedisService.addResultToStatus(description.getWorkId(), "offset",
                        objectMapper.writeValueAsString(List.of(description.getOffset() + description.getBatch(), mediaUrlList.size())));
            }
            case "checkResized" -> {
                String offset = objectMapper.writeValueAsString(albumService.processResizedAlbumImagesInBatch(description));
                workerRedisService.addResultToStatus(description.getWorkId(), "offset", offset);
            }
            case "albumVideoUrl" -> {
                String videoPartialUrl = albumService.getAlbumPartialVideoUrl(description);
                workerRedisService.addResultToStatus(description.getWorkId(), "result", videoPartialUrl);
            }
        }
    }
}
