package dev.chinh.streamingservice.backend.content.service;

import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.mediapersistence.entity.MediaDescription;
import dev.chinh.streamingservice.mediapersistence.projection.NameEntityDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private final RedisTemplate<String, String> redisStringTemplate;
    private final MinIOService minIOService;

    public static final Resolution thumbnailResolution = Resolution.p360;
    private static final AtomicInteger counter = new AtomicInteger(2);

    public record AlbumUrlInfo(List<String> mediaUrlList, List<String> buckets, List<String> pathList) {}

    public void processThumbnails(String userId, List<NameEntityDTO> items) {
        if (counter.get() == 0) {
            System.out.println("Counter is 0, skipping thumbnail processing");
            return;
        }
        var albumUrlInfo = getThumbnailImagesAsAlbumUrls(userId, items);
        processThumbnails(userId, albumUrlInfo);
    }

    public void processThumbnails(String userId, Collection<? extends MediaDescription> items) {
        if (counter.get() == 0) {
            System.out.println("Counter is 0, skipping thumbnail processing");
            return;
        }
        var albumUrlInfo = getMixThumbnailImagesAsAlbumUrls(userId, items);
        processThumbnails(userId, albumUrlInfo);
    }

    private void processThumbnails(String userId, AlbumUrlInfo albumUrlInfo) {
        counter.decrementAndGet();
        try {
            if (albumUrlInfo.mediaUrlList().isEmpty()) {
                counter.incrementAndGet();
                return;
            }
            int exitCode = processResizedImagesInBatch(albumUrlInfo, thumbnailResolution, getThumbnailOutputParentPath(userId), true);
            if (exitCode != 0) {
                throw new RuntimeException("Failed to resize thumbnails");
            }
            long now = System.currentTimeMillis() + 60 * 60 * 1000;
            addCacheThumbnails(albumUrlInfo.mediaUrlList, now, (name) -> name.replaceFirst("/chunks/thumbnail-cache/", ""));
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        counter.incrementAndGet();
    }

    public record ShortThumbnailInfo(String thumbnailName, String thumbnailObject) {}

    private AlbumUrlInfo getMixThumbnailImagesAsAlbumUrls(String userId, Collection<? extends MediaDescription> mediaDescriptionList) {
        List<ShortThumbnailInfo> shortThumbnailInfoList = new ArrayList<>();
        for (MediaDescription mediaDescription : mediaDescriptionList) {
            if (!mediaDescription.hasThumbnail())
                continue;

            String thumbnailFileName = getThumbnailPath(userId, mediaDescription.getId(), mediaDescription.getThumbnail());
            shortThumbnailInfoList.add(new ShortThumbnailInfo(thumbnailFileName, mediaDescription.getThumbnail()));
        }
        return getThumbnailImagesAsAlbumUrlInfo(shortThumbnailInfoList);
    }

    private AlbumUrlInfo getThumbnailImagesAsAlbumUrls(String userId, List<NameEntityDTO> mediaNameEntryList) {
        List<ShortThumbnailInfo> shortThumbnailInfoList = new ArrayList<>();
        for (NameEntityDTO mediaNameEntry : mediaNameEntryList) {
            if (mediaNameEntry.getThumbnail() == null)
                continue;

            String thumbnailFileName = getThumbnailPath(userId, mediaNameEntry.getName(), mediaNameEntry.getThumbnail());
            shortThumbnailInfoList.add(new ShortThumbnailInfo(thumbnailFileName, mediaNameEntry.getThumbnail()));
        }
        return getThumbnailImagesAsAlbumUrlInfo(shortThumbnailInfoList);
    }

    private AlbumUrlInfo getThumbnailImagesAsAlbumUrlInfo(List<ShortThumbnailInfo> shortThumbnailInfoList) {
        List<Object> existenceResults = checkHasCacheThumbnails(shortThumbnailInfoList);

        List<String> pathList = new ArrayList<>();
        List<String> albumUrlList = new ArrayList<>();
        for (int i = 0; i < shortThumbnailInfoList.size(); i++) {
            var info = shortThumbnailInfoList.get(i);
            if (existenceResults.get(i) != null) // has thumbnail already
                continue;

            pathList.add(info.thumbnailObject);

            String urlString = "/chunks" + getThumbnailUrlParentPath() + "/" + info.thumbnailName;
            albumUrlList.add(urlString);
        }
        return new AlbumUrlInfo(albumUrlList, List.of(ContentMetaData.THUMBNAIL_BUCKET), pathList);
    }

    private List<Object> checkHasCacheThumbnails(List<ShortThumbnailInfo> shortThumbnailInfoList) {
        return redisStringTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (var info : shortThumbnailInfoList) {
                connection.zSetCommands().zScore("thumbnail-cache".getBytes(), info.thumbnailName.getBytes());
            }
            return null;
        });
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

    public static String getThumbnailPath(String parentPath, String id, String thumbnail) {
        String originalExtension = thumbnail.contains(".") ? thumbnail.substring(thumbnail.lastIndexOf(".") + 1)
                .toLowerCase() : "jpg";
        return parentPath + "/" + id + "_" + thumbnailResolution + "." + originalExtension;
    }

    public static String getThumbnailPath(String parentPath, long mediaId, String thumbnail) {
        String originalExtension = thumbnail.contains(".") ? thumbnail.substring(thumbnail.lastIndexOf(".") + 1)
                .toLowerCase() : "jpg";
        return parentPath + "/" + mediaId + "_" + thumbnailResolution + "." + originalExtension;
    }

    public static String getThumbnailUrlParentPath() {
        return "/thumbnail-cache";
    }

    private static String getThumbnailOutputParentPath(String userId) {
        return "/thumbnail-cache/" + userId;
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
