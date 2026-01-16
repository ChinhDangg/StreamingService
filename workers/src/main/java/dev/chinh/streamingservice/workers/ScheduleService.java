package dev.chinh.streamingservice.workers;

import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.constant.MediaJobStatus;
import dev.chinh.streamingservice.workers.service.AlbumService;
import dev.chinh.streamingservice.workers.service.ThumbnailService;
import dev.chinh.streamingservice.workers.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

@Service
@EnableScheduling
@RequiredArgsConstructor
public class ScheduleService {

    private final VideoService videoService;
    private final AlbumService albumService;
    private final ThumbnailService thumbnailService;

    private final RedisTemplate<String, String> queueRedisTemplate;

    @Scheduled(fixedRate = 60_000, initialDelay = 60_000)
    public void scheduled() {
        stopNonViewingVideoRunningJob();
        try {
            OSUtil.refreshUsableMemory();
        } catch (Exception e) {
            System.out.println("Failed to refresh memory usage");
        }
    }

    @Scheduled(fixedDelay = 15 * 60 * 1000) // 15 mins
    public void scheduled15mins() {
        removeAlbumCreationInfo();
        cleanThumbnails();

        trimJobStreams();
    }


    private void trimJobStreams() {
        queueRedisTemplate.execute((RedisCallback<Void>) conn -> {
            var streams = new String[] {
                    VideoWorker.STREAM,
                    AlbumWorker.STREAM
            };
            // RedisConnection -> RedisCommandsProvider -> streamCommands()
            var streamCommands = conn.streamCommands();
            for (String stream : streams) {
                streamCommands.xTrim(stream.getBytes(), 100_000, true);
            }
            return null;
        });
    }

    private void stopNonViewingVideoRunningJob() {
        Set<String> runningVideoJobs = videoService.getCacheRunningJobs(System.currentTimeMillis());

        for (String runningVideoJob : runningVideoJobs) {

            String videoJobId = runningVideoJob;
            Double lastAccess = videoService.getCacheVideoLastAccess(videoJobId);
            long millisPassed = (lastAccess == null) ? 130_000 : (long) (System.currentTimeMillis() - lastAccess);
            if (millisPassed < 120_000) {
                break; // sorted so any after is the same
            }

            var cachedJobStatus = videoService.getVideoJobStatusInfo(videoJobId);
            if (!cachedJobStatus.get("status").toString().equals(MediaJobStatus.RUNNING.name())) {
                // running job probably completed or removed for space - remove running cache
                videoService.removeCacheRunningJob(videoJobId);
                videoService.removeCacheVideoLastAccess(videoJobId);
                continue;
            }
            String jobId = cachedJobStatus.get("jobId").toString(); // UUID
            try {
                videoService.stopFfmpegJob(videoJobId, jobId);
            } catch (Exception e) {
                System.out.println("Failed to stop ffmpeg job: " + jobId);
            }
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
            try {
                OSUtil.deleteForceMemoryDirectory(path);
            } catch (IOException e) {
                System.err.println("Failed to delete memory path: " + path);
            }
            thumbnailService.removeThumbnailLastAccess(thumbnailFileName);
        }
    }
}
