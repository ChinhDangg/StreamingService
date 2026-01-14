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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private final MinIOService minIOService;

    private static final String diskDir = "disk";
    private static final String defaultVidPath = "vid";
    private static final String defaultAlbumPath = "album";

    public String generateThumbnailFromVideo(long mediaId, String bucket, String objectName, double videoLength) throws Exception {

        String thumbnailObject = defaultVidPath + "/" + objectName.substring(0, objectName.lastIndexOf("/"))
                + "/" + mediaId + "_" + UUID.randomUUID() + "_thumb.jpg";
        String thumbnailOutput = OSUtil.normalizePath(diskDir, thumbnailObject);
        Files.createDirectories(Path.of(thumbnailOutput.substring(0, thumbnailOutput.lastIndexOf("/"))));
        String videoInput = minIOService.getObjectUrlForContainer(bucket, objectName);
        videoLength = videoLength > 2 ? 2 : 0.5;

        List<String> command = Arrays.asList(
                "docker", "exec", "ffmpeg",
                "ffmpeg",
                "-v", "error",                 // Reduce noise to see real issues
                "-ss", String.valueOf(videoLength), // Seek before input (Fast Seek)
                "-i", videoInput,
                "-frames:v", "1",              // Stop after 1 frame
                "-q:v", "2",
                "-strict", "unofficial",
                "-f", "image2",                // Force output format
                "-update", "1",                // Ensure it writes a single file
                thumbnailOutput
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

    public String copyAlbumObjectToThumbnailBucket(String sourceBucket, String sourceObject) throws Exception {
        String thumbnailObject = sourceObject.startsWith(defaultAlbumPath)
                ? sourceObject
                : OSUtil.normalizePath(defaultAlbumPath, sourceObject);
        minIOService.copyObjectToAnotherBucket(sourceBucket, sourceObject, ContentMetaData.THUMBNAIL_BUCKET, thumbnailObject);
        return thumbnailObject;
    }
}
