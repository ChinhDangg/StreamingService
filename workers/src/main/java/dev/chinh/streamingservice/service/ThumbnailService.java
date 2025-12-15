package dev.chinh.streamingservice.service;

import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private final RedisTemplate<String, String> queueRedisTemplate;
    private final MinIOService minIOService;

    public static final String thumbnailBucket = "thumbnail";
    public static final Resolution thumbnailResolution = Resolution.p360;
    private static final String diskDir = "disk";

    public Set<ZSetOperations.TypedTuple<String>> getAllThumbnailCacheLastAccess(long max) {
        return queueRedisTemplate.opsForZSet()
                .rangeByScoreWithScores("thumbnail-cache", 0, max, 0, 50);
    }

    public void removeThumbnailLastAccess(String thumbnailFileName) {
        queueRedisTemplate.opsForZSet().remove("thumbnail-cache", thumbnailFileName);
    }

    public static String getThumbnailParentPath() {
        return "/thumbnail-cache/" + thumbnailResolution;
    }

    public String generateThumbnailFromVideo(MediaJobDescription mediaJobDescription) throws Exception {
        String bucket = mediaJobDescription.getBucket();
        String objectName = mediaJobDescription.getPath();

        String thumbnailObject = objectName.substring(0, objectName.lastIndexOf(".")) + "-thumb.jpg";
        String thumbnailOutput = OSUtil.normalizePath(diskDir, thumbnailObject);
        Files.createDirectories(Path.of(thumbnailOutput.substring(0, thumbnailOutput.lastIndexOf("/"))));
        String videoInput = minIOService.getSignedUrlForContainerNginx(bucket, objectName, (int) Duration.ofMinutes(15).toSeconds());


        List<String> command = Arrays.asList(
                "docker", "exec", "ffmpeg",
                "ffmpeg",
                "-v", "error",                 // only show errors, no logs
                "-skip_frame", "nokey",        // skip NON-keyframes → decode only I-frames - fast operation
                "-ss", "2",                    // FAST SEEK: jump to keyframe nearest the 1-second mark
                "-i", videoInput,              // input video
                "-frames:v", "1",              // output exactly one frame
                "-vsync", "vfr",               // variable frame rate; ensures correct frame extraction
                "-q:v", "2",                   // high JPEG quality (2 = very high, 1 = max)
                // select only keyframes (only I-frames). Escaped for Java.
                "-vf", "select='eq(pict_type\\,I)'",
                thumbnailOutput                // output thumbnail (original resolution)
        );

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true); // merge STDOUT + STDERR

        Process process = pb.start();

        List<String> logs = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                logs.add("[ffmpeg] " + line);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        int exitCode = process.waitFor();
        System.out.println("ffmpeg generate thumbnail from video exited with code " + exitCode);
        if (exitCode != 0) {
            logs.forEach(System.out::println);
            throw new RuntimeException("Failed to generate thumbnail from video");
        }

        thumbnailObject = thumbnailObject.startsWith(MediaUploadService.defaultVidPath)
                ? thumbnailObject
                : OSUtil.normalizePath(MediaUploadService.defaultVidPath, thumbnailObject);
        minIOService.moveFileToObject(ThumbnailService.thumbnailBucket, thumbnailObject, thumbnailOutput);
        Files.delete(Path.of(thumbnailOutput));

        return thumbnailObject;
    }
}
