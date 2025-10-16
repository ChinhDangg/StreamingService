package dev.chinh.streamingservice;

import dev.chinh.streamingservice.content.constant.MediaJobStatus;
import dev.chinh.streamingservice.content.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@EnableScheduling
@RequiredArgsConstructor
public class ScheduleService {

    private final VideoService videoService;

    @Scheduled(fixedRate = 60_000)
    public void scheduled() throws Exception {
        stopNonViewingPartialVideoRunningJob();
    }

    private void stopNonViewingPartialVideoRunningJob() throws Exception {
        Set<Object> runningVideoJobs = videoService.getCacheRunningJobs(System.currentTimeMillis());

        for (Object runningVideoJob : runningVideoJobs) {

            String partialJobId = (String) runningVideoJob;
            double lastAccess = videoService.getCacheLastAccess(partialJobId);
            long millisPassed = (long) (System.currentTimeMillis() - lastAccess);
            if (millisPassed < 60_000) {
                continue;
            }

            var cachedJobStatus = videoService.getCacheTempVideoJobStatus(partialJobId);
            if (cachedJobStatus.get("status") != MediaJobStatus.RUNNING) {
                // running job probably completed or removed for space - remove running cache
                videoService.removeCacheRunningJob(partialJobId);
                continue;
            }
            String jobId = cachedJobStatus.get("jobId").toString(); // UUID
            videoService.stopFfmpegJob(jobId);
            videoService.addCacheTempVideoJobStatus(partialJobId, null, MediaJobStatus.STOPPED);
        }
    }
}
