package dev.chinh.streamingservice.data.service;

import dev.chinh.streamingservice.OSUtil;
import dev.chinh.streamingservice.content.constant.MediaType;
import dev.chinh.streamingservice.content.constant.Resolution;
import dev.chinh.streamingservice.content.service.AlbumService;
import dev.chinh.streamingservice.content.service.MinIOService;
import dev.chinh.streamingservice.data.dto.MediaNameEntry;
import dev.chinh.streamingservice.data.entity.MediaDescription;
import dev.chinh.streamingservice.search.data.MediaSearchItem;
import dev.chinh.streamingservice.upload.service.MediaUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AlbumService albumService;
    private final MinIOService minIOService;

    public static final String thumbnailBucket = "thumbnail";
    public static final Resolution thumbnailResolution = Resolution.p360;
    private static final String diskDir = "disk";

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
            int exitCode = albumService.processResizedImagesInBatch(albumUrlInfo, thumbnailResolution, getThumbnailParentPath(), true);
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
            int exitCode = albumService.processResizedImagesInBatch(albumUrlInfo, thumbnailResolution, getThumbnailParentPath(), false);
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

    public AlbumService.AlbumUrlInfo getMixThumbnailImagesAsAlbumUrls(List<MediaDescription> mediaDescriptionList) {
        List<String> pathList = new ArrayList<>();
        List<AlbumService.MediaUrl> albumUrlList = new ArrayList<>();
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
            albumUrlList.add(new AlbumService.MediaUrl(MediaType.IMAGE, pathString));
        }
        return new AlbumService.AlbumUrlInfo(albumUrlList, bucketList, pathList);
    }

    public AlbumService.AlbumUrlInfo getThumbnailImagesAsAlbumUrls(List<MediaNameEntry> mediaNameEntryList) {
        List<String> pathList = new ArrayList<>();
        List<AlbumService.MediaUrl> albumUrlList = new ArrayList<>();
        for (MediaNameEntry mediaNameEntry : mediaNameEntryList) {
            if (mediaNameEntry.getThumbnail() == null)
                continue;

            pathList.add(mediaNameEntry.getThumbnail());

            String pathString = "/chunks" + getThumbnailPath(mediaNameEntry.getName(), mediaNameEntry.getThumbnail());
            albumUrlList.add(new AlbumService.MediaUrl(MediaType.IMAGE, pathString));
        }
        return new AlbumService.AlbumUrlInfo(albumUrlList, List.of(thumbnailBucket), pathList);
    }

    private void addCacheThumbnails(String thumbnailFileName, long expiry) {
        redisTemplate.opsForZSet().add("thumbnail-cache", thumbnailFileName, expiry);
    }

    private boolean hasCacheThumbnails(String thumbnailFileName) {
        return redisTemplate.opsForZSet().score("thumbnail-cache", thumbnailFileName) != null;
    }

    public Set<ZSetOperations.TypedTuple<Object>> getAllThumbnailCacheLastAccess(long max) {
        return redisTemplate.opsForZSet()
                .rangeByScoreWithScores("thumbnail-cache", 0, max, 0, 50);
    }

    public void removeThumbnailLastAccess(String thumbnailFileName) {
        redisTemplate.opsForZSet().remove("thumbnail-cache", thumbnailFileName);
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
