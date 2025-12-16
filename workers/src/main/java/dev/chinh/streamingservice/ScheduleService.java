package dev.chinh.streamingservice;

import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.constant.MediaJobStatus;
import dev.chinh.streamingservice.service.AlbumService;
import dev.chinh.streamingservice.service.MediaUploadService;
import dev.chinh.streamingservice.service.ThumbnailService;
import dev.chinh.streamingservice.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
//@EnableScheduling
@RequiredArgsConstructor
public class ScheduleService {

    private final VideoService videoService;
    private final AlbumService albumService;
    private final ThumbnailService thumbnailService;
    private final MediaUploadService mediaUploadService;

    @Scheduled(fixedRate = 60_000, initialDelay = 60_000)
    public void scheduled() throws Exception {
        stopNonViewingVideoRunningJob();
        removeAlbumCreationInfo();
        cleanThumbnails();
        removeExpiredUploadSession();
        OSUtil.refreshUsableMemory();
    }

    private void stopNonViewingVideoRunningJob() throws Exception {
        Set<String> runningVideoJobs = videoService.getCacheRunningJobs(System.currentTimeMillis());

        for (String runningVideoJob : runningVideoJobs) {

            String videoJobId = runningVideoJob;
            double lastAccess = videoService.getCacheVideoLastAccess(videoJobId);
            long millisPassed = (long) (System.currentTimeMillis() - lastAccess);
            if (millisPassed < 60_000) {
                break; // sorted so any after is the same
            }

            var cachedJobStatus = videoService.getVideoJobStatusInfo(videoJobId);
            if (!cachedJobStatus.get("status").toString().equals(MediaJobStatus.RUNNING.name())) {
                // running job probably completed or removed for space - remove running cache
                videoService.removeCacheRunningJob(videoJobId);
                continue;
            }
            String jobId = cachedJobStatus.get("jobId").toString(); // UUID
            videoService.stopFfmpegJob(jobId);
            videoService.addCacheVideoJobStatus(videoJobId, null, null, MediaJobStatus.STOPPED);
        }
    }

    private void removeAlbumCreationInfo() {
        long now = System.currentTimeMillis();
        Set<ZSetOperations.TypedTuple<String>> lastAccessMediaJob = albumService.getAllAlbumCacheLastAccess(now);
        for (ZSetOperations.TypedTuple<String> albumJob : lastAccessMediaJob) {
            if (now - albumJob.getScore() < 60_000) {
                break;
            }

            String albumJobId = Objects.requireNonNull(albumJob.getValue());
            if (!albumJobId.endsWith(":album"))
                continue;

            albumService.removeAlbumCacheLastAccess(albumJobId);
            albumService.removeCacheAlbumCreationInfo(albumJobId);
        }
    }

    private void cleanThumbnails() {
        long now = System.currentTimeMillis();
        Set<ZSetOperations.TypedTuple<String>> lastAccessThumbnails = thumbnailService.getAllThumbnailCacheLastAccess(now);
        for (ZSetOperations.TypedTuple<String> thumbnail : lastAccessThumbnails) {
            long millisPassed = (long) (System.currentTimeMillis() - thumbnail.getScore());
            if (millisPassed < 60_000) {
                break;
            }

            String thumbnailFileName = thumbnail.getValue();

            System.out.println("Removing: " + thumbnailFileName);
            String path = ThumbnailService.getThumbnailParentPath() + "/" + thumbnailFileName;
            OSUtil.deleteForceMemoryDirectory(path);
            thumbnailService.removeThumbnailLastAccess(thumbnailFileName);
        }
    }

    private void removeExpiredUploadSession() throws Exception {
        long now = System.currentTimeMillis();
        Set<ZSetOperations.TypedTuple<String>> lastAccessSessions = mediaUploadService.getAllUploadSessionCacheLastAccess(now);
        for (ZSetOperations.TypedTuple<String> session : lastAccessSessions) {
            if (now - session.getScore() < 60_000) {
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
                    mediaUploadService.removeUploadObject(objectName);
                }
            }
        }
    }
}
