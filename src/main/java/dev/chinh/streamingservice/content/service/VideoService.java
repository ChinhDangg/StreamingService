package dev.chinh.streamingservice.content.service;

import dev.chinh.streamingservice.OSUtil;
import dev.chinh.streamingservice.content.constant.Resolution;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final MinIOService minIOService;

    // for mac
    // diskutil erasevolume HFS+ 'RAMDISK' `hdiutil attach -nomount ram://1048576`

    // for window
    // # Remove old if exists
    // imdisk -D -m R: 2>$null

    // # Create 1 GB RAM disk at R:
    // imdisk -a -s 1G -m R:

    // # Format it (quick NTFS)
    // Start-Process cmd "/c format R: /FS:NTFS /Q /Y" -Verb RunAs -Wait

    private final String RAMDISK = "Volumes/RAMDISK/";

    public String getOriginalVideoUrl(String bucket, String videoId) throws Exception {
        return minIOService.getSignedUrlForHostNginx(bucket, videoId, 300); // 5 minutes
    }

    public String getPreviewVideoUrl(String bucket, String videoId) throws Exception {
        String nginxUrl = minIOService.getSignedUrlForContainerNginx(bucket, videoId, 300);

        double duration = 613.0; // TODO: probe real duration
        int segments = 20;
        double previewLength = duration * 0.10;
        double clipLength = previewLength / segments;
        double interval = duration / segments;

        // === resolution control ===
        int targetHeight = 360; // change to 720, 1080, etc.
        // ==========================

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

        // concat -> scale
        fc.append(String.format(
                "%sconcat=n=%d:v=1:a=1[vcat][acat];" +      // stitch video+audio
                        "[vcat]scale=-2:%d[v]",                     // scale once after concat
                concatInputs, segments, targetHeight
        ));

        String filterComplex = fc.toString();

        String containerDir = "/chunks/" + videoId + "/preview";
        String outPath = containerDir + "/master.m3u8";
        String hostDir = videoId + "/preview";
        if (!OSUtil.createTempDir(hostDir)) {
            throw new IOException("Failed to create temporary directory: " + hostDir);
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
        return "/stream/" + videoId + "/preview/master.m3u8";
    }

    public String getPartialVideoUrl(String bucket, String videoId, Resolution res) throws Exception {
        // 1. Get a presigned URL with container Nginx so ffmpeg can access in container
        // 2. Rewrite URL to go through Nginx proxy instead of direct MinIO
        String nginxUrl = minIOService.getSignedUrlForContainerNginx(bucket, videoId, 300);

        // 3. Host vs container paths
        String hostDir = RAMDISK + videoId + "/partial";  // host path
        String containerDir = "/chunks/" + videoId + "/partial";      // container path
        String outPath = containerDir + "/master.m3u8";

        // Ensure directory exists on host
        File dir = new File(hostDir);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Failed to create directory: " + hostDir);
            }
        }

        int width = 1920;
        int height = 1080;
        int target = res.getResolution();

        String scale;
        if (width >= height) { // Landscape → fix height
            scale = "scale=-2:" + target;
        } else { // Portrait → fix width
            scale = "scale=" + target + ":-2";
        }

        // 4. ffmpeg command (no mkdir -p needed, dir already exists)
        String[] command = {
                "docker", "exec", "ffmpeg",   // run inside ffmpeg container
                "ffmpeg", "-y",               // call ffmpeg, overwrite outputs if they exist
                "-i", nginxUrl,               // input: presigned video URL via Nginx proxy
                "-vf", scale,                 // video filter: resize to 360p height, keep aspect ratio
                "-c:v", "h264",               // encode video with H.264 codec
                "-preset", "veryfast",        // encoder speed/efficiency tradeoff: "veryfast" = low CPU, larger file
                "-c:a", "aac",                // encode audio with AAC codec
                "-f", "hls",                  // output format = HTTP Live Streaming (HLS)
                "-hls_time", "4",             // segment duration: ~4 seconds per .ts file
                "-hls_list_size", "0",        // keep ALL segments in playlist (0 = unlimited)
                outPath                       // output playlist path: /chunks/<videoId>/partial/master.m3u8
        };

        System.out.println("Running ffmpeg command: " + String.join(" ", command));

        runAndLogAsync(command);

        File playlist = new File(hostDir + "/master.m3u8");
        int retries = 10;
        while (!playlist.exists() && retries-- > 0) {
            Thread.sleep(500); // wait 0.5s
        }
        if (!playlist.exists()) {
            throw new RuntimeException("ffmpeg did not create playlist in time: " + playlist.getAbsolutePath());
        }

        // 5. Return playlist URL for browser
        return "/stream/" + videoId + "/partial/master.m3u8";
    }

    private void runAndLog(String[] cmd) throws Exception {
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                //System.out.println("[ffmpeg] " + line);
            }
        }
        int exit = process.waitFor();
        System.out.println("ffmpeg exited with code " + exit);
        if (exit != 0) {
            throw new RuntimeException("Command failed with code " + exit);
        }
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
                e.printStackTrace();
            }
            try {
                int exit = process.waitFor();
                System.out.println("ffmpeg exited with code " + exit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }


}

