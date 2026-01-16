package dev.chinh.streamingservice.workers.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.constant.MediaJobStatus;
import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import dev.chinh.streamingservice.workers.VideoWorker;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class VideoService extends MediaService implements ResourceCleanable {

    private static final Logger log = LogManager.getLogger(VideoService.class);

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


    @Override
    public void handleJob(String tokenKey, MediaJobDescription mediaJobDescription) {
        try {
            boolean toHandleJob = isJobWithinHandleWindow(tokenKey, mediaJobDescription);
            if (!toHandleJob) { // check if job has passed too long or not
                workerRedisService.updateStatus(mediaJobDescription.getWorkId(), MediaJobStatus.STOPPED.name());
                workerRedisService.releaseToken(tokenKey);
                return;
            }
            switch (mediaJobDescription.getJobType()) {
                case "preview" -> {
                    String url = getPreviewVideoUrl(tokenKey, mediaJobDescription);
                    workerRedisService.updateStatus(mediaJobDescription.getWorkId(), MediaJobStatus.RUNNING.name());
                    workerRedisService.addResultToStatus(mediaJobDescription.getWorkId(), "result", url);
                }
                case "partial" -> {
                    String url = getPartialVideoUrl(tokenKey, mediaJobDescription, mediaJobDescription.getResolution());
                    workerRedisService.updateStatus(mediaJobDescription.getWorkId(), MediaJobStatus.RUNNING.name());
                    workerRedisService.addResultToStatus(mediaJobDescription.getWorkId(), "result", url);
                }
                case null, default -> throw new IllegalArgumentException("Unknown job type");
           };
        } catch (Exception e) {
            // job is called to handle - acquired token for it
            // if failed, release the acquired token
            workerRedisService.releaseToken(tokenKey);
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isJobWithinHandleWindow(String tokenKey, MediaJobDescription mediaJobDescription) {
        if (mediaJobDescription.getJobType().equals("videoThumbnail")) {
            return true;
        }
        return super.isJobWithinHandleWindow(tokenKey, mediaJobDescription);
    }

    private String getOriginalVideoUrl(MediaJobDescription mediaJobDescription) {
        return minIOService.getRedirectObjectUrl(mediaJobDescription.getBucket(), mediaJobDescription.getPath());
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

        String nginxUrl = minIOService.getObjectUrlForContainer(mediaJobDescription.getBucket(), mediaJobDescription.getPath());

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
        runAndLogAsync(tokenKey, command.toArray(new String[0]), cacheJobId, outPath, this::saveVideoPreviewToObjectStorage, mediaJobDescription);

        addCacheVideoLastAccess(cacheJobId, null);
        addCacheVideoJobStatus(cacheJobId, partialVideoJobId, estimatedSize, MediaJobStatus.RUNNING);
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

        String nginxUrl = minIOService.getObjectUrlForContainer(mediaJobDescription.getBucket(), mediaJobDescription.getPath());

        String scale = getFfmpegScaleString(
                mediaJobDescription.getWidth(), mediaJobDescription.getHeight(), res.getResolution());

        String containerDir = "/chunks/" + videoDir;
        String outPath = containerDir + masterFileName;
        String partialVideoJobId = createPartialVideo(tokenKey, nginxUrl, scale, videoDir, outPath, prevJobStopped, cacheJobId);

        addCacheVideoLastAccess(cacheJobId, null);
        addCacheVideoJobStatus(cacheJobId, partialVideoJobId, estimatedSize, MediaJobStatus.RUNNING);
        addCacheRunningJob(cacheJobId);

        // check the master file has been created. maybe check first chunks being created for smoother experience
        checkPlaylistCreated(videoDir + masterFileName);

        // 5. Return playlist URL for browser
        return getNginxVideoStreamUrl(videoDir);
    }

    private void saveVideoPreviewToObjectStorage(MediaJobDescription jobDescription) {
        long videoId = jobDescription.getId();
        String videoDir = videoId + "/preview";
        String containerDir = "/chunks/" + videoDir;
        String inputPath = containerDir + masterFileName;

        try {
            String output = OSUtil.createDirInRAMDiskElseDisk("disk", "preview-generated") +
                    "/" + videoId + "_preview_full_output.mp4";

            List<String> commands = List.of(
                    "docker", "exec", "ffmpeg",
                    "ffmpeg",
                    "-v", "error",
                    "-i", inputPath,
                    "-c", "copy",
                    "-bsf:a:m:codec:aac", "aac_adtstoasc",
                    OSUtil.replaceHostRAMDiskWithContainer(output)
            );

            OSUtil.runCommandAndLog(commands.toArray(new String[0]), null);

            String previewObject = jobDescription.getPreview();
            minIOService.moveFileToObject(ContentMetaData.PREVIEW_BUCKET, previewObject, output);
            Files.deleteIfExists(Path.of(output));
        } catch (Exception e) {
            log.error("e: ", e);
            throw new RuntimeException(e);
        }
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

        runAndLogAsync(tokenKey, command.toArray(new String[0]), cacheJobId, outPath, null , null);
        return partialVideoJobId;
    }

    private String getFfmpegScaleString(int width, int height, int target) {
        return (width >= height) ? "scale=-2:" + target : "scale=" + target + ":-2";
    }

    public void checkPlaylistCreated(String playlist) throws InterruptedException {
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

    private <T> void runAndLogAsync(String tokenKey, String[] cmd, String videoJobId, String videoMasterFilePath, Consumer<T> action, T data) throws Exception {
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
                if (exit == 0) {
                    System.out.println("here");
                    boolean wrote = OSUtil.writeTextToTempFile(videoMasterFilePath.replaceFirst("/chunks/", ""), List.of("#EXT-X-ENDLIST"), false);
                    System.out.println(wrote);
                }
                if (videoJobId != null && exit == 0) {
                    action.accept(data);
                    addCacheVideoJobStatus(videoJobId, null, null, MediaJobStatus.COMPLETED);
                }
                else {
                    addCacheVideoJobStatus(videoJobId, null, null, MediaJobStatus.FAILED);
                    logs.forEach(System.out::println);
                }
                workerRedisService.updateStatus(videoJobId, MediaJobStatus.COMPLETED.name());
                workerRedisService.releaseToken(tokenKey);
            } catch (Exception e) {
                System.err.println(e.getMessage());
                e.printStackTrace();
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

    public void addCacheVideoLastAccess(String videoId, Long expiry) {
        String videoLastAccessKey = "cache:lastAccess:video";
        addCacheLastAccess(videoLastAccessKey, videoId, expiry);
    }

    public Double getCacheVideoLastAccess(String videoWorkId) {
        return getCacheLastAccess(videoLastAccessKey, videoWorkId);
    }

    public void removeCacheVideoLastAccess(String videoId) {
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

            String mediaMemoryPath = mediaJobId.replace(":", "/");
            try {
                boolean deleted = OSUtil.deleteForceMemoryDirectory(mediaMemoryPath);
                if (deleted)
                    removingSpace = removingSpace - estimatedSize;
            } catch (IOException e) {
                System.err.println("Failed to delete memory path: " + mediaMemoryPath);
            }

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
