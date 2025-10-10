package dev.chinh.streamingservice.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.OSUtil;
import dev.chinh.streamingservice.content.constant.Resolution;
import dev.chinh.streamingservice.data.MediaMetaDataRepository;
import dev.chinh.streamingservice.data.entity.MediaDescription;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.List;
import java.util.UUID;

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
        MediaDescription mediaDescription = getMediaDescription(videoId);
        String nginxUrl = minIOService.getSignedUrlForContainerNginx(
                mediaDescription.getBucket(), mediaDescription.getPath(), mediaDescription.getLength() + extraExpireSeconds);

        double duration = mediaDescription.getLength();
        int segments = 20;
        double previewLength = duration * 0.10;
        double clipLength = previewLength / segments;
        double interval = duration / segments;

        // === resolution control ===
        final int resolution = Resolution.p360.getResolution();

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

        String scale = getFfmpegScaleString(mediaDescription.getWidth(), mediaDescription.getHeight(), resolution);

        // concat -> scale
        fc.append(String.format(
                "%sconcat=n=%d:v=1:a=1[vcat][acat];" +      // stitch video+audio
                        "[vcat]%s[v]",                     // scale once after concat
                concatInputs, segments, scale
        )); // scale=-2:%d[v]

        String filterComplex = fc.toString();

        String masterFileName = "/master.m3u8";
        String videoDir = videoId + "/preview";
        String containerDir = "/chunks/" + videoDir;
        String outPath = containerDir + masterFileName;
        if (!OSUtil.createTempDir(videoDir)) {
            throw new IOException("Failed to create temporary directory: " + videoDir);
        }

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
        runAndLogAsync(hlsCmd);

        checkPlaylistCreated(videoDir + masterFileName);

        return "/stream/" + videoId + "/preview" + masterFileName;
    }

    private String currentPartialVideoJobId = null;

    public String getPartialVideoUrl(Long videoId, Resolution res) throws Exception {
        if (currentPartialVideoJobId != null) {
            stopFfmpegJob(currentPartialVideoJobId);
        }

        // 1. Get a presigned URL with container Nginx so ffmpeg can access in container
        // 2. Rewrite URL to go through Nginx proxy instead of direct MinIO
        MediaDescription mediaDescription = getMediaDescription(videoId);
        String nginxUrl = minIOService.getSignedUrlForContainerNginx(mediaDescription.getBucket(),
                mediaDescription.getPath(), mediaDescription.getLength() + extraExpireSeconds);

        // 3. Host vs container paths
        String masterFileName = "/master.m3u8";
        String videoDir = videoId + "/partial";
        String containerDir = "/chunks/" + videoDir;
        String outPath = containerDir + masterFileName;
        if (!OSUtil.createTempDir(videoDir)) {
            throw new IOException("Failed to create temporary directory: " + videoDir);
        }

        String scale = getFfmpegScaleString(
                mediaDescription.getWidth(), mediaDescription.getHeight(), res.getResolution());

        currentPartialVideoJobId = UUID.randomUUID().toString();
        // 4. ffmpeg command (no mkdir -p needed, dir already exists)
        String[] command = {
                "docker", "exec", "ffmpeg",   // run inside ffmpeg container
                "ffmpeg", "-y",               // call ffmpeg, overwrite outputs if they exist
                "-i", nginxUrl,               // input: presigned video URL via Nginx proxy
                "-vf", scale,                 // video filter: resize to 360p height, keep aspect ratio
                "-c:v", "h264",               // encode video with H.264 codec
                "-preset", "veryfast",        // encoder speed/efficiency tradeoff: "veryfast" = low CPU, larger file
                "-c:a", "aac",                // encode audio with AAC codec
                "-metadata", "job_id=" + currentPartialVideoJobId,   // unique tag
                "-f", "hls",                  // output format = HTTP Live Streaming (HLS)
                "-hls_time", "4",             // segment duration: ~4 seconds per .ts file
                "-hls_list_size", "0",        // keep ALL segments in playlist (0 = unlimited)
                outPath                       // output playlist path: /chunks/<videoId>/partial/master.m3u8
        };

        runAndLogAsync(command);

        // check the master file has been created. maybe check first chunks being created for smoother experience
        checkPlaylistCreated(videoDir + masterFileName);

        // 5. Return playlist URL for browser
        return "/stream/" + videoId + "/partial" + masterFileName;
    }

    private void cacheTempVideoMetadata() {

    }

    private void stopFfmpegJob(String jobId) throws Exception {
        runAndLog(new String[] {
                        "docker", "exec", "ffmpeg",
                        "pkill", "-f", "job_id=" + jobId
                }, List.of(1)
        );
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

    private String getFfmpegScaleString(int width, int height, int target) {
        return (width >= height) ? "scale=-2:" + target : "scale=" + target + ":-2";
    }

    private void runAndLogAsync(String[] cmd) throws Exception {
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
                currentPartialVideoJobId = null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }


}

