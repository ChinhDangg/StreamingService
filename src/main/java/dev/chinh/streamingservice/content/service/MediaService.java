package dev.chinh.streamingservice.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.data.MediaMetaDataRepository;
import dev.chinh.streamingservice.data.entity.MediaDescription;
import dev.chinh.streamingservice.data.entity.MediaMetaData;
import dev.chinh.streamingservice.search.data.MediaSearchItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;

@RequiredArgsConstructor
public abstract class MediaService {

    protected final RedisTemplate<String, Object> redisTemplate;
    protected final MinIOService minIOService;
    private final ObjectMapper objectMapper;
    private final MediaMetaDataRepository mediaRepository;

    protected MediaDescription getMediaDescription(long mediaId) {
        MediaDescription mediaDescription  = getCachedMediaSearchItem(String.valueOf(mediaId));
        if (mediaDescription == null)
            mediaDescription = findMediaMetaDataAllInfo(mediaId);
        return mediaDescription;
    }

    protected MediaSearchItem getCachedMediaSearchItem(String id) {
        return objectMapper.convertValue(redisTemplate.opsForValue().get("media::" + id), MediaSearchItem.class);
    }

    protected MediaMetaData findMediaMetaDataAllInfo(long id) {
        MediaMetaData mediaMetaData = mediaRepository.findByIdWithAllInfo(id).orElseThrow(() ->
                new IllegalArgumentException("Media not found with id " + id));
        cacheMediaSearchItem(objectMapper.convertValue(mediaMetaData, MediaSearchItem.class));
        return mediaMetaData;
    }

    private void cacheMediaSearchItem(MediaSearchItem item) {
        String id = "media::" + item.getId();
        redisTemplate.opsForValue().set(id, item, Duration.ofHours(1));
    }

    protected String runAndLog(String[] cmd) throws Exception {
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String line, lastLine = null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            while ((line = br.readLine()) != null) {
                //System.out.println("[ffmpeg] " + line);
                lastLine = line;
            }
        }
        int exit = process.waitFor();
        System.out.println("ffmpeg exited with code " + exit);
        if (exit != 0) {
            throw new RuntimeException("Command failed with code " + exit);
        }
        return lastLine;
    }
}
