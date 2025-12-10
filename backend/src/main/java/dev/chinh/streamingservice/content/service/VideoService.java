package dev.chinh.streamingservice.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.MediaMapper;
import dev.chinh.streamingservice.OSUtil;
import dev.chinh.streamingservice.content.constant.MediaJobStatus;
import dev.chinh.streamingservice.content.constant.Resolution;
import dev.chinh.streamingservice.data.repository.MediaMetaDataRepository;
import dev.chinh.streamingservice.data.entity.MediaDescription;
import dev.chinh.streamingservice.data.service.MediaMetadataService;
import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class VideoService extends MediaService implements ResourceCleanable {

    private final MemoryManager memoryManager;

    public VideoService(RedisTemplate<String, Object> redisTemplate,
                        ObjectMapper objectMapper, MediaMapper mediaMapper,
                        MediaMetaDataRepository mediaRepository,
                        MinIOService minIOService,
                        MediaMetadataService mediaMetadataService,
                        MemoryManager memoryManager) {
        super(redisTemplate, objectMapper, mediaMapper, mediaRepository, minIOService, mediaMetadataService);
        this.memoryManager = memoryManager;
    }

    @PostConstruct
    public void registerWithMemoryManager() {
        memoryManager.registerResourceCleanable(this);
    }

    private final int extraExpirySeconds = 30 * 60; // 30 minutes

    public String getOriginalVideoUrl(Long videoId) throws Exception {
        MediaDescription mediaDescription = getMediaDescription(videoId);
        return minIOService.getSignedUrlForHostNginx(mediaDescription.getBucket(), mediaDescription.getPath(),
                mediaDescription.getLength() + extraExpirySeconds); // video duration + 30 minutes extra
    }

    public String getPreviewVideoUrl(Long videoId) throws Exception {
        String videoDir = videoId + "/preview";
        String containerDir = "/chunks/" + videoDir;
        String outPath = containerDir + masterFileName;

        String cacheJobId = getCachePreviewJobId(videoId);

        addCacheVideoLastAccess(cacheJobId, null);

        MediaJobStatus mediaJobStatus = getVideoJobStatus(cacheJobId);
        boolean prevJobStopped = false;
        if (mediaJobStatus != null) {
            if (mediaJobStatus.equals(MediaJobStatus.COMPLETED) || mediaJobStatus.equals(MediaJobStatus.RUNNING))
                return getNginxVideoStreamUrl(videoDir);
            else
                prevJobStopped = true;
        }

        OSUtil.createTempDir(videoDir);

        // === resolution control ===
        final Resolution resolution = Resolution.p240;

        MediaDescription mediaDescription = getMediaDescription(videoId);

        double duration = mediaDescription.getLength();
        int segments = Math.max(1, Math.min(Math.toIntExact(Math.round(duration / 60 / 5 * 10)), 20));
        double previewLength = Math.min(duration, 60);
        double clipLength = previewLength / segments;
        double interval = duration / segments;

        if (duration <= 60) {
            return getPartialVideoUrl(videoId, resolution);
        }

        long estimatedSize = (long) (Resolution.getEstimatedSize(
                        mediaDescription.getSize(), mediaDescription.getWidth(), mediaDescription.getHeight(), resolution)
                        / (duration / previewLength));
        memoryManager.freeMemoryForSize(estimatedSize);

        // Build trim chains
        StringBuilder fc = new StringBuilder();
        StringBuilder concatInputs = new StringBuilder();
        for (int i = 0; i < segments; i++) {
            double start = i * interval;
            double end   = start + clipLength;

            fc.append(String.format(
                    "[0:v]trim=start=%.3f:end=%.3f,setpts=PTS-STARTPTS[v%d];" +
                            "[0:a]atrim=start=%.3f:end=%.3f,asetpts=PTS-STARTPTS[a%d];",
                    start, end, i, start, end, i
            ));
            concatInputs.append(String.format("[v%d][a%d]", i, i));
        }

        String scale = getFfmpegScaleString(mediaDescription.getWidth(), mediaDescription.getHeight(), resolution.getResolution());

        // concat -> scale
        fc.append(String.format(
                "%sconcat=n=%d:v=1:a=1[vcat][acat];" +      // stitch video+audio
                        "[vcat]%s[v]",                     // scale once after concat
                concatInputs, segments, scale
        )); // scale=-2:%d[v]
        String filterComplex = fc.toString();

        String nginxUrl = minIOService.getSignedUrlForContainerNginx(
                mediaDescription.getBucket(), mediaDescription.getPath(), mediaDescription.getLength() + extraExpirySeconds);

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
                "-map", "[v]", "-map", "[acat]",
                "-c:v", "h264", "-preset", "veryfast",
//                "-c:a", "aac",
                "-metadata", "job_id=" + partialVideoJobId,
                // Optional: improve HLS segmenting/keyframes consistency
                "-g", "48", "-keyint_min", "48", "-sc_threshold", "0",
                "-f", "hls",
                "-hls_time", String.valueOf(segmentDuration),
                "-hls_list_size", "0",
                "-hls_flags", "append_list+omit_endlist",
                outPath
        ));
        runAndLogAsync(command.toArray(new String[0]), cacheJobId, outPath);

        addCacheVideoJobStatus(cacheJobId, null, estimatedSize, MediaJobStatus.RUNNING);
        addCacheRunningJob(cacheJobId);

        checkPlaylistCreated(videoDir + masterFileName);

        return getNginxVideoStreamUrl(videoDir);
    }

    public String getPartialVideoUrl(Long videoId, Resolution res) throws Exception {
        // 1. container paths
        String videoDir = videoId + "/" + res;

        String cacheJobId = getCacheMediaJobId(videoId, res);

        addCacheVideoLastAccess(cacheJobId, null);

        MediaJobStatus mediaJobStatus = getVideoJobStatus(cacheJobId);
        boolean prevJobStopped = false;
        if (mediaJobStatus != null) {
            if (mediaJobStatus.equals(MediaJobStatus.COMPLETED) || mediaJobStatus.equals(MediaJobStatus.RUNNING))
                return getNginxVideoStreamUrl(videoDir);
            else
                prevJobStopped = true;
        }

        MediaDescription mediaDescription = getMediaDescription(videoId);
        if (checkSrcSmallerThanTarget(mediaDescription.getWidth(), mediaDescription.getHeight(), res.getResolution()))
            return getOriginalVideoUrl(videoId);

        long estimatedSize = Resolution.getEstimatedSize(
                mediaDescription.getSize(), mediaDescription.getWidth(), mediaDescription.getHeight(), res);
        boolean enoughSpace = memoryManager.freeMemoryForSize(estimatedSize);
        if (!enoughSpace)
            return getOriginalVideoUrl(videoId);

        OSUtil.createTempDir(videoDir);

        // 2. Get a presigned URL with container Nginx so ffmpeg can access in container
        // 3. Rewrite URL to go through Nginx proxy instead of direct MinIO
        String nginxUrl = minIOService.getSignedUrlForContainerNginx(mediaDescription.getBucket(),
                mediaDescription.getPath(), mediaDescription.getLength() + extraExpirySeconds);

        String scale = getFfmpegScaleString(
                mediaDescription.getWidth(), mediaDescription.getHeight(), res.getResolution());

        String containerDir = "/chunks/" + videoDir;
        String outPath = containerDir + masterFileName;
        String partialVideoJobId = createPartialVideo(nginxUrl, scale, videoDir, outPath, prevJobStopped, cacheJobId);

        addCacheVideoJobStatus(cacheJobId, partialVideoJobId, estimatedSize, MediaJobStatus.RUNNING);
        addCacheRunningJob(cacheJobId);

        // check the master file has been created. maybe check first chunks being created for smoother experience
        checkPlaylistCreated(videoDir + masterFileName);

        // 5. Return playlist URL for browser
        return getNginxVideoStreamUrl(videoDir);
    }

    public String createPartialVideo(String inputUrl, String scale, String videoDir, String outPath,
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
                "-c:a", "aac",                // encode audio with AAC codec
                "-metadata", "job_id=" + partialVideoJobId,    // unique tag
                "-f", "hls",                  // output format = HTTP Live Streaming (HLS)
                "-hls_time", String.valueOf(segmentDuration),  // segment duration: ~4 seconds per .ts file
                "-hls_list_size", "0",        // keep ALL segments in playlist (0 = unlimited)
                "-hls_flags", "append_list+omit_endlist", // append to existing with starting from last index written and don't write ENDLIST to continue later
                outPath                       // output playlist path: /chunks/<videoId>/<resolution>/partial/master.m3u8)
        ));

        runAndLogAsync(command.toArray(new String[0]), cacheJobId, outPath);
        return partialVideoJobId;
    }

    private final String videoLastAccessKey = "cache:lastAccess:video";
    public void addCacheVideoLastAccess(String videoId, Long expiry) {
        addCacheLastAccess(videoLastAccessKey, videoId, expiry);
    }

    public Double getCacheVideoLastAccess(String videoWorkId) {
        return getCacheLastAccess(videoLastAccessKey, videoWorkId);
    }

    private void removeCacheVideoLastAccess(String videoId) {
        removeCacheLastAccess(videoLastAccessKey, videoId);
    }

    public Set<ZSetOperations.TypedTuple<Object>> getAllVideoCacheLastAccess(long max) {
        return getAllCacheLastAccess(videoLastAccessKey, max);
    }

    public void addCacheVideoJobStatus(String id, String jobId, Long size, MediaJobStatus status) {
        if (jobId != null)
            redisTemplate.opsForHash().put(id, "jobId", jobId);
        if (size != null) {
            redisTemplate.opsForHash().put(id, "size", size);
        }
        redisTemplate.opsForHash().put(id, "status", status);
    }

    public MediaJobStatus getVideoJobStatus(String videoJobId) {
        Map<Object, Object> cachedJobStatus = getVideoJobStatusInfo(videoJobId);
        if (!cachedJobStatus.isEmpty())
            return (MediaJobStatus) cachedJobStatus.get("status");
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

    public Set<Object> getCacheRunningJobs(long max) {
        return redisTemplate.opsForZSet()
                .rangeByScore("video:running", 0, max, 0, 50);
    }

    private String getCachePreviewJobId(long videoId) {
        return videoId + ":preview";
    }

    public void stopFfmpegJob(String jobId) throws Exception {
        OSUtil.runCommandAndLog(new String[]{
                "docker", "exec", "ffmpeg",
                "pkill", "-INT", "-f", "job_id=" + jobId
        }, List.of(255));
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

    @Override
    protected MediaDescription getMediaDescription(long videoId) {
        MediaDescription mediaDescription = super.getMediaDescription(videoId);
        if (!mediaDescription.hasKey())
            throw new IllegalArgumentException("Requested video media does not has key with id: " + videoId);
        return mediaDescription;
    }

    public void checkPlaylistCreated(String playlist) throws IOException, InterruptedException {
        int retries = 20;
        while (!OSUtil.checkTempFileExists(playlist) && retries-- > 0) {
            Thread.sleep(500); // wait 0.5s
        }
        if (!OSUtil.checkTempFileExists(playlist)) {
            throw new RuntimeException("ffmpeg did not create playlist in time: " + playlist);
        }
    }

    private String getFfmpegScaleString(int width, int height, int target) {
        return (width >= height) ? "scale=-2:" + target : "scale=" + target + ":-2";
    }

    private void runAndLogAsync(String[] cmd, String videoId, String videoMasterFilePath) throws Exception {
        Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();

        // Start a new thread just for logging
        new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println("[ffmpeg] " + line);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            try {
                int exit = process.waitFor();
                System.out.println("ffmpeg exited with code " + exit);
                // mark as completed
                if (videoId != null && exit == 0)
                    addCacheVideoJobStatus(videoId, null, null, MediaJobStatus.COMPLETED);
                if (exit == 0)
                    OSUtil.writeTextToTempFile(videoMasterFilePath.replaceFirst("/chunks/", ""), List.of("#EXT-X-ENDLIST"), false);
                else
                    addCacheVideoJobStatus(videoId, null, null, MediaJobStatus.FAILED);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
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
        Set<ZSetOperations.TypedTuple<Object>> lastAccessMediaJob = getAllVideoCacheLastAccess(now);
        for (ZSetOperations.TypedTuple<Object> mediaJob : lastAccessMediaJob) {
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

            String mediaJobId = Objects.requireNonNull(mediaJob.getValue()).toString();
            System.out.println("Removing: " + mediaJobId);

            long estimatedSize = (long) getVideoJobStatusInfo(mediaJobId).get("size");
            removingSpace = removingSpace - estimatedSize;

            String mediaMemoryPath = mediaJobId.replace(":", "/");
            OSUtil.deleteForceMemoryDirectory(mediaMemoryPath);

            removeCacheVideoLastAccess(mediaJobId);
            removeCacheVideoJobStatus(mediaJobId);
        }
        if (!enough && removingSpace - headRoom <= 0)
            enough = true;
        OSUtil.updateUsableMemory(neededSpace - removingSpace); // removingSpace is negative or 0
        return enough;
    }
}

