package dev.chinh.streamingservice;

import dev.chinh.streamingservice.content.constant.MediaJobStatus;
import dev.chinh.streamingservice.content.service.VideoService;
import dev.chinh.streamingservice.data.service.ThumbnailService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
//@EnableScheduling
@RequiredArgsConstructor
public class ScheduleService {

    private final VideoService videoService;
    private final ThumbnailService thumbnailService;

    @Scheduled(fixedRate = 60_000, initialDelay = 60_000)
    public void scheduled() throws Exception {
        stopNonViewingVideoRunningJob();
        cleanThumbnails();
        Thread.sleep(1_000);
        OSUtil.refreshUsableMemory();
    }

    private void stopNonViewingVideoRunningJob() throws Exception {
        Set<Object> runningVideoJobs = videoService.getCacheRunningJobs(System.currentTimeMillis());

        for (Object runningVideoJob : runningVideoJobs) {

            String videoJobId = (String) runningVideoJob;
            double lastAccess = videoService.getCacheVideoLastAccess(videoJobId);
            long millisPassed = (long) (System.currentTimeMillis() - lastAccess);
            if (millisPassed < 60_000) {
                continue;
            }

            var cachedJobStatus = videoService.getVideoJobStatusInfo(videoJobId);
            if (cachedJobStatus.get("status") != MediaJobStatus.RUNNING) {
                // running job probably completed or removed for space - remove running cache
                videoService.removeCacheRunningJob(videoJobId);
                continue;
            }
            String jobId = cachedJobStatus.get("jobId").toString(); // UUID
            videoService.stopFfmpegJob(jobId);
            videoService.addCacheVideoJobStatus(videoJobId, null, null, MediaJobStatus.STOPPED);
        }
    }

    private void cleanThumbnails() {
        long now = System.currentTimeMillis();
        Set<ZSetOperations.TypedTuple<Object>> lastAccessThumbnails = thumbnailService.getAllThumbnailCacheLastAccess(now);
        for (ZSetOperations.TypedTuple<Object> thumbnail : lastAccessThumbnails) {
            long millisPassed = (long) (System.currentTimeMillis() - thumbnail.getScore());
            if (millisPassed < 60_000) {
                break;
            }

            String thumbnailFileName = (String) thumbnail.getValue();

            String path = ThumbnailService.getThumbnailParentPath() + "/" + thumbnailFileName;
            OSUtil.deleteForceMemoryDirectory(path);
            thumbnailService.removeThumbnailLastAccess(thumbnailFileName);
        }
    }
}
