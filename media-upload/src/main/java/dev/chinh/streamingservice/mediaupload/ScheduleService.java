package dev.chinh.streamingservice.mediaupload;

import dev.chinh.streamingservice.mediaupload.upload.service.MediaUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@EnableScheduling
@RequiredArgsConstructor
public class ScheduleService {

    private final MediaUploadService mediaUploadService;

    @Scheduled(fixedDelay = 15 * 60 * 1000) // 15 mins
    public void scheduled15mins() {
        removeExpiredUploadSession();
    }

    private void removeExpiredUploadSession() {
        long now = System.currentTimeMillis();
        Set<ZSetOperations.TypedTuple<String>> lastAccessSessions = mediaUploadService.getAllUploadSessionCacheLastAccess(now);
        for (ZSetOperations.TypedTuple<String> session : lastAccessSessions) {
            if (now - session.getScore() < 300_000) {
                break;
            }

            String sessionId = Objects.requireNonNull(session.getValue());
            Map<Object, Object> objectMap = mediaUploadService.getUploadSessionObjects(sessionId);
            if (objectMap.isEmpty()) continue;

            mediaUploadService.removeCacheMediaSessionRequest(sessionId);
            mediaUploadService.removeUploadSessionCacheLastAccess(sessionId);

            Set<String> seen = new HashSet<>();
            for (Object value : objectMap.values()) {
                String objectName = value.toString();
                if (seen.add(objectName)) {
                    try {
                        mediaUploadService.removeUploadObject(objectName);
                    } catch (Exception e) {
                        System.out.println("Failed to remove object: " + objectName);
                    }
                }
            }
        }
    }
}
