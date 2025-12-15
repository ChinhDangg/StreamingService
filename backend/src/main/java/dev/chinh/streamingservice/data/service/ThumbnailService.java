package dev.chinh.streamingservice.data.service;

import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.content.service.MinIOService;
import dev.chinh.streamingservice.data.dto.MediaNameEntry;
import dev.chinh.streamingservice.data.entity.MediaDescription;
import dev.chinh.streamingservice.upload.service.MediaUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private final RedisTemplate<String, String> redisStringTemplate;
    private final MinIOService minIOService;

    public static final String thumbnailBucket = "thumbnail";
    public static final Resolution thumbnailResolution = Resolution.p360;
    private static final String diskDir = "disk";

    public record MediaUrl(MediaType type, String url) {}
    public record AlbumUrlInfo(List<MediaUrl> mediaUrlList, List<String> buckets, List<String> pathList) {}

    public void processThumbnails(List<MediaNameEntry> items) {

        long now = System.currentTimeMillis() + 60 * 60 * 1000;
        List<MediaNameEntry> newThumbnails = new ArrayList<>();
        for (MediaNameEntry item : items) {
            if (item.getThumbnail() == null)
                continue;
            String thumbnailFileName = Paths.get(getThumbnailPath(item.getName(), item.getThumbnail())).getFileName().toString();
            if (!hasCacheThumbnails(thumbnailFileName)) {
                newThumbnails.add(item);
            }
        }
        if (newThumbnails.isEmpty())
            return;
        var albumUrlInfo = getThumbnailImagesAsAlbumUrls(newThumbnails);
        try {
            if (albumUrlInfo.mediaUrlList().isEmpty())
                return;
            int exitCode = processResizedImagesInBatch(albumUrlInfo, thumbnailResolution, getThumbnailParentPath(), true);
            if (exitCode != 0) {
                throw new RuntimeException("Failed to resize thumbnails");
            }
            for (MediaNameEntry item : newThumbnails) {
                addCacheThumbnails(Paths.get(getThumbnailPath(item.getName(), item.getThumbnail())).getFileName().toString(), now);
            }
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void processThumbnails(Collection<? extends MediaDescription> items) {
        List<MediaDescription> newThumbnails = new ArrayList<>();
        for (MediaDescription item : items) {
            if (!item.hasThumbnail())
                continue;
            String thumbnailFileName = Paths.get(getThumbnailPath(item.getId(), item.getThumbnail())).getFileName().toString();
            if (!hasCacheThumbnails(thumbnailFileName)) {
                newThumbnails.add(item);
            }
        }
        if (newThumbnails.isEmpty())
            return;
        var albumUrlInfo = getMixThumbnailImagesAsAlbumUrls(newThumbnails);
        try {
            if (albumUrlInfo.mediaUrlList().isEmpty())
                return;
            int exitCode = processResizedImagesInBatch(albumUrlInfo, thumbnailResolution, getThumbnailParentPath(), false);
            if (exitCode != 0) {
                throw new RuntimeException("Failed to resize thumbnails");
            }
            long now = System.currentTimeMillis() + 60 * 60 * 1000;
            for (MediaDescription item : newThumbnails) {
                addCacheThumbnails(Paths.get(getThumbnailPath(item.getId(), item.getThumbnail())).getFileName().toString(), now);
            }
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private AlbumUrlInfo getMixThumbnailImagesAsAlbumUrls(List<MediaDescription> mediaDescriptionList) {
        List<String> pathList = new ArrayList<>();
        List<MediaUrl> albumUrlList = new ArrayList<>();
        List<String> bucketList = new ArrayList<>();
        for (MediaDescription mediaDescription : mediaDescriptionList) {
            if (!mediaDescription.hasThumbnail())
                continue;

            if (mediaDescription.hasKey()) // video
                bucketList.add(ThumbnailService.thumbnailBucket);
            else
                bucketList.add(mediaDescription.getBucket());
            pathList.add(mediaDescription.getThumbnail());

            String pathString = "/chunks" + getThumbnailPath(mediaDescription.getId(), mediaDescription.getThumbnail());
            albumUrlList.add(new MediaUrl(MediaType.IMAGE, pathString));
        }
        return new AlbumUrlInfo(albumUrlList, bucketList, pathList);
    }

    private AlbumUrlInfo getThumbnailImagesAsAlbumUrls(List<MediaNameEntry> mediaNameEntryList) {
        List<String> pathList = new ArrayList<>();
        List<MediaUrl> albumUrlList = new ArrayList<>();
        for (MediaNameEntry mediaNameEntry : mediaNameEntryList) {
            if (mediaNameEntry.getThumbnail() == null)
                continue;

            pathList.add(mediaNameEntry.getThumbnail());

            String pathString = "/chunks" + getThumbnailPath(mediaNameEntry.getName(), mediaNameEntry.getThumbnail());
            albumUrlList.add(new MediaUrl(MediaType.IMAGE, pathString));
        }
        return new AlbumUrlInfo(albumUrlList, List.of(thumbnailBucket), pathList);
    }

    private void addCacheThumbnails(String thumbnailFileName, long expiry) {
        redisStringTemplate.opsForZSet().add("thumbnail-cache", thumbnailFileName, expiry);
    }

    private boolean hasCacheThumbnails(String thumbnailFileName) {
        return redisStringTemplate.opsForZSet().score("thumbnail-cache", thumbnailFileName) != null;
    }

    public static String getThumbnailPath(String id, String thumbnail) {
        String originalExtension = thumbnail.contains(".") ? thumbnail.substring(thumbnail.lastIndexOf(".") + 1)
                .toLowerCase() : "jpg";
        return getThumbnailParentPath() + "/" + id + "_" + thumbnailResolution + "." + originalExtension;
    }

    public static String getThumbnailPath(long mediaId, String thumbnail) {
        String originalExtension = thumbnail.contains(".") ? thumbnail.substring(thumbnail.lastIndexOf(".") + 1)
                .toLowerCase() : "jpg";
        return getThumbnailParentPath() + "/" + mediaId + "_" + thumbnailResolution + "." + originalExtension;
    }

    public static String getThumbnailParentPath() {
        return "/thumbnail-cache/" + thumbnailResolution;
    }


    private int processResizedImagesInBatch(AlbumUrlInfo albumUrlInfo, Resolution resolution, String albumDir, boolean isAlbum) throws InterruptedException, IOException {
        // Start one persistent bash session inside ffmpeg container
        ProcessBuilder pb = new ProcessBuilder("docker", "exec", "-i", "ffmpeg", "bash").redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {

            OSUtil.createTempDir(albumDir);

            String scale = getFfmpegScaleString(resolution, true);
            for (int i = 0; i < albumUrlInfo.mediaUrlList.size(); i++) {
                String output = albumUrlInfo.mediaUrlList.get(i).url;

                String bucket = isAlbum ? albumUrlInfo.buckets.getFirst() : albumUrlInfo.buckets.get(i);
                String input = minIOService.getSignedUrlForContainerNginx(bucket, albumUrlInfo.pathList.get(i), 5 * 60);

                String ffmpegCmd = String.format(
                        "ffmpeg -n -hide_banner -loglevel info " +
                                "-i \"%s\" -vf %s -q:v 2 -frames:v 1 \"%s\"",
                        input, scale, output
                );
                writer.write(ffmpegCmd + "\n");
                writer.flush();
            }

            // close stdin to end the bash session
            writer.write("exit\n");
            writer.flush();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Capture ffmpeg combined logs
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines().forEach(System.out::println);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int exit = process.waitFor();
        System.out.println("ffmpeg exited with code " + exit);
        return exit;
    }

    private String getFfmpegScaleString(Resolution resolution, boolean wrapInDoubleQuotes) {
        if (wrapInDoubleQuotes)
            return String.format(
                    "\"scale='if(gte(iw,ih),-2,min(iw,%1$d))':'if(gte(ih,iw),-2,min(ih,%1$d))'\"",
                    resolution.getResolution()
            );
        return String.format(
                "scale='if(gte(iw,ih),-2,min(iw,%1$d))':'if(gte(ih,iw),-2,min(ih,%1$d))'",
                resolution.getResolution()
        );
    }

    public String generateThumbnailFromVideo(String bucket, String objectName) throws Exception {
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

        int exitCode = process.waitFor();
        if (exitCode != 0) {
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
