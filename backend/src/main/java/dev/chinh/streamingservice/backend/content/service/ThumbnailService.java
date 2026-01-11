package dev.chinh.streamingservice.backend.content.service;

import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.persistence.entity.MediaDescription;
import dev.chinh.streamingservice.persistence.projection.MediaNameEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private final RedisTemplate<String, String> redisStringTemplate;
    private final MinIOService minIOService;

    public static final Resolution thumbnailResolution = Resolution.p360;

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
            int exitCode = processResizedImagesInBatch(albumUrlInfo, thumbnailResolution, getThumbnailParentPath(), false);
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
                System.out.println("Failed to resize thumbnails");
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
//        List<String> bucketList = new ArrayList<>();
        for (MediaDescription mediaDescription : mediaDescriptionList) {
            if (!mediaDescription.hasThumbnail())
                continue;

//            if (mediaDescription.getMediaType() == MediaType.VIDEO || mediaDescription.getMediaType() == MediaType.GROUPER)
//                bucketList.add(ContentMetaData.THUMBNAIL_BUCKET);
//            else
//                bucketList.add(mediaDescription.getBucket());
//            pathList.add(mediaDescription.getThumbnail());

            String pathString = "/chunks" + getThumbnailPath(mediaDescription.getId(), mediaDescription.getThumbnail());
            albumUrlList.add(new MediaUrl(MediaType.IMAGE, pathString));
        }
//        return new AlbumUrlInfo(albumUrlList, bucketList, pathList);
        return new AlbumUrlInfo(albumUrlList, List.of(ContentMetaData.THUMBNAIL_BUCKET), pathList);
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
        return new AlbumUrlInfo(albumUrlList, List.of(ContentMetaData.THUMBNAIL_BUCKET), pathList);
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
                String input = minIOService.getObjectUrlForContainer(bucket, albumUrlInfo.pathList.get(i));

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
        List<String> logs = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                logs.add("[ffmpeg] " + line);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        int exit = process.waitFor();
        System.out.println("ffmpeg transcode media thumbnails exited with code " + exit);
        if (exit != 0) {
            logs.forEach(System.out::println);
        }
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
}
