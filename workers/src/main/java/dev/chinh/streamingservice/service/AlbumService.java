package dev.chinh.streamingservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.constant.MediaJobStatus;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.constant.Resolution;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import io.minio.Result;
import io.minio.messages.Item;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class AlbumService extends MediaService implements ResourceCleanable, JobHandler {

    private final MemoryManager memoryManager;
    private final VideoService videoService;

    public AlbumService(@Qualifier("queueRedisTemplate") RedisTemplate<String, String> redisTemplate,
                        ObjectMapper objectMapper,
                        MinIOService minIOService,
                        WorkerRedisService workerRedisService,
                        MemoryManager memoryManager,
                        VideoService videoService) {
        super(redisTemplate, objectMapper, minIOService, workerRedisService);
        this.memoryManager = memoryManager;
        this.videoService = videoService;
    }

    private final int expirySeconds = 60 * 60; // 1 hour

    public record MediaUrl(MediaType type, String url) {}
    public record AlbumUrlInfo(List<MediaUrl> mediaUrlList, List<String> buckets, List<String> pathList) {}
    public record AlbumUrlInfoWithSizeAndDimensions(AlbumUrlInfo albumUrlInfo, SizeAndDimensions sizeAndDimensions) {}
    public record SizeAndDimensions(long size, int width, int height, int length) {}

    @Override
    public void handleJob(String tokenKey, MediaJobDescription description) {
        try {
            workerRedisService.updateStatus(description.getWorkId(), MediaJobStatus.RUNNING.name());
            switch (description.getJobType()) {
                case "albumUrlList" -> {
                    var mediaUrlList = getAllMediaUrlInAnAlbum(description);
                    String mediaUrlListString = objectMapper.writeValueAsString(mediaUrlList);
                    workerRedisService.addResultToStatus(description.getWorkId(), "result", mediaUrlListString);
                    workerRedisService.addResultToStatus(description.getWorkId(), "offset",
                            objectMapper.writeValueAsString(List.of(description.getOffset() + description.getBatch(), mediaUrlList.size())));
                    workerRedisService.releaseToken(tokenKey);
                }
                case "checkResized" -> {
                    String offset = objectMapper.writeValueAsString(processResizedAlbumImagesInBatch(description));
                    workerRedisService.addResultToStatus(description.getWorkId(), "offset", offset);
                    workerRedisService.releaseToken(tokenKey);
                }
                case "albumVideoUrl" -> {
                    String videoPartialUrl = getAlbumPartialVideoUrl(tokenKey, description);
                    workerRedisService.addResultToStatus(description.getWorkId(), "result", videoPartialUrl);
                }
                default -> throw new BadRequestException("Invalid jobType: " + description.getJobType());
            }
            workerRedisService.updateStatus(description.getWorkId(), MediaJobStatus.COMPLETED.name());
        } catch (Exception e) {
            workerRedisService.releaseToken(tokenKey);
            throw new RuntimeException(e);
        }
    }

    private List<MediaUrl> getAllMediaUrlInAnAlbum(MediaJobDescription mediaJobDescription) throws Exception {
        long albumId = mediaJobDescription.getId();
        Resolution resolution = mediaJobDescription.getResolution();

        String albumCreationId = getCacheMediaJobIdString(albumId, resolution);
        List<MediaUrl> cacheUrls = getCacheAlbumCreatedUrl(albumId, albumCreationId);
        if (cacheUrls != null) {
            return cacheUrls;
        }

        var albumInfo = getAlbumInfoAndAddToCache(mediaJobDescription);
        if (resolution != Resolution.original) {
            mediaJobDescription.setOffset(0);
            mediaJobDescription.setBatch(5);
            processResizedAlbumImagesInBatch(mediaJobDescription);
        }

        return albumInfo.albumUrlInfo.mediaUrlList;
    }

    private AlbumUrlInfo getAlbumOriginalUrls(MediaJobDescription mediaJobDescription) throws Exception {
        List<String> pathList = getCacheAlbumPathList(mediaJobDescription.getId());

        if (pathList == null) {
            Iterable<Result<Item>> results = minIOService.getAllItemsInBucketWithPrefix(mediaJobDescription.getBucket(), mediaJobDescription.getPath());
            pathList = new ArrayList<>();
            for (Result<Item> result : results)
                pathList.add(result.get().objectName());
        }

        List<MediaUrl> albumUrls = new ArrayList<>();
        for (String path : pathList) {
            MediaType mediaType = MediaType.detectMediaType(path);
            String originalVideoUrl = minIOService.getSignedUrlForHostNginx(mediaJobDescription.getBucket(),
                    path, expirySeconds);
            albumUrls.add(new MediaUrl(mediaType, originalVideoUrl));
        }
        return new AlbumUrlInfo(albumUrls, new ArrayList<>(List.of(mediaJobDescription.getBucket())), pathList);
    }

    /**
     * Return nginx urls for resized image - may not have actual image.
     * Get the url list first then call for actual images or videos resize later.
     */
    private AlbumUrlInfo getAlbumResizedUrls(MediaJobDescription mediaJobDescription) throws Exception {
        long albumId = mediaJobDescription.getId();
        Resolution res = mediaJobDescription.getResolution();

        String albumDir = "/stream/album/" + albumId + "/" + res.name();
        String acceptHeader = mediaJobDescription.getAcceptHeader();

        List<MediaUrl> albumUrlList = new ArrayList<>();
        List<String> albumPathList = getCacheAlbumPathList(albumId);

        if (albumPathList == null) {
            albumPathList = new ArrayList<>();
            Iterable<Result<Item>> results = minIOService.getAllItemsInBucketWithPrefix(mediaJobDescription.getBucket(), mediaJobDescription.getPath());
            for (Result<Item> result : results) {
                albumPathList.add(result.get().objectName());
            }
        }

        for (String path : albumPathList) {
            String key = path.substring(path.lastIndexOf("/") + 1);

            MediaType mediaType = MediaType.detectMediaType(key);

            if (mediaType == MediaType.VIDEO) {
                String videoDir = "/api/album/" + albumId + "/" + res + "/vid/" + albumUrlList.size();
                albumUrlList.add(new MediaUrl(mediaType, videoDir));
                continue;
            }

            String originalExtension = key.contains(".") ? key.substring(key.lastIndexOf(".") + 1)
                    .toLowerCase() : "jpg";
            String format = (acceptHeader != null && acceptHeader.contains("image/webp")) ? "webp" : originalExtension;
            String cacheFileName = albumUrlList.size() + "_" + res.getResolution() + "." + format;
            String cachePath = OSUtil.normalizePath(albumDir, cacheFileName);
            albumUrlList.add(new MediaUrl(mediaType, cachePath));
        }
        return new AlbumUrlInfo(albumUrlList, new ArrayList<>(List.of(mediaJobDescription.getBucket())), albumPathList);
    }

    private List<Integer> processResizedAlbumImagesInBatch(MediaJobDescription mediaJobDescription) throws Exception {
        long albumId = mediaJobDescription.getId();
        Resolution resolution = mediaJobDescription.getResolution();
        int offset = mediaJobDescription.getOffset();
        int batch = mediaJobDescription.getBatch();

        if (resolution == Resolution.original)
            throw new BadRequestException("Cannot process original images in batch");

        String albumJobId = getCacheMediaJobIdString(albumId, resolution);

        int previousProcessedOffset = getCacheAlbumJobInfo(albumId, albumJobId);
        System.out.println("previousOffset: " + previousProcessedOffset);
        if (previousProcessedOffset >= offset + batch) {
            return List.of(previousProcessedOffset, -1);
        }

        List<MediaUrl> mediaUrlList = getCacheAlbumCreatedUrl(albumId, albumJobId);

        AlbumUrlInfoWithSizeAndDimensions albumInfo = getCacheAlbumCreationInfoWithSizeAndDimensions(albumId);
        if (albumInfo == null) {
            System.out.println(("No cache found with albumId " + albumId));
            albumInfo = getAlbumInfoAndAddToCache(mediaJobDescription);
        }

        if (mediaUrlList == null) {
            mediaUrlList = albumInfo.albumUrlInfo.mediaUrlList;
        }

        AlbumUrlInfo albumUrlInfo = albumInfo.albumUrlInfo;

        int size = albumUrlInfo.pathList.size();
        if (offset >= size)
            return List.of(size, size);

        int stop = Math.min(size-1, previousProcessedOffset + batch);
        List<Integer> notResized = new ArrayList<>();
        for (int i = previousProcessedOffset; i <= stop; i++) {
            if (MediaType.detectMediaType(albumUrlInfo.pathList.get(i)) != MediaType.IMAGE)
                continue;
            notResized.add(i);
        }

        if (notResized.isEmpty()) {
            addCacheAlbumJobInfo(albumId, albumJobId, previousProcessedOffset + batch);
            return List.of(previousProcessedOffset + batch, size);
        }

        AlbumUrlInfo notResizedAlbumUrlInfo = new AlbumUrlInfo(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        notResizedAlbumUrlInfo.buckets.add(albumUrlInfo.buckets.getFirst());
        String albumDir = albumId + "/" + resolution;
        for (int i : notResized) {
            notResizedAlbumUrlInfo.mediaUrlList.add(new MediaUrl(mediaUrlList.get(i).type,
                    OSUtil.normalizePath("/chunks/" + albumDir, mediaUrlList.get(i).url.substring(mediaUrlList.get(i).url.lastIndexOf("/") + 1))));
            notResizedAlbumUrlInfo.pathList.add(albumUrlInfo.pathList.get(i));
        }

        SizeAndDimensions sizeAndDimensions = albumInfo.sizeAndDimensions;
        long estimatedSize = notResized.size() * Resolution.getEstimatedSize(sizeAndDimensions.size, sizeAndDimensions.width, sizeAndDimensions.height, resolution) / size;
        boolean enough = memoryManager.freeMemoryForSize(estimatedSize);
        if (!enough) {
            return List.of(previousProcessedOffset, size);
        }

        int exitCode = processResizedImagesInBatch(notResizedAlbumUrlInfo, resolution, albumDir, true);
        if (exitCode != 0) {
            throw new RuntimeException("Failed to resize images in batch for albumId " + albumId + " and resolution " + resolution + " with exit code " + exitCode);
        }
        addCacheAlbumJobInfo(albumId, albumJobId, previousProcessedOffset + notResized.size());

        System.out.println("offset after: " + (previousProcessedOffset + notResized.size()));
        return List.of(previousProcessedOffset + notResized.size(), size);
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
                String input = minIOService.getSignedUrlForContainerNginx(bucket, albumUrlInfo.pathList.get(i), expirySeconds);

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

        List<String> logs = getLogsFromInputStream(process.getInputStream());

        int exit = process.waitFor();
        System.out.println("ffmpeg Resizing Images exited with code " + exit);
        if (exit != 0) {
            logs.forEach(System.out::println);
        }
        return exit;
    }

    public String getAlbumVidCacheJobIdString(long albumId, int vidNum, Resolution resolution) {
        return albumId + ":" + vidNum + ":" + resolution;
    }

    private String getAlbumPartialVideoUrl(String tokenKey, MediaJobDescription mediaJobDescription) throws Exception {
        long albumId = mediaJobDescription.getId();
        Resolution albumRes = mediaJobDescription.getResolution();
        int vidNum = mediaJobDescription.getVidNum();
        Resolution res = mediaJobDescription.getVidResolution();

        AlbumUrlInfo albumUrlInfo = getCacheAlbumCreationInfo(albumId);
        if (albumUrlInfo == null) {
            System.out.println(("No cache found with albumId " + albumId));
            albumUrlInfo = getAlbumInfoAndAddToCache(mediaJobDescription).albumUrlInfo;
        }

        final String albumVidCacheJobId = getAlbumVidCacheJobIdString(albumId, vidNum, res);

        String videoPath = albumUrlInfo.pathList.get(vidNum);
        if (MediaType.detectMediaType(albumUrlInfo.pathList.get(vidNum)) != MediaType.VIDEO)
            throw new BadRequestException("Invalid video path: " + albumVidCacheJobId);

        String bucket = albumUrlInfo.buckets.getFirst();

        if (res == Resolution.original) {
            return minIOService.getSignedUrlForHostNginx(bucket, videoPath, expirySeconds);
        }

        List<String> streamUrlPaths = List.of(
                "album-vid",
                String.valueOf(albumId),
                albumRes.name(),
                String.valueOf(vidNum),
                res.name()
        );
        String streamUrl = getNginxVideoStreamUrl(String.join("/", streamUrlPaths));

        MediaJobStatus mediaJobStatus = videoService.getVideoJobStatus(albumVidCacheJobId);
        boolean prevJobStopped = false;
        if (mediaJobStatus != null) {
            if (!mediaJobStatus.equals(MediaJobStatus.STOPPED))
                return streamUrl;
            else
                prevJobStopped = true;
        }

        long objectSize = minIOService.getObjectSize(bucket, videoPath);
        boolean enoughSpace = memoryManager.freeMemoryForSize(objectSize);
        if (!enoughSpace)
            return minIOService.getSignedUrlForHostNginx(bucket, videoPath, expirySeconds);

        final String videoDir = albumVidCacheJobId.replace(":", "/");
        OSUtil.createTempDir(videoDir);

        String containerDir = "/chunks/" + videoDir;
        String outPath = containerDir + masterFileName;

        String scale = getFfmpegScaleString(res, false);
        String input = minIOService.getSignedUrlForContainerNginx(bucket, videoPath, expirySeconds);
        String partialVideoJobId = videoService.createPartialVideo(tokenKey, input, scale, videoDir, outPath, prevJobStopped, albumVidCacheJobId);

        videoService.addCacheVideoJobStatus(albumVidCacheJobId, partialVideoJobId, objectSize, MediaJobStatus.RUNNING);
        videoService.addCacheRunningJob(albumVidCacheJobId);

        videoService.checkPlaylistCreated(videoDir + masterFileName);
        return streamUrl;
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


    private String getAlbumCacheHashIdString(long albumId) {
        return albumId + ":album";
    }

    private final String albumLastAccessKey = "cache:lastAccess:album";

    public void removeAlbumCacheLastAccess(String albumMediaCreatedId) {
        removeCacheLastAccess(albumLastAccessKey, albumMediaCreatedId);
    }

    public Set<ZSetOperations.TypedTuple<String>> getAllAlbumCacheLastAccess(long max) {
        return getAllCacheLastAccess(albumLastAccessKey, max);
    }

    private AlbumUrlInfoWithSizeAndDimensions getAlbumInfoAndAddToCache(MediaJobDescription mediaJobDescription) throws Exception {
        long albumId = mediaJobDescription.getId();
        Resolution resolution = mediaJobDescription.getResolution();

        AlbumUrlInfo albumUrlInfo = (resolution == Resolution.original) ?
                getAlbumOriginalUrls(mediaJobDescription) :
                getAlbumResizedUrls(mediaJobDescription);
        SizeAndDimensions sizeAndDimensions = new SizeAndDimensions(mediaJobDescription.getSize(), mediaJobDescription.getWidth(), mediaJobDescription.getHeight(), mediaJobDescription.getLength());
        addCacheAlbumCreationInfo(albumId, albumUrlInfo, sizeAndDimensions);
        addCacheAlbumCreatedUrl(albumId, getCacheMediaJobIdString(albumId, resolution), albumUrlInfo.mediaUrlList);
        return new AlbumUrlInfoWithSizeAndDimensions(albumUrlInfo, sizeAndDimensions);
    }

    private AlbumUrlInfoWithSizeAndDimensions getCacheAlbumCreationInfoWithSizeAndDimensions(long albumId) throws JsonProcessingException {
        AlbumUrlInfo albumUrlInfo = getCacheAlbumCreationInfo(albumId);
        if (albumUrlInfo == null)
            return null;

        String albumHashId = getAlbumCacheHashIdString(albumId);
        Object sizeAndDimensionsValue = redisTemplate.opsForHash().get(albumHashId, albumId + ":sizeAndDimensions");
        if (sizeAndDimensionsValue == null)
            return new AlbumUrlInfoWithSizeAndDimensions(albumUrlInfo, null);
        SizeAndDimensions sizeAndDimensions = objectMapper.readValue((String) sizeAndDimensionsValue, new TypeReference<>() {});
        return new AlbumUrlInfoWithSizeAndDimensions(albumUrlInfo, sizeAndDimensions);
    }

    private void addCacheAlbumJobInfo(long albumId, String albumJobId, int offset) {
        String albumHashId = getAlbumCacheHashIdString(albumId);
        redisTemplate.opsForHash().put(albumHashId, albumJobId + ":offset", String.valueOf(offset));
    }

    private int getCacheAlbumJobInfo(long albumId, String albumJobId) {
        String albumHashId = getAlbumCacheHashIdString(albumId);
        Object offset = redisTemplate.opsForHash().get(albumHashId, albumJobId + ":offset");
        if (offset == null)
            return 0;
        return Integer.parseInt((String) offset);
    }

    private void removeCacheAlbumJobInfo(long albumId, String albumJobId) {
        String albumHashId = getAlbumCacheHashIdString(albumId);
        redisTemplate.opsForHash().delete(albumHashId, albumJobId + ":offset");
    }

    private void addCacheAlbumCreatedUrl(long albumId, String albumJobId, List<MediaUrl> mediaUrlList) throws JsonProcessingException {
        String albumHashId = getAlbumCacheHashIdString(albumId);
        String mediaUrlListString = objectMapper.writeValueAsString(mediaUrlList);
        redisTemplate.opsForHash().put(albumHashId, albumJobId, mediaUrlListString);
    }

    private List<MediaUrl> getCacheAlbumCreatedUrl(long albumId, String albumJobId) throws JsonProcessingException {
        String albumHashId = getAlbumCacheHashIdString(albumId);
        Object value = redisTemplate.opsForHash().get(albumHashId, albumJobId);
        if (value == null)
            return null;
        return objectMapper.readValue((String) value, new TypeReference<>() {});
    }

    private void removeCacheAlbumCreatedUrl(long albumId, String albumJobId) {
        String albumHashId = getAlbumCacheHashIdString(albumId);
        redisTemplate.opsForHash().delete(albumHashId, albumJobId);
    }

    private List<String> getCacheAlbumPathList(long albumId) throws JsonProcessingException {
        Object pathListValue = redisTemplate.opsForHash().get(getAlbumCacheHashIdString(albumId), albumId + ":pathList");
        if (pathListValue == null)
            return null;
        return objectMapper.readValue((String) pathListValue, new TypeReference<>() {});
    }

    private SizeAndDimensions getCacheAlbumSizeAndDimensions(long albumId) throws JsonProcessingException {
        Object sizeAndDimensionsValue = redisTemplate.opsForHash().get(getAlbumCacheHashIdString(albumId), albumId + ":sizeAndDimensions");
        if (sizeAndDimensionsValue == null)
            return null;
        return objectMapper.readValue((String) sizeAndDimensionsValue, new TypeReference<>() {});
    }

    private void addCacheAlbumCreationInfo(long albumId, AlbumUrlInfo albumUrlInfo, SizeAndDimensions sizeAndDimensions) throws JsonProcessingException {
        String albumHashId = getAlbumCacheHashIdString(albumId);

        redisTemplate.opsForHash().put(albumHashId, "bucket", albumUrlInfo.buckets.getFirst());

        if (albumUrlInfo.pathList != null) {
            String pathListString = objectMapper.writeValueAsString(albumUrlInfo.pathList);
            redisTemplate.opsForHash().put(albumHashId, albumId + ":pathList", pathListString);
        }

        String sizeAndDimensionsString = objectMapper.writeValueAsString(sizeAndDimensions);
        redisTemplate.opsForHash().put(albumHashId, albumId + ":sizeAndDimensions", sizeAndDimensionsString);
    }

    private AlbumUrlInfo getCacheAlbumCreationInfo(long albumId) throws JsonProcessingException {
        String albumHashId = getAlbumCacheHashIdString(albumId);
        Object pathListValue = redisTemplate.opsForHash().get(albumHashId, albumId + ":pathList");
        if (pathListValue == null)
            return null;
        List<String> pathList = objectMapper.readValue((String) pathListValue, new TypeReference<>() {});
        List<String> buckets = new ArrayList<>();
        buckets.add((String) redisTemplate.opsForHash().get(albumHashId, "bucket"));
        return new AlbumUrlInfo(new ArrayList<>(), buckets, pathList);
    }

    public void removeCacheAlbumCreationInfo(String albumHashId) {
        redisTemplate.opsForHash().delete(albumHashId);
    }

    /**
     * Only free album image info. Videos in album is handled by video service free memory
     */
    @Override
    public boolean freeMemorySpaceForSize(long size) {
        if (OSUtil.getUsableMemory() >= size) {
            OSUtil.updateUsableMemory(-size);
            return true;
        }
        if (size > OSUtil.MEMORY_TOTAL) {
            System.out.println("Memory limit reached: " + size / 1000 + " MB");
            return false;
        }

        long headRoom = (long) (OSUtil.MEMORY_TOTAL * 0.1);
        long neededSpace = size + headRoom - OSUtil.getUsableMemory();
        neededSpace = (neededSpace > OSUtil.MEMORY_TOTAL) ? size : neededSpace;

        long removingSpace = neededSpace;
        boolean enough = false;

        long now = System.currentTimeMillis();
        Set<ZSetOperations.TypedTuple<String>> lastAccessMediaJob = getAllAlbumCacheLastAccess(now);
        for (ZSetOperations.TypedTuple<String> mediaJob : lastAccessMediaJob) {
            if (removingSpace <= 0) {
                enough = true;
                break;
            }

            long millisPassed = (long) (now - mediaJob.getScore());
            if (millisPassed < 60_000) {
                // zset is already sort, so if one found still being active - then the rest after is the same
                if (removingSpace - headRoom > 0) {
                    System.out.println("Most content is still being active, can't remove enough memory");
                    System.out.println("Serve original size from disk instead or wait");
                }
                break;
            }

            String mediaJobId = Objects.requireNonNull(mediaJob.getValue());
            System.out.println("Removing: " + mediaJobId);

            String[] mediaJobIdParts = mediaJobId.split(":");
            long albumId = Long.parseLong(mediaJobIdParts[0]);
            Resolution resolution = Resolution.valueOf(mediaJobIdParts[1]);

            // avoid removing entire album info (pathList, bucket, size) in making space
            // (only remove the album job which actually has memory data usage)
            if (mediaJobId.endsWith(":album")) {
                continue;
            }

            try {
                SizeAndDimensions sizeAndDimensions = getCacheAlbumSizeAndDimensions(albumId);
                assert sizeAndDimensions != null;
                long estimatedSize = Resolution.getEstimatedSize(
                        sizeAndDimensions.size, sizeAndDimensions.width, sizeAndDimensions.height, resolution) / sizeAndDimensions.length;
                removingSpace -= estimatedSize;
                System.out.println("Estimated size: " + estimatedSize);
            } catch (Exception e) {
                System.out.println("Failed to get estimated size to estimate removing space");
            }

            String mediaMemoryPath = mediaJobId.replace(":", "/");
            OSUtil.deleteForceMemoryDirectory(mediaMemoryPath);
            removeCacheAlbumJobInfo(albumId, mediaJobId);
            removeCacheAlbumCreatedUrl(albumId, mediaJobId);
            removeAlbumCacheLastAccess(mediaJobId);
            removeJobStatus(mediaJobId);
        }
        if (!enough && removingSpace - headRoom <= 0)
            enough = true;
        OSUtil.updateUsableMemory(neededSpace - removingSpace); // removingSpace is negative or 0
        return enough;
    }
}
