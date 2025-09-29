package dev.chinh.streamingservice.content.service;

import dev.chinh.streamingservice.content.constant.Resolution;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final MinIOService minIOService;

    // diskutil erasevolume HFS+ 'RAMDISK' `hdiutil attach -nomount ram://1048576`

    public String getSignedUrlForHostNginx(String bucket, String object, int expirySeconds) throws Exception {
        String signedUrl = minIOService.getSignedUrl(bucket, object, expirySeconds);
        // Replace the MinIO base URL with your Nginx URL
        return signedUrl.replace("http://localhost:9000", "http://localhost/stream/minio");
    }

    public String getSignedUrlForContainerNginx(String bucket, String object, int expirySeconds) throws Exception {
        String signedUrl = minIOService.getSignedUrl(bucket, object, expirySeconds);
        return signedUrl.replace("http://localhost:9000", "http://nginx/stream/minio");
    }

    public String getPreviewVideoUrl(String videoId) throws Exception {
        String bucket = "testminio";
        // 1. Get a presigned URL with container Nginx so ffmpeg can access in container
        String nginxUrl = getSignedUrlForContainerNginx(bucket, videoId, 300);
        // 1-1. Use stored metadata (hardcode for now: 10m13s = 613s)
        double duration = 613.0;
        System.out.println("Using stored duration: " + duration + " seconds");

        // 2. Calculate preview plan
        int segments = 20; // coverage (how many checkpoints across the video).
        double previewLength = duration * 0.10; // 10% of video (total runtime of the final preview)
        double clipLength = previewLength / segments; // evenly distributed
        double interval = duration / segments;        // spacing between starts

        List<String> partFiles = new ArrayList<>();

        String hostDir = "/Volumes/RAMDISK/" + videoId + "/preview";   // host path
        String containerDir = "/chunks/" + videoId + "/preview";       // container path

        // Ensure directory exists on host
        File dir = new File(hostDir);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Failed to create directory: " + hostDir);
            }
        }

        // 3. Extract subclips (ffmpeg writes inside containerDir)
        for (int i = 0; i < segments; i++) {
            double start = i * interval;
            String partFile = containerDir + "/part" + i + ".mp4";  // container path
            partFiles.add(partFile);

            String[] cutCmd = {
                    "docker", "exec", "ffmpeg",
                    "ffmpeg", "-ss", String.valueOf(start),
                    "-t", String.valueOf(clipLength),
                    "-i", nginxUrl,
                    "-c", "copy", partFile
            };
            runAndLog(cutCmd);
        }

        // 4. Write concat_list.txt on host
        File concatList = new File(hostDir + "/concat_list.txt");
        try (PrintWriter pw = new PrintWriter(concatList)) {
            for (String part : partFiles) {
                pw.println("file '" + part + "'");
            }
        }

        // 5. Concat inside container (reads concat_list.txt via mounted RAMDISK)
        String previewFile = containerDir + "/preview.mp4";
        String[] concatCmd = {
                "docker", "exec", "ffmpeg",
                "ffmpeg", "-f", "concat", "-safe", "0",
                "-i", containerDir + "/concat_list.txt",
                "-c", "copy", previewFile
        };
        runAndLog(concatCmd);

        // 6. Encode to HLS (scaled to 360p)
        String outPath = containerDir + "/master.m3u8";
        String[] hlsCmd = {
                "docker", "exec", "ffmpeg",
                "ffmpeg", "-i", previewFile,
                "-vf", "scale=-2:360",
                "-c:v", "h264", "-preset", "veryfast",
                "-c:a", "aac", "-f", "hls",
                "-hls_time", "4", "-hls_list_size", "0", outPath
        };
        runAndLogAsync(hlsCmd);

        // 7. Return URL for frontend
        return "/stream/" + videoId + "/preview/master.m3u8";
    }

    public String getPartialVideoUrl(String videoId, Resolution res) throws Exception {
        String bucket = "testminio";

        // 1. Get a presigned URL with container Nginx so ffmpeg can access in container
        // 2. Rewrite URL to go through Nginx proxy instead of direct MinIO
        String nginxUrl = getSignedUrlForContainerNginx(bucket, videoId, 300);

        // 3. Host vs container paths
        String hostDir = "/Volumes/RAMDISK/" + videoId + "/partial";  // host path
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
                System.out.println("[ffmpeg] " + line);
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

