package dev.chinh.streamingservice.filemanager.service;

import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.filemanager.constant.FileType;
import dev.chinh.streamingservice.filemanager.data.FileSystemItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

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
            addCacheThumbnails(albumUrlInfo.mediaUrlList, now, (name) -> name.substring(name.lastIndexOf("/") + 1));
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        counter.incrementAndGet();
        return albumUrlInfo.fullUrlList;
    }

    public record ShortFileInfo(String thumbnailName, String thumbnailObject, String bucket, String objectName) {}

    private AlbumUrlInfo getFileSystemItemThumbnailAsAlbumUrls(List<FileSystemItem> items) {
        List<String> fullUrlList = new ArrayList<>();
        List<ShortFileInfo> shortFileInfoList = new ArrayList<>();
        for (var item : items) {
            if (item.getThumbnail() == null && item.getFileType() != FileType.IMAGE) {
                fullUrlList.add(null);
                continue;
            }

            String thumbnailPath = getThumbnailPath(
                    item.getMId() == null ? item.getId() : item.getMId().toString(),
                    item.getThumbnail() == null ? item.getObjectName() : item.getThumbnail()
            );
            fullUrlList.add(thumbnailPath);

            shortFileInfoList.add(new ShortFileInfo(thumbnailPath, item.getThumbnail(), item.getBucket(), item.getObjectName()));
        }

        // Batch check existence in Redis (The Pipeline)
        List<Object> existenceResults = redisStringTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (var info : shortFileInfoList) {
                String name = info.thumbnailName().substring(info.thumbnailName().lastIndexOf("/") + 1);
                connection.zSetCommands().zScore("thumbnail-cache".getBytes(), name.getBytes());
            }
            return null;
        });

        List<String> pathList = new ArrayList<>();
        List<String> buckets = new ArrayList<>();
        List<String> urlList = new ArrayList<>();
        for (int i = 0; i < shortFileInfoList.size(); i++) {
            var item = shortFileInfoList.get(i);

            if (existenceResults.get(i) != null) // has thumbnail already
                continue;

            if (item.thumbnailObject != null) {
                pathList.add(item.thumbnailObject);
                buckets.add(ContentMetaData.THUMBNAIL_BUCKET);
            }
            else {
                pathList.add(item.objectName);
                buckets.add(item.bucket);
            }
            String urlString = "/chunks" + item.thumbnailName;
            urlList.add(urlString);
        }
        return new AlbumUrlInfo(urlList, buckets, pathList, fullUrlList);
    }

    public static String getThumbnailPath(String id, String thumbnail) {
        if (id == null || thumbnail == null)
            return null;
        String originalExtension = thumbnail.contains(".") ? thumbnail.substring(thumbnail.lastIndexOf(".") + 1)
                .toLowerCase() : "jpg";
        return getThumbnailParentPath() + "/" + id + "_" + thumbnailResolution + "." + originalExtension;
    }

    public static String getThumbnailParentPath() {
        return "/thumbnail-cache/" + thumbnailResolution;
    }

    private void addCacheThumbnails(List<String> thumbnailFileNames, long expiry, Function<String, String> processThumbnailName) {
        redisStringTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String thumbnailFileName : thumbnailFileNames) {
                String name = processThumbnailName == null ? thumbnailFileName : processThumbnailName.apply(thumbnailFileName);
                connection.zSetCommands().zAdd(
                        "thumbnail-cache".getBytes(),
                        expiry,
                        name.getBytes()
                );
            }
            return null;
        });
    }

    private int processResizedImagesInBatch(AlbumUrlInfo albumUrlInfo, Resolution resolution, String albumDir, boolean isAlbum) throws InterruptedException, IOException {
        String ffmpegName = System.getenv("FFMPEG_NAME");
        String[] shellCmd;
        if (ffmpegName == null || ffmpegName.isEmpty()) {
            // PROD: Start a local bash session
            shellCmd = new String[]{"sh"};
        } else {
            // DEV: Start a bash session inside the ffmpeg container
            shellCmd = new String[]{"docker", "exec", "-i", ffmpegName, "sh"};
        }
        // Start one persistent bash session inside ffmpeg container
        ProcessBuilder pb = new ProcessBuilder(shellCmd).redirectErrorStream(true);
        Process process = pb.start();

        // Capture ffmpeg combined logs in a thread-safe list
        List<String> logs = Collections.synchronizedList(new ArrayList<>());
        Thread logConsumer = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    logs.add("[ffmpeg] " + line);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        logConsumer.start();

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {

            OSUtil.createTempDir(albumDir, ffmpegName);

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
            process.destroy();
            e.printStackTrace();
            System.err.println("Failed to execute ffmpeg command: " + e.getMessage());
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
