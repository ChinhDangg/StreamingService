package dev.chinh.streamingservice.mediaobject;

import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private final MinIOService minIOService;

    private static final String diskDir = "disk";
    public static final String defaultVidPath = "vid";

    public String generateThumbnailFromVideo(String bucket, String objectName) throws Exception {

        String thumbnailObject = objectName.substring(0, objectName.lastIndexOf(".")) + "-thumb.jpg";
        String thumbnailOutput = OSUtil.normalizePath(diskDir, thumbnailObject);
        Files.createDirectories(Path.of(thumbnailOutput.substring(0, thumbnailOutput.lastIndexOf("/"))));
        String videoInput = minIOService.getObjectUrlForContainer(bucket, objectName);

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
        Path thumbnailPath = Path.of(thumbnailOutput);

        int exitCode = process.waitFor();
        System.out.println("ffmpeg generate thumbnail from video exited with code " + exitCode);
        if (exitCode != 0) {
            logs.forEach(System.out::println);
            try {
                Files.deleteIfExists(thumbnailPath);
            } catch (IOException e) {
                System.out.println("Failed to clean up thumbnail file: ");
                System.out.println(e.getMessage());
            }
            throw new RuntimeException("Failed to generate thumbnail from video");
        }

        thumbnailObject = thumbnailObject.startsWith(defaultVidPath)
                ? thumbnailObject
                : OSUtil.normalizePath(defaultVidPath, thumbnailObject);
        minIOService.moveFileToObject(ContentMetaData.THUMBNAIL_BUCKET, thumbnailObject, thumbnailOutput);
        Files.delete(thumbnailPath);

        return thumbnailObject;
    }

    private List<String> getLogsFromInputStream(InputStream inputStream) {
        List<String> logs = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                logs.add("[ffmpeg] " + line);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return logs;
    }
}
