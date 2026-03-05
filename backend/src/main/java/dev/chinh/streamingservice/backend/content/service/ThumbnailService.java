package dev.chinh.streamingservice.backend.content.service;

import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.persistence.entity.MediaDescription;
import dev.chinh.streamingservice.persistence.projection.NameEntityDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private final RedisTemplate<String, String> redisStringTemplate;
    private final MinIOService minIOService;

    public static final Resolution thumbnailResolution = Resolution.p360;
    private static final AtomicInteger counter = new AtomicInteger(2);

    public record AlbumUrlInfo(List<String> mediaUrlList, List<String> buckets, List<String> pathList) {}

    public void processThumbnails(List<NameEntityDTO> items) {
        if (counter.get() == 0)
            return;
        counter.decrementAndGet();
        long now = System.currentTimeMillis() + 60 * 60 * 1000;
        var albumUrlInfo = getThumbnailImagesAsAlbumUrls(items);
        try {
            if (albumUrlInfo.mediaUrlList().isEmpty())
                return;
            int exitCode = processResizedImagesInBatch(albumUrlInfo, thumbnailResolution, getThumbnailParentPath(), true);
            if (exitCode != 0) {
                throw new RuntimeException("Failed to resize thumbnails");
            }
            for (String url : albumUrlInfo.mediaUrlList) {
                addCacheThumbnails(url.substring(url.lastIndexOf("/") + 1), now);
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        counter.incrementAndGet();
    }

    public void processThumbnails(Collection<? extends MediaDescription> items) {
        if (counter.get() == 0)
            return;
        counter.decrementAndGet();
        var albumUrlInfo = getMixThumbnailImagesAsAlbumUrls(items);
        try {
            if (albumUrlInfo.mediaUrlList().isEmpty())
                return;
            int exitCode = processResizedImagesInBatch(albumUrlInfo, thumbnailResolution, getThumbnailParentPath(), true);
            if (exitCode != 0) {
                throw new RuntimeException("Failed to resize thumbnails");
            }
            long now = System.currentTimeMillis() + 60 * 60 * 1000;
            for (String url : albumUrlInfo.mediaUrlList) {
                addCacheThumbnails(url.substring(url.lastIndexOf("/") + 1), now);
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        counter.incrementAndGet();
    }

    private AlbumUrlInfo getMixThumbnailImagesAsAlbumUrls(Collection<? extends MediaDescription> mediaDescriptionList) {
        List<String> pathList = new ArrayList<>();
        List<String> albumUrlList = new ArrayList<>();
        for (MediaDescription mediaDescription : mediaDescriptionList) {
            if (!mediaDescription.hasThumbnail())
                continue;

            String thumbnailFileName = getThumbnailPath(mediaDescription.getId(), mediaDescription.getThumbnail());
            if (hasCacheThumbnails(thumbnailFileName.substring(thumbnailFileName.lastIndexOf("/") + 1))) {
                continue;
            }

            pathList.add(mediaDescription.getThumbnail());

            String urlString = "/chunks" + thumbnailFileName;
            albumUrlList.add(urlString);
        }
        return new AlbumUrlInfo(albumUrlList, List.of(ContentMetaData.THUMBNAIL_BUCKET), pathList);
    }

    private AlbumUrlInfo getThumbnailImagesAsAlbumUrls(List<NameEntityDTO> mediaNameEntryList) {
        List<String> pathList = new ArrayList<>();
        List<String> albumUrlList = new ArrayList<>();
        for (NameEntityDTO mediaNameEntry : mediaNameEntryList) {
            if (mediaNameEntry.getThumbnail() == null)
                continue;

            String thumbnailFileName = getThumbnailPath(mediaNameEntry.getName(), mediaNameEntry.getThumbnail());
            if (hasCacheThumbnails(thumbnailFileName.substring(thumbnailFileName.lastIndexOf("/") + 1))) {
                continue;
            }

            pathList.add(mediaNameEntry.getThumbnail());

            String urlString = "/chunks" + thumbnailFileName;
            albumUrlList.add(urlString);
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
                String output = albumUrlInfo.mediaUrlList.get(i);

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
