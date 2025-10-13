package dev.chinh.streamingservice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.content.constant.MediaJobStatus;
import dev.chinh.streamingservice.content.constant.Resolution;
import dev.chinh.streamingservice.content.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@EnableScheduling
@RequiredArgsConstructor
public class ScheduleService {

    private final VideoService videoService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private final String nginxBaseUrl = "http://localhost";

    @Scheduled(fixedRate = 60_000)
    public void scheduled() throws Exception {
        stopNonViewingPartialVideoRunningJob(60_000);
    }

    private void stopNonViewingPartialVideoRunningJob(long expiryTimeInMillisecond) throws Exception {
        Map<String, Double> activeVideos = getActiveWatchers();
        boolean cleaned = false;
        for (Map.Entry<String, Double> activeVideo : activeVideos.entrySet()) {
            String[] keyPart = activeVideo.getKey().split("::");
            String videoId = keyPart[0];                             // 12345
            Resolution resolution = Resolution.valueOf(keyPart[1]);  // p720
            // partial:12345::p720
            String partialJobId = videoService.getCachePartialJobId(Long.parseLong(videoId), resolution);
            System.out.println(partialJobId);

            long millisPassed = (long) (System.currentTimeMillis() - (activeVideo.getValue() * 1000));
            System.out.println(millisPassed);
            if (millisPassed < expiryTimeInMillisecond) {
                continue;
            }
            cleaned = true;

            var cachedJobStatus = videoService.getCacheTempVideoProcess(partialJobId);
            if (cachedJobStatus.get("status") != MediaJobStatus.RUNNING) {
                continue;
            }
            String jobId = cachedJobStatus.get("jobId").toString(); // UUID
            videoService.stopFfmpegJob(jobId);
            videoService.addCacheTempVideoProcess(partialJobId, null, MediaJobStatus.STOPPED);
        }
        if (cleaned)
            System.out.println(cleanupInactiveWatchers((expiryTimeInMillisecond / 1000) + 2));
    }

    /**
     * Get all currently active watchers from Nginx.
     * Example JSON: { "12345::p720": 1728598143.26 }
     */
    private Map<String, Double> getActiveWatchers() {
        String url = nginxBaseUrl + "/internal/watchers";
        try {
            String json = restTemplate.getForObject(url, String.class);
            if (json == null || json.isEmpty()) return Collections.emptyMap();

            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            System.out.println("Nginx failed get active watchers: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Request Nginx to cleanup inactive watchers (older than timeout seconds)
     * Returns list of keys removed.
     */
    private List<String> cleanupInactiveWatchers(long timeoutSeconds) {
        String url = String.format("%s/internal/cleanup_watchers?timeout=%d", nginxBaseUrl, timeoutSeconds);
        try {
            String json = restTemplate.getForObject(url, String.class);
            if (json == null || json.isEmpty()) return Collections.emptyList();

            Map<String, Object> result = objectMapper.readValue(json, new TypeReference<>() {});
            Object removed = result.get("removed");
            if (removed instanceof List) {
                //noinspection unchecked
                return (List<String>) removed;
            }
            return Collections.emptyList();
        } catch (Exception e) {
            System.out.println("Nginx clean up failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }
}
