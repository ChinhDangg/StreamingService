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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private final RedisTemplate<String, String> redisStringTemplate;
    private final MinIOService minIOService;

    public static final Resolution thumbnailResolution = Resolution.p360;
    private final Set<String> thumbnailServiceCache = ConcurrentHashMap.newKeySet(); // service cache during request only, main cache is still in redis
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
            processResizedImagesInBatch(albumUrlInfo, thumbnailResolution, getThumbnailOutputParentPath(userId), true);
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

    private void processResizedImagesInBatch(AlbumUrlInfo albumUrlInfo, Resolution resolution, String albumDir, boolean isAlbum) {
        String ffmpegName = System.getenv("FFMPEG_NAME");
        final Set<String> localThumbnailCache = ConcurrentHashMap.newKeySet();

        try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            Semaphore semaphore = new Semaphore(4);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            OSUtil.createTempDir(albumDir, ffmpegName);

            String scale = getFfmpegScaleString(resolution, false);

            for (int i = 0; i < albumUrlInfo.mediaUrlList.size(); i++) {
                final int index = i;
                final String output = albumUrlInfo.mediaUrlList.get(index);
                final String name = output.replaceFirst("/chunks/thumbnail-cache/", "");
                if (thumbnailServiceCache.contains(name))
                    continue;

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        semaphore.acquire();

                        List<String> cmd = new ArrayList<>();
                        if (ffmpegName != null && !ffmpegName.isEmpty()) {
                            // DEV: Start a bash session inside the ffmpeg container
                            cmd.addAll(List.of("docker", "exec", ffmpegName));
                        }

                        String bucket = isAlbum ? albumUrlInfo.buckets.getFirst() : albumUrlInfo.buckets.get(index);
                        String input = minIOService.getObjectUrlForContainer(bucket, albumUrlInfo.pathList.get(index));

                        cmd.addAll(List.of(
                                "ffmpeg",
                                "-n", "-hide_banner", "-loglevel", "error",
                                "-i", input,
                                "-vf", scale,
                                "-q:v", "2",
                                "-frames:v", "1",
                                "-update", "1",
                                output
                        ));

                        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
                        Process process = pb.start();

                        // Capture ffmpeg combined logs in a thread-safe list
                        List<String> logs = Collections.synchronizedList(new ArrayList<>());
                        Thread logConsumer = Thread.ofVirtual().unstarted(() -> {
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

                        int exitCode = process.waitFor();
                        if (exitCode != 0) {
                            logs.forEach(System.out::println);
                            System.out.println();
                        } else {
                            thumbnailServiceCache.add(name);
                            localThumbnailCache.add(name);
                        }

                    } catch (Exception e) {
                        System.err.println("Failed to execute ffmpeg command: " + e.getMessage());
                    } finally {
                        semaphore.release();
                    }
                }, executorService);

                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            addCacheThumbnails(localThumbnailCache.stream().toList(), System.currentTimeMillis() + 60 * 60 * 1000, null);
            localThumbnailCache.forEach(thumbnailServiceCache::remove);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
