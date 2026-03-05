package dev.chinh.streamingservice.filemanager.service;

import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.filemanager.constant.FileType;
import dev.chinh.streamingservice.filemanager.data.FileSystemItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private final RedisTemplate<String, String> redisStringTemplate;
    private final MinIOService minIOService;

    private static final Resolution thumbnailResolution = Resolution.p144;
    // simple limit to process thumbnails in order for now, later add a job to worker for queue work but will be async
    private static final AtomicInteger counter = new AtomicInteger(2);

    public record AlbumUrlInfo(List<String> mediaUrlList, List<String> buckets, List<String> pathList, List<String> fullUrlList) {}

    public List<String> processThumbnail(List<FileSystemItem> items) {
        AlbumUrlInfo albumUrlInfo = getFileSystemItemThumbnailAsAlbumUrls(items);
        if (albumUrlInfo.mediaUrlList.isEmpty())
            return albumUrlInfo.fullUrlList;
        if (counter.get() == 0)
            return albumUrlInfo.fullUrlList;
        counter.decrementAndGet();
        try {
            int exitCode = processResizedImagesInBatch(albumUrlInfo, thumbnailResolution, getThumbnailParentPath(), false);
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
        return albumUrlInfo.fullUrlList;
    }

    private AlbumUrlInfo getFileSystemItemThumbnailAsAlbumUrls(List<FileSystemItem> items) {
        List<String> pathList = new ArrayList<>();
        List<String> buckets = new ArrayList<>();
        List<String> urlList = new ArrayList<>();
        List<String> fullUrlList = new ArrayList<>();
        for (FileSystemItem item : items) {
            if (item.getThumbnail() == null && item.getFileType() != FileType.IMAGE) {
                fullUrlList.add(null);
                continue;
            }

            String thumbnailFileName = getThumbnailPath(
                    item.getMId() == null ? item.getId() : item.getMId().toString(),
                    item.getThumbnail() == null ? item.getObjectName() : item.getThumbnail()
            );
            fullUrlList.add(thumbnailFileName);

            if (item.getThumbnail() != null) {
                pathList.add(item.getThumbnail());
                buckets.add(ContentMetaData.THUMBNAIL_BUCKET);
            }
            else {
                pathList.add(item.getObjectName());
                buckets.add(item.getBucket());
            }
            if (hasCacheThumbnails(thumbnailFileName.substring(thumbnailFileName.lastIndexOf("/") + 1))) {
                pathList.removeLast();
                buckets.removeLast();
                continue;
            }
            String urlString = "/chunks" + thumbnailFileName;
            urlList.add(urlString);
        }
        return new AlbumUrlInfo(urlList, buckets, pathList, fullUrlList);
    }

    public static String getThumbnailPath(String id, String thumbnail) {
        String originalExtension = thumbnail.contains(".") ? thumbnail.substring(thumbnail.lastIndexOf(".") + 1)
                .toLowerCase() : "jpg";
        return getThumbnailParentPath() + "/" + id + "_" + thumbnailResolution + "." + originalExtension;
    }

    public static String getThumbnailParentPath() {
        return "/thumbnail-cache/" + thumbnailResolution;
    }

    private void addCacheThumbnails(String thumbnailFileName, long expiry) {
        redisStringTemplate.opsForZSet().add("thumbnail-cache", thumbnailFileName, expiry);
    }

    private boolean hasCacheThumbnails(String thumbnailFileName) {
        return redisStringTemplate.opsForZSet().score("thumbnail-cache", thumbnailFileName) != null;
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
            System.err.println("Failed to execute ffmpeg command");
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
            System.err.println("Failed to read ffmpeg logs");
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
