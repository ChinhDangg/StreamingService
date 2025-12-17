package dev.chinh.streamingservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.constant.MediaJobStatus;
import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import dev.chinh.streamingservice.workers.VideoWorker;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class VideoService extends MediaService implements ResourceCleanable, JobHandler {

    @Qualifier("ffmpegExecutor")
    private final ExecutorService ffmpegExecutor;

    private final MemoryManager memoryManager;

    public VideoService(@Qualifier("queueRedisTemplate") RedisTemplate<String, String> redisTemplate,
                        ObjectMapper objectMapper,
                        MinIOService minIOService,
                        WorkerRedisService workerRedisService,
                        MemoryManager memoryManager,
                        ExecutorService ffmpegExecutor) {
        super(redisTemplate, objectMapper, minIOService, workerRedisService);
        this.memoryManager = memoryManager;
        this.ffmpegExecutor = ffmpegExecutor;
    }

    @PostConstruct
    public void registerWithMemoryManager() {
        memoryManager.registerResourceCleanable(this);
    }

    private final int extraExpirySeconds = 30 * 60; // 30 minutes
    private static final String diskDir = "disk";

    @Override
    public void handleJob(String tokenKey, MediaJobDescription mediaJobDescription) {
        try {
           String url = switch (mediaJobDescription.getJobType()) {
                case "preview" -> {
                    url = getPreviewVideoUrl(tokenKey, mediaJobDescription);
                    workerRedisService.updateStatus(mediaJobDescription.getWorkId(), MediaJobStatus.RUNNING.name());
                    yield url;
                }
                case "partial" -> {
                    url = getPartialVideoUrl(tokenKey, mediaJobDescription, mediaJobDescription.getResolution());
                    workerRedisService.updateStatus(mediaJobDescription.getWorkId(), MediaJobStatus.RUNNING.name());
                    yield url;
                }
                case "videoThumbnail" -> {
                    url = generateThumbnailFromVideo(mediaJobDescription);
                    workerRedisService.updateStatus(mediaJobDescription.getWorkId(), MediaJobStatus.COMPLETED.name());
                    workerRedisService.releaseToken(tokenKey);
                    yield url;
                }
                case null, default -> throw new IllegalArgumentException("Unknown job type");
           };
           workerRedisService.addResultToStatus(mediaJobDescription.getWorkId(), "result", url);
        } catch (Exception e) {
            // job is called to handle - acquired token for it
            // if failed, release the acquired token
            workerRedisService.releaseToken(tokenKey);
            throw new RuntimeException(e);
        }
    }

    private String getOriginalVideoUrl(MediaJobDescription mediaJobDescription) throws Exception {
        return minIOService.getSignedUrlForHostNginx(mediaJobDescription.getBucket(), mediaJobDescription.getPath(),
                mediaJobDescription.getLength() + extraExpirySeconds); // video duration + 30 minutes extra
    }

    private String getPreviewVideoUrl(String tokenKey, MediaJobDescription mediaJobDescription) throws Exception {
        long videoId = mediaJobDescription.getId();
        String videoDir = videoId + "/preview";
        String containerDir = "/chunks/" + videoDir;
        String outPath = containerDir + masterFileName;

        String cacheJobId = getCachePreviewJobId(videoId);

        MediaJobStatus mediaJobStatus = getVideoJobStatus(cacheJobId);
        boolean prevJobStopped = false;
        if (mediaJobStatus != null) {
            if (mediaJobStatus.equals(MediaJobStatus.COMPLETED) || mediaJobStatus.equals(MediaJobStatus.RUNNING))
                return getNginxVideoStreamUrl(videoDir);
            else
                prevJobStopped = true;
        }

        // === resolution control ===
        final Resolution resolution = Resolution.p240;

        double duration = mediaJobDescription.getLength();
        int segments = Math.max(1, Math.min(Math.toIntExact(Math.round(duration / 60 / 5 * 10)), 20));
        double previewLength = Math.min(duration, 60);
        double clipLength = previewLength / segments;
        double interval = duration / segments;

        if (duration <= 60) {
            return getPartialVideoUrl(tokenKey, mediaJobDescription, resolution);
        }

        OSUtil.createTempDir(videoDir);

        long estimatedSize = (long) (Resolution.getEstimatedSize(
                mediaJobDescription.getSize(), mediaJobDescription.getWidth(), mediaJobDescription.getHeight(), resolution)
                / (duration / previewLength));
        memoryManager.freeMemoryForSize(estimatedSize);

        // Build trim chains
        StringBuilder fc = new StringBuilder();
        StringBuilder concatInputs = new StringBuilder();
        for (int i = 0; i < segments; i++) {
            double start = i * interval;
            double end   = start + clipLength;

//            fc.append(String.format(
//                    "[0:v]trim=start=%.3f:end=%.3f,setpts=PTS-STARTPTS[v%d];" +
//                            "[0:a]atrim=start=%.3f:end=%.3f,asetpts=PTS-STARTPTS[a%d];",
//                    start, end, i, start, end, i
//            ));
//            concatInputs.append(String.format("[v%d][a%d]", i, i));
            fc.append(String.format(
                    "[0:v]trim=start=%.3f:end=%.3f,setpts=PTS-STARTPTS[v%d];",
                    start, end, i
            ));
            concatInputs.append(String.format("[v%d]", i));
        }

        String scale = getFfmpegScaleString(mediaJobDescription.getWidth(), mediaJobDescription.getHeight(), resolution.getResolution());

        // concat -> scale
        fc.append(String.format(
//                "%sconcat=n=%d:v=1:a=1[vcat][acat];" +     // stitch video+audio
//                        "[vcat]%s[v]",                     // scale once after concat
                "%sconcat=n=%d:v=1:a=0[vcat];" +            // stitch video
                        "[vcat]%s[v]",                      // scale once after concat
                concatInputs, segments, scale
        )); // scale=-2:%d[v]
        String filterComplex = fc.toString();

        String nginxUrl = minIOService.getSignedUrlForContainerNginx(
                mediaJobDescription.getBucket(), mediaJobDescription.getPath(), mediaJobDescription.getLength() + extraExpirySeconds);

        int segmentDuration = 4;
        String partialVideoJobId = UUID.randomUUID().toString();
        List<String> command = new ArrayList<>(List.of(
                "docker", "exec", "ffmpeg",
                "ffmpeg", "-y", "-hide_banner"
        ));

        if (prevJobStopped) {
            String playListLines = OSUtil.readPlayListFromTempDir(videoDir);
            if (playListLines != null && !playListLines.isEmpty()) {
                int lastSegment = findLastSegmentFromPlayList(playListLines);
                if (lastSegment > 0) {
                    double resumeAt = lastSegment * segmentDuration;
                    command.addAll(List.of("-ss", String.valueOf(resumeAt)));
                }
            }
        }

        command.addAll(List.of(
                "-i", nginxUrl,
                "-filter_complex", filterComplex,
                "-map", "[v]",
//                "-map", "[acat]",
                "-c:v", "h264", "-preset", "veryfast",
//                "-c:a", "aac",    // encode audio with AAC codec
                "-metadata", "job_id=" + partialVideoJobId,
                // Optional: improve HLS segmenting/keyframes consistency
                "-g", "48", "-keyint_min", "48", "-sc_threshold", "0",
                "-f", "hls",
                "-hls_time", String.valueOf(segmentDuration),
                "-hls_list_size", "0",
                "-hls_flags", "append_list+omit_endlist",
                outPath
        ));
        runAndLogAsync(tokenKey, command.toArray(new String[0]), cacheJobId, outPath);

        addCacheVideoJobStatus(cacheJobId, null, estimatedSize, MediaJobStatus.RUNNING);
        addCacheRunningJob(cacheJobId);

        checkPlaylistCreated(videoDir + masterFileName);

        return getNginxVideoStreamUrl(videoDir);
    }

    private String getPartialVideoUrl(String tokenKey, MediaJobDescription mediaJobDescription, Resolution res) throws Exception {
        if (res == Resolution.original)
            return getOriginalVideoUrl(mediaJobDescription);

        long videoId = mediaJobDescription.getId();

        // 1. container paths
        String videoDir = videoId + "/" + res;

        String cacheJobId = getCacheMediaJobIdString(videoId, res);

        MediaJobStatus mediaJobStatus = getVideoJobStatus(cacheJobId);
        boolean prevJobStopped = false;
        if (mediaJobStatus != null) {
            if (mediaJobStatus.equals(MediaJobStatus.COMPLETED) || mediaJobStatus.equals(MediaJobStatus.RUNNING))
                return getNginxVideoStreamUrl(videoDir);
            else
                prevJobStopped = true;
        }

        if (checkSrcSmallerThanTarget(mediaJobDescription.getWidth(), mediaJobDescription.getHeight(), res.getResolution()))
            return getOriginalVideoUrl(mediaJobDescription);

        long estimatedSize = Resolution.getEstimatedSize(
                mediaJobDescription.getSize(), mediaJobDescription.getWidth(), mediaJobDescription.getHeight(), res);
        boolean enoughSpace = memoryManager.freeMemoryForSize(estimatedSize);
        if (!enoughSpace)
            return getOriginalVideoUrl(mediaJobDescription);

        OSUtil.createTempDir(videoDir);

        // 2. Get a presigned URL with container Nginx so ffmpeg can access in container
        // 3. Rewrite URL to go through Nginx proxy instead of direct MinIO
        String nginxUrl = minIOService.getSignedUrlForContainerNginx(mediaJobDescription.getBucket(),
                mediaJobDescription.getPath(), mediaJobDescription.getLength() + extraExpirySeconds);

        String scale = getFfmpegScaleString(
                mediaJobDescription.getWidth(), mediaJobDescription.getHeight(), res.getResolution());

        String containerDir = "/chunks/" + videoDir;
        String outPath = containerDir + masterFileName;
        String partialVideoJobId = createPartialVideo(tokenKey, nginxUrl, scale, videoDir, outPath, prevJobStopped, cacheJobId);

        addCacheVideoJobStatus(cacheJobId, partialVideoJobId, estimatedSize, MediaJobStatus.RUNNING);
        addCacheRunningJob(cacheJobId);

        // check the master file has been created. maybe check first chunks being created for smoother experience
        checkPlaylistCreated(videoDir + masterFileName);

        // 5. Return playlist URL for browser
        return getNginxVideoStreamUrl(videoDir);
    }

    public String createPartialVideo(String tokenKey, String inputUrl, String scale, String videoDir, String outPath,
                                     boolean prevJobStopped, String cacheJobId) throws Exception {
        final int segmentDuration = 4;
        String partialVideoJobId = UUID.randomUUID().toString();
        // 4. ffmpeg command
        List<String> command = new ArrayList<>(List.of(
                "docker", "exec", "ffmpeg",     // run inside ffmpeg container
                "ffmpeg", "-y"                  // call ffmpeg, overwrite outputs if they exist
        ));

        if (prevJobStopped) {
            String playListLines = OSUtil.readPlayListFromTempDir(videoDir);
            if (playListLines != null && !playListLines.isEmpty()) {
                int lastSegment = findLastSegmentFromPlayList(playListLines);
                if (lastSegment > 0) {
                    double resumeAt = lastSegment * segmentDuration;
                    command.addAll(List.of("-ss", String.valueOf(resumeAt)));
                }
            }
        }

        command.addAll(List.of(
                "-i", inputUrl,     // input: presigned video URL via Nginx proxy
                "-vf", scale,                 // video filter: resize to 360p height, keep aspect ratio
                "-c:v", "h264",               // encode video with H.264 codec
                "-preset", "veryfast",        // encoder speed/efficiency tradeoff: "veryfast" = low CPU, larger file
                "-force_key_frames", "expr:gte(t,n_forced*" + segmentDuration + ")", // force keyframe every 4 seconds as hls_time is just a target
                "-sc_threshold", "0",             // disable scene change detection as it inserts extra keyframes
                "-c:a", "aac",                // encode audio with AAC codec
                "-metadata", "job_id=" + partialVideoJobId,    // unique tag
                "-f", "hls",                  // output format = HTTP Live Streaming (HLS)
                "-hls_time", String.valueOf(segmentDuration),  // segment duration: ~4 seconds per .ts file
                "-hls_list_size", "0",        // keep ALL segments in playlist (0 = unlimited)
                "-hls_flags", "append_list+omit_endlist", // append to existing with starting from last index written and don't write ENDLIST to continue later
                outPath                       // output playlist path: /chunks/<videoId>/<resolution>/partial/master.m3u8)
        ));

        runAndLogAsync(tokenKey, command.toArray(new String[0]), cacheJobId, outPath);
        return partialVideoJobId;
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

        List<String> logs = getLogsFromInputStream(process.getInputStream());

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

    private String getFfmpegScaleString(int width, int height, int target) {
        return (width >= height) ? "scale=-2:" + target : "scale=" + target + ":-2";
    }

    public void checkPlaylistCreated(String playlist) throws IOException, InterruptedException {
        int retries = 100;
        while (!OSUtil.checkTempFileExists(playlist) && retries-- > 0) {
            Thread.sleep(100);
        }
        if (!OSUtil.checkTempFileExists(playlist)) {
            throw new RuntimeException("ffmpeg did not create playlist in time: " + playlist);
        }
    }

    private int findLastSegmentFromPlayList(String lines) {
        Pattern pattern = Pattern.compile("master(\\d+)\\.ts");
        int lastIndex = 0;
        for (String line : lines.split("\n")) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                int num =  Integer.parseInt(matcher.group(1));
                if (num > lastIndex) lastIndex = num;
            }
        }
        return lastIndex;
    }

    private void runAndLogAsync(String tokenKey, String[] cmd, String videoJobId, String videoMasterFilePath) throws Exception {
        Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();

        // Start a new thread
        ffmpegExecutor.submit(() -> {
            List<String> logs = getLogsFromInputStream(process.getInputStream());
            try {
                int exit = process.waitFor();
                System.out.println("ffmpeg video " + videoMasterFilePath + " exited with code " + exit);
                // mark as completed
                if (videoJobId != null && exit == 0)
                    addCacheVideoJobStatus(videoJobId, null, null, MediaJobStatus.COMPLETED);
                if (exit == 0)
                    OSUtil.writeTextToTempFile(videoMasterFilePath.replaceFirst("/chunks/", ""), List.of("#EXT-X-ENDLIST"), false);
                else {
                    addCacheVideoJobStatus(videoJobId, null, null, MediaJobStatus.FAILED);
                    logs.forEach(System.out::println);
                }
                workerRedisService.updateStatus(videoJobId, MediaJobStatus.COMPLETED.name());
                workerRedisService.releaseToken(tokenKey);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void stopFfmpegJob(String videoJobId, String jobId) throws Exception {
        OSUtil.runCommandAndLog(new String[]{
                "docker", "exec", "ffmpeg",
                "pkill", "-INT", "-f", "job_id=" + jobId
        }, List.of(255));
        workerRedisService.updateStatus(videoJobId, MediaJobStatus.STOPPED.name());
        addCacheVideoJobStatus(videoJobId, null, null, MediaJobStatus.STOPPED);
        workerRedisService.releaseToken(VideoWorker.TOKEN_KEY);
    }

    private String getCachePreviewJobId(long videoId) {
        return videoId + ":preview";
    }

    private final String videoLastAccessKey = "cache:lastAccess:video";
    private Set<ZSetOperations.TypedTuple<String>> getAllVideoCacheLastAccess(long max) {
        return getAllCacheLastAccess(videoLastAccessKey, max);
    }

    public Double getCacheVideoLastAccess(String videoWorkId) {
        return getCacheLastAccess(videoLastAccessKey, videoWorkId);
    }

    private void removeCacheVideoLastAccess(String videoId) {
        removeCacheLastAccess(videoLastAccessKey, videoId);
    }

    public void addCacheVideoJobStatus(String id, String jobId, Long size, MediaJobStatus status) {
        if (jobId != null)
            redisTemplate.opsForHash().put(id, "jobId", jobId);
        if (size != null) {
            redisTemplate.opsForHash().put(id, "size", String.valueOf(size));
        }
        redisTemplate.opsForHash().put(id, "status", status.name());
    }

    public MediaJobStatus getVideoJobStatus(String videoJobId) {
        Map<Object, Object> cachedJobStatus = getVideoJobStatusInfo(videoJobId);
        if (!cachedJobStatus.isEmpty())
            return MediaJobStatus.valueOf((String) cachedJobStatus.get("status"));
        return null;
    }

    public Map<Object, Object> getVideoJobStatusInfo(String videoJobId) {
        return redisTemplate.opsForHash().entries(videoJobId);
    }

    private void removeCacheVideoJobStatus(String videoJobId) {
        redisTemplate.delete(videoJobId);
    }

    public void addCacheRunningJob(String jobId) {
        redisTemplate.opsForZSet().add("video:running", jobId, System.currentTimeMillis());
    }

    public void removeCacheRunningJob(String jobId) {
        redisTemplate.opsForZSet().remove("video:running", jobId);
    }

    public Set<String> getCacheRunningJobs(long max) {
        return redisTemplate.opsForZSet()
                .rangeByScore("video:running", 0, max, 0, 50);
    }

    public boolean freeMemorySpaceForSize(long size) {
        if (OSUtil.getUsableMemory() >= size) {
            OSUtil.updateUsableMemory(-size);
            return true;
        }
        if (size > OSUtil.MEMORY_TOTAL) {
            System.out.println("Memory limit reached: " + size / 1000 + " MB");
            return false;
        }

        long headRoom = (long) (OSUtil.MEMORY_TOTAL * 0.1);
        long neededSpace = size + headRoom - OSUtil.getUsableMemory();
        neededSpace = (neededSpace > OSUtil.MEMORY_TOTAL) ? size : neededSpace;

        long removingSpace = neededSpace;
        boolean enough = false;

        long now = System.currentTimeMillis();
        Set<ZSetOperations.TypedTuple<String>> lastAccessMediaJob = getAllVideoCacheLastAccess(now);
        for (ZSetOperations.TypedTuple<String> mediaJob : lastAccessMediaJob) {
            if (removingSpace <= 0) {
                enough = true;
                break;
            }

            long millisPassed = (long) (System.currentTimeMillis() - mediaJob.getScore());
            if (millisPassed < 60_000) {
                // zset is already sort, so if one found still being active - then the rest after is the same
                if (removingSpace - headRoom > 0) {
                    System.out.println("Most content is still being active, can't remove enough memory");
                    System.out.println("Serve original size from disk instead or wait");
                }
                break;
            }

            String mediaJobId = Objects.requireNonNull(mediaJob.getValue());
            System.out.println("Removing: " + mediaJobId);

            long estimatedSize = Long.parseLong((String) getVideoJobStatusInfo(mediaJobId).get("size"));
            removingSpace = removingSpace - estimatedSize;

            String mediaMemoryPath = mediaJobId.replace(":", "/");
            OSUtil.deleteForceMemoryDirectory(mediaMemoryPath);

            removeCacheVideoLastAccess(mediaJobId);
            removeCacheVideoJobStatus(mediaJobId);
            removeJobStatus(mediaJobId);
        }
        if (!enough && removingSpace - headRoom <= 0)
            enough = true;
        OSUtil.updateUsableMemory(neededSpace - removingSpace); // removingSpace is negative or 0
        return enough;
    }
}
