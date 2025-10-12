package dev.chinh.streamingservice.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.OSUtil;
import dev.chinh.streamingservice.content.constant.MediaJobStatus;
import dev.chinh.streamingservice.content.constant.Resolution;
import dev.chinh.streamingservice.data.MediaMetaDataRepository;
import dev.chinh.streamingservice.data.entity.MediaDescription;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class VideoService extends MediaService {

    public VideoService(RedisTemplate<String, Object> redisTemplate, MinIOService minIOService,
                        ObjectMapper objectMapper, MediaMetaDataRepository mediaRepository) {
        super(redisTemplate, minIOService, objectMapper, mediaRepository);
    }

    private final int extraExpireSeconds = 30 * 60; // 30 minutes

    public String getOriginalVideoUrl(Long videoId) throws Exception {
        MediaDescription mediaDescription = getMediaDescription(videoId);
        return minIOService.getSignedUrlForHostNginx(mediaDescription.getBucket(), mediaDescription.getPath(),
                mediaDescription.getLength() + extraExpireSeconds); // video duration + 30 minutes extra
    }

    public String getPreviewVideoUrl(Long videoId) throws Exception {
        String masterFileName = "/master.m3u8";
        String videoDir = videoId + "/preview";
        String containerDir = "/chunks/" + videoDir;
        String outPath = containerDir + masterFileName;

        // === resolution control ===
        final Resolution resolution = Resolution.p360;

        String cacheJobId = getCachePreviewJobId(videoId, resolution);
        var cachedJob = getCacheTempVideoProcess(cacheJobId);
        if (!cachedJob.isEmpty()) {
            return "/stream/" + videoId + "/preview" + masterFileName;
        }

        if (!OSUtil.createTempDir(videoDir)) {
            throw new IOException("Failed to create temporary directory: " + videoDir);
        }

        MediaDescription mediaDescription = getMediaDescription(videoId);

        double duration = mediaDescription.getLength();
        int segments = 20;
        double previewLength = duration * 0.10;
        double clipLength = previewLength / segments;
        double interval = duration / segments;

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
                mediaDescription.getBucket(), mediaDescription.getPath(), mediaDescription.getLength() + extraExpireSeconds);

        String[] hlsCmd = {
                "docker", "exec", "ffmpeg",
                "ffmpeg", "-hide_banner",
                "-i", nginxUrl,
                "-filter_complex", filterComplex,
                "-map", "[v]", "-map", "[acat]",
                "-c:v", "h264", "-preset", "veryfast",
                "-c:a", "aac",
                // Optional: improve HLS segmenting/keyframes consistency
                "-g", "48", "-keyint_min", "48", "-sc_threshold", "0",
                "-f", "hls",
                "-hls_time", "4",
                "-hls_list_size", "0",
                outPath
        };
        runAndLogAsync(hlsCmd, cacheJobId);

        addCacheTempVideoProcess(cacheJobId, null, MediaJobStatus.RUNNING);

        checkPlaylistCreated(videoDir + masterFileName);

        return "/stream/" + videoId + "/preview" + masterFileName;
    }

    public String getPartialVideoUrl(Long videoId, Resolution res) throws Exception {
        // 1. container paths
        String masterFileName = "/master.m3u8";
        String videoDir = videoId + "/" + res + "/partial";
        String containerDir = "/chunks/" + videoDir;
        String outPath = containerDir + masterFileName;

        String cacheJobId = getCachePartialJobId(videoId, res);
        Map<Object, Object> cachedJob = getCacheTempVideoProcess(cacheJobId);
        boolean prevJobStopped = false;
        if (!cachedJob.isEmpty()) {
            MediaJobStatus status = (MediaJobStatus) cachedJob.get("status");
            if (status != MediaJobStatus.STOPPED)
                return "/stream/" + videoId + "/" + res + "/partial" + masterFileName;
            prevJobStopped = true;
        }

        MediaDescription mediaDescription = getMediaDescription(videoId);
        if (checkSrcSmallerThanTarget(mediaDescription.getWidth(), mediaDescription.getHeight(), res.getResolution()))
            return getOriginalVideoUrl(videoId);

        if (!OSUtil.createTempDir(videoDir)) {
            throw new IOException("Failed to create temporary directory: " + videoDir);
        }

        // 2. Get a presigned URL with container Nginx so ffmpeg can access in container
        // 3. Rewrite URL to go through Nginx proxy instead of direct MinIO
        String nginxUrl = minIOService.getSignedUrlForContainerNginx(mediaDescription.getBucket(),
                mediaDescription.getPath(), mediaDescription.getLength() + extraExpireSeconds);

        String scale = getFfmpegScaleString(
                mediaDescription.getWidth(), mediaDescription.getHeight(), res.getResolution());

        int segmentDuration = 4;
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
                "-i", nginxUrl,     // input: presigned video URL via Nginx proxy
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

        runAndLogAsync(command.toArray(new String[0]), cacheJobId);

        addCacheTempVideoProcess(cacheJobId, partialVideoJobId, MediaJobStatus.RUNNING);

        // check the master file has been created. maybe check first chunks being created for smoother experience
        checkPlaylistCreated(videoDir + masterFileName);

        // 5. Return playlist URL for browser
        return "/stream/" + videoDir + masterFileName;
    }

    private void addCacheTempVideoProcess(String id, String jobId, MediaJobStatus status) {
        if (jobId != null)
            redisTemplate.opsForHash().put(id, "jobId", jobId);
        redisTemplate.opsForHash().put(id, "status", status);
    }

    private Map<Object, Object> getCacheTempVideoProcess(String id) {
        return redisTemplate.opsForHash().entries(id);
    }

    private String getCachePartialJobId(long videoId, Resolution res) {
        return "partial:" + videoId + ":" + res;
    }

    private String getCachePreviewJobId(long videoId, Resolution res) {
        return "preview:" + videoId + ":" + res;
    }

    public void stopFfmpegJob(String jobId) throws Exception {
        runAndLog(new String[]{
                "docker", "exec", "ffmpeg",
                "pkill", "-INT", "-f", "job_id=" + jobId
        }, List.of(0));
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
            throw new IllegalStateException("Requested video media does not has key with id: " + videoId);
        return mediaDescription;
    }

    private void checkPlaylistCreated(String playlist) throws IOException, InterruptedException {
        int retries = 10;
        while (!OSUtil.checkTempFileExists(playlist) && retries-- > 0) {
            Thread.sleep(500); // wait 0.5s
        }
        if (!OSUtil.checkTempFileExists(playlist)) {
            throw new RuntimeException("ffmpeg did not create playlist in time: " + playlist);
        }
    }

    private void runAndLogAsync(String[] cmd, String videoId) throws Exception {
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
                    addCacheTempVideoProcess(videoId, null, MediaJobStatus.COMPLETED);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }


}

