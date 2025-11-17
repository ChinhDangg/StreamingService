package dev.chinh.streamingservice.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.MediaMapper;
import dev.chinh.streamingservice.OSUtil;
import dev.chinh.streamingservice.content.constant.MediaJobStatus;
import dev.chinh.streamingservice.content.constant.MediaType;
import dev.chinh.streamingservice.content.constant.Resolution;
import dev.chinh.streamingservice.data.repository.MediaMetaDataRepository;
import dev.chinh.streamingservice.data.entity.MediaDescription;
import dev.chinh.streamingservice.data.service.MediaMetadataService;
import dev.chinh.streamingservice.exception.ResourceNotFoundException;
import io.minio.Result;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.coyote.BadRequestException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class AlbumService extends MediaService implements ResourceCleanable {

    private final VideoService videoService;
    private final MemoryManager memoryManager;

    public AlbumService(RedisTemplate<String, Object> redisTemplate,
                        ObjectMapper objectMapper, MediaMapper mediaMapper,
                        MediaMetaDataRepository mediaRepository,
                        MinIOService minIOService,
                        MediaMetadataService mediaMetadataService,
                        VideoService videoService,
                        MemoryManager memoryManager) {
        super(redisTemplate, objectMapper, mediaMapper, mediaRepository, minIOService, mediaMetadataService);
        this.videoService = videoService;
        this.memoryManager = memoryManager;
    }

    @PostConstruct
    public void registerWithMemoryManager() {
        memoryManager.registerResourceCleanable(this);
    }

    private final int expirySeconds = 60 * 60; // 1 hour

    public record MediaUrl(MediaType type, String url) {}
    public record AlbumUrlInfo(List<MediaUrl> mediaUrlList, List<String> buckets, List<String> pathList) {}
    public record AlbumUrlInfoWithSizeAndDimensions(AlbumUrlInfo albumUrlInfo, SizeAndDimensions sizeAndDimensions) {}
    public record SizeAndDimensions(long size, int width, int height) {}

    public List<MediaUrl> getAllMediaInAnAlbum(Long albumId, Resolution resolution, HttpServletRequest request) throws Exception {
        String albumCreationId = getCacheMediaJobId(albumId, resolution);
        List<MediaUrl> cacheUrls = getCacheAlbumCreatedUrl(albumId, albumCreationId);
        addCacheAlbumLastAccess(albumId, albumCreationId);
        if (cacheUrls != null) {
            return cacheUrls;
        }

        var albumInfo = getAlbumInfoAndAddToCache(albumId, resolution, request);
        processResizedAlbumImagesInBatch(albumId, resolution, 0, 5, request);

        return albumInfo.albumUrlInfo.mediaUrlList;
    }

    private final String albumLastAccessKey = "cache:lastAccess:album";
    public void addCacheAlbumLastAccess(long albumId, String albumCreationId) {
        long now = System.currentTimeMillis() + expirySeconds * 1000;
        addCacheLastAccess(albumLastAccessKey, albumCreationId, now);
        addCacheLastAccess(albumLastAccessKey, getAlbumCacheHashId(albumId), now);
    }

    public void addCacheAlbumVideoLastAccess(long albumId, String albumVidCacheJobId, Resolution albumRes) {
        long now = System.currentTimeMillis() + expirySeconds * 1000;
        videoService.addCacheVideoLastAccess(albumVidCacheJobId, now);
        addCacheLastAccess(albumLastAccessKey, getCacheMediaJobId(albumId, albumRes), now);
        addCacheLastAccess(albumLastAccessKey, getAlbumCacheHashId(albumId), now);
    }

    private void removeAlbumCacheLastAccess(String albumMediaCreatedId) {
        removeCacheLastAccess(albumLastAccessKey, albumMediaCreatedId);
    }

    public Set<ZSetOperations.TypedTuple<Object>> getAllAlbumCacheLastAccess(long max) {
        return getAllCacheLastAccess(albumLastAccessKey, max);
    }

    private String getAlbumCacheHashId(long albumId) {
        return albumId + ":album";
    }

    private void addCacheAlbumCreationInfo(long albumId, AlbumUrlInfo albumUrlInfo, SizeAndDimensions sizeAndDimensions) throws JsonProcessingException {
        String albumHashId = getAlbumCacheHashId(albumId);

        redisTemplate.opsForHash().put(albumHashId, "bucket", albumUrlInfo.buckets.getFirst());

        String pathListString = objectMapper.writeValueAsString(albumUrlInfo.pathList);
        redisTemplate.opsForHash().put(albumHashId, albumId + ":pathList", pathListString);

        String sizeAndDimensionsString = objectMapper.writeValueAsString(sizeAndDimensions);
        redisTemplate.opsForHash().put(albumHashId, albumId + ":sizeAndDimensions", sizeAndDimensionsString);
    }

    public AlbumUrlInfo getCacheAlbumCreationInfo(long albumId) throws JsonProcessingException {
        String albumHashId = getAlbumCacheHashId(albumId);
        Object pathListValue = redisTemplate.opsForHash().get(albumHashId, albumId + ":pathList");
        if (pathListValue == null)
            return null;
        List<String> pathList = objectMapper.readValue((String) pathListValue, new TypeReference<>() {});
        List<String> buckets = new ArrayList<>();
        buckets.add((String) redisTemplate.opsForHash().get(albumHashId, "bucket"));
        return new AlbumUrlInfo(new ArrayList<>(), buckets, pathList);
    }

    public AlbumUrlInfoWithSizeAndDimensions getCacheAlbumCreationInfoWithSizeAndDimensions(long albumId) throws JsonProcessingException {
        AlbumUrlInfo albumUrlInfo = getCacheAlbumCreationInfo(albumId);
        if (albumUrlInfo == null)
            return null;

        String albumHashId = getAlbumCacheHashId(albumId);
        Object sizeAndDimensionsValue = redisTemplate.opsForHash().get(albumHashId, albumId + ":sizeAndDimensions");
        if (sizeAndDimensionsValue == null)
            return new AlbumUrlInfoWithSizeAndDimensions(albumUrlInfo, null);
        SizeAndDimensions sizeAndDimensions = objectMapper.readValue((String) sizeAndDimensionsValue, new TypeReference<>() {});
        return new AlbumUrlInfoWithSizeAndDimensions(albumUrlInfo, sizeAndDimensions);
    }

    private void addCacheAlbumJobInfo(long albumId, String albumJobId, int offset) {
        String albumHashId = getAlbumCacheHashId(albumId);
        redisTemplate.opsForHash().put(albumHashId, albumJobId + ":offset", offset);
    }

    private int getCacheAlbumJobInfo(long albumId, String albumJobId) {
        String albumHashId = getAlbumCacheHashId(albumId);
        Object offset = redisTemplate.opsForHash().get(albumHashId, albumJobId + ":offset");
        if (offset == null)
            return -1;
        return (int) offset;
    }

    private void removeCacheAlbumJobInfo(long albumId, String albumJobId) {
        String albumHashId = getAlbumCacheHashId(albumId);
        redisTemplate.opsForHash().delete(albumHashId, albumJobId + ":offset");
    }

    private void addCacheAlbumCreatedUrl(long albumId, String albumJobId, List<MediaUrl> mediaUrlList) throws JsonProcessingException {
        String albumHashId = getAlbumCacheHashId(albumId);
        String mediaUrlListString = objectMapper.writeValueAsString(mediaUrlList);
        redisTemplate.opsForHash().put(albumHashId, albumJobId, mediaUrlListString);
    }

    private List<MediaUrl> getCacheAlbumCreatedUrl(long albumId, String albumJobId) throws JsonProcessingException {
        String albumHashId = getAlbumCacheHashId(albumId);
        Object value = redisTemplate.opsForHash().get(albumHashId, albumJobId);
        if (value == null)
            return null;
        return objectMapper.readValue((String) value, new TypeReference<>() {});
    }

    private void removeCacheAlbumCreatedUrl(long albumId, String albumJobId) {
        String albumHashId = getAlbumCacheHashId(albumId);
        redisTemplate.opsForHash().delete(albumHashId, albumJobId);
    }

    private List<String> getCacheAlbumPathList(long albumId) throws JsonProcessingException {
        Object pathListValue = redisTemplate.opsForHash().get(getAlbumCacheHashId(albumId), albumId + ":pathList");
        if (pathListValue == null)
            return null;
        return objectMapper.readValue((String) pathListValue, new TypeReference<>() {});
    }

    private SizeAndDimensions getCacheAlbumSizeAndDimensions(long albumId) throws JsonProcessingException {
        Object sizeAndDimensionsValue = redisTemplate.opsForHash().get(getAlbumCacheHashId(albumId), albumId + ":sizeAndDimensions");
        if (sizeAndDimensionsValue == null)
            return null;
        return objectMapper.readValue((String) sizeAndDimensionsValue, new TypeReference<>() {});
    }

    private AlbumUrlInfoWithSizeAndDimensions getAlbumInfoAndAddToCache(long albumId, Resolution resolution, HttpServletRequest request) throws Exception {
        MediaDescription mediaDescription = getMediaDescription(albumId);
        AlbumUrlInfo albumUrlInfo = (resolution == Resolution.original) ?
                getAlbumOriginalUrls(mediaDescription) :
                getAlbumResizedUrls(mediaDescription, albumId, resolution, request);
        SizeAndDimensions sizeAndDimensions = new SizeAndDimensions(mediaDescription.getSize(), mediaDescription.getWidth(), mediaDescription.getHeight());
        addCacheAlbumCreationInfo(albumId, albumUrlInfo, sizeAndDimensions);
        addCacheAlbumCreatedUrl(albumId, getCacheMediaJobId(albumId, resolution), albumUrlInfo.mediaUrlList);
        return new AlbumUrlInfoWithSizeAndDimensions(albumUrlInfo, sizeAndDimensions);
    }

    /**
     * Return nginx urls for resized image - may not have actual image.
     * Get the url list first then call for actual images or videos resize later.
     */
    private AlbumUrlInfo getAlbumResizedUrls(MediaDescription mediaDescription, long albumId, Resolution res,
                                             HttpServletRequest request) throws Exception {
        String albumDir = "/stream/album/" + albumId + "/" + res.name();
        String acceptHeader = request.getHeader("Accept");

        List<MediaUrl> albumUrlList = new ArrayList<>();
        List<String> albumPathList = getCacheAlbumPathList(albumId);

        if (albumPathList == null) {
            albumPathList = new ArrayList<>();
            Iterable<Result<Item>> results = minIOService.getAllItemsInBucketWithPrefix(mediaDescription.getBucket(), mediaDescription.getPath());
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
        return new AlbumUrlInfo(albumUrlList, new ArrayList<>(List.of(mediaDescription.getBucket())), albumPathList);
    }

    private AlbumUrlInfo getAlbumOriginalUrls(MediaDescription mediaDescription) throws Exception {
        List<String> pathList = getCacheAlbumPathList(mediaDescription.getId());

        if (pathList == null) {
            Iterable<Result<Item>> results = minIOService.getAllItemsInBucketWithPrefix(mediaDescription.getBucket(), mediaDescription.getPath());
            pathList = new ArrayList<>();
            for (Result<Item> result : results)
                pathList.add(result.get().objectName());
        }

        List<MediaUrl> albumUrls = new ArrayList<>();
        for (String path : pathList) {
            MediaType mediaType = MediaType.detectMediaType(path);
            String originalVideoUrl = minIOService.getSignedUrlForHostNginx(mediaDescription.getBucket(),
                    path, expirySeconds);
            albumUrls.add(new MediaUrl(mediaType, originalVideoUrl));
        }
        return new AlbumUrlInfo(albumUrls, new ArrayList<>(List.of(mediaDescription.getBucket())), null);
    }

    public int processResizedAlbumImagesInBatch(long albumId, Resolution resolution, int offset, int batch,
                                                 HttpServletRequest request) throws Exception {
        String albumJobId = getCacheMediaJobId(albumId, resolution);
        int previousProcessedOffset = getCacheAlbumJobInfo(albumId, albumJobId);
        if (previousProcessedOffset >= offset + batch) {
            return previousProcessedOffset;
        }

        List<MediaUrl> mediaUrlList = getCacheAlbumCreatedUrl(albumId, albumJobId);

        AlbumUrlInfoWithSizeAndDimensions albumInfo = getCacheAlbumCreationInfoWithSizeAndDimensions(albumId);
        if (albumInfo == null) {
            System.out.println(("No cache found with albumId " + albumId));
            albumInfo = getAlbumInfoAndAddToCache(albumId, resolution, request);
        }

        if (mediaUrlList == null) {
            mediaUrlList = albumInfo.albumUrlInfo.mediaUrlList;
        }

        AlbumUrlInfo albumUrlInfo = albumInfo.albumUrlInfo;

        int size = albumUrlInfo.pathList.size();
        if (offset >= size)
            return 0;

        int stop = Math.min(size, offset + batch);
        List<Integer> notResized = new ArrayList<>();
        for (int i = offset; i < stop; i++) {
            if (MediaType.detectMediaType(albumUrlInfo.pathList.get(i)) != MediaType.IMAGE)
                continue;
            notResized.add(i);
        }

        if (notResized.isEmpty())
            return 0;

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
            return -1;
        }

        processResizedImagesInBatch(notResizedAlbumUrlInfo, resolution, albumDir, true);

        addCacheAlbumLastAccess(albumId, albumJobId);
        addCacheAlbumJobInfo(albumId, albumJobId, offset + batch);
        return offset + batch;
    }

    public void processResizedImagesInBatch(AlbumUrlInfo albumUrlInfo, Resolution resolution, String albumDir, boolean isAlbum) throws InterruptedException, IOException {
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

        // Capture ffmpeg combined logs
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines().forEach(System.out::println);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int exit = process.waitFor();
        System.out.println("ffmpeg exited with code " + exit);
    }

    public String getAlbumVidCacheJobIdString(long albumId, int vidNum, Resolution resolution) {
        return albumId + ":" + vidNum + ":" + resolution;
    }

    public String getAlbumPartialVideoUrl(long albumId, Resolution albumRes, int vidNum, Resolution res,
                                          HttpServletRequest request) throws Exception {
        AlbumUrlInfo albumUrlInfo = getCacheAlbumCreationInfo(albumId);
        if (albumUrlInfo == null) {
            System.out.println(("No cache found with albumId " + albumId));
            albumUrlInfo = getAlbumInfoAndAddToCache(albumId, albumRes, request).albumUrlInfo;
        }

        final String albumVidCacheJobId = getAlbumVidCacheJobIdString(albumId, vidNum, res);

        String videoPath = albumUrlInfo.pathList.get(vidNum);
        if (MediaType.detectMediaType(albumUrlInfo.pathList.get(vidNum)) != MediaType.VIDEO)
            throw new BadRequestException("Invalid video path: " + albumVidCacheJobId);

        addCacheAlbumVideoLastAccess(albumId, albumVidCacheJobId, albumRes);

        String bucket = albumUrlInfo.buckets.getFirst();

        if (res == Resolution.original) {
            return minIOService.getSignedUrlForHostNginx(bucket, videoPath, expirySeconds);
        }

        final String videoDir = albumVidCacheJobId.replace(":", "/");
        String streamUrl = getNginxVideoStreamUrl("album-vid/" + videoDir);

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

        OSUtil.createTempDir(videoDir);

        String containerDir = "/chunks/" + videoDir;
        String outPath = containerDir + masterFileName;

        String scale = getFfmpegScaleString(res, false);
        String input = minIOService.getSignedUrlForContainerNginx(bucket, videoPath, expirySeconds);
        String partialVideoJobId = videoService.createPartialVideo(input, scale, videoDir, outPath, prevJobStopped, albumVidCacheJobId);

        videoService.addCacheVideoJobStatus(albumVidCacheJobId, partialVideoJobId, objectSize, MediaJobStatus.RUNNING);
        videoService.addCacheRunningJob(albumVidCacheJobId);

        videoService.checkPlaylistCreated(videoDir + masterFileName);
        return streamUrl;
    }

    public ResponseEntity<Void> processResizedImageURLAsRedirectResponse(Long albumId, String key, Resolution res,
                                                                         HttpServletRequest request) throws Exception {
        MediaDescription mediaDescription = getMediaDescription(albumId);

        String originalExtension = key.contains(".") ? key.substring(key.lastIndexOf(".") + 1)
                .toLowerCase() : "jpg";
        String acceptHeader = request.getHeader("Accept");
        String format = (acceptHeader != null && acceptHeader.contains("image/webp")) ? "webp" : originalExtension;

        String mediaPath = OSUtil.normalizePath(mediaDescription.getPath(), key);
        if (!minIOService.objectExists(mediaDescription.getBucket(), mediaPath)) {
            throw new ResourceNotFoundException("Object not found: " + key);
        }

        String nginxUrl = minIOService.getSignedUrlForContainerNginx(mediaDescription.getBucket(), mediaPath, expirySeconds);

        // 2. Build cache path {temp dir}/{albumId}/{res}/{key}_{res}.{format}
        String albumDir = OSUtil.normalizePath(String.valueOf(albumId), res.name());

        // Append resolution + format to the file name
        String baseName = key.replaceAll("\\.[^.]+$", ""); // strip extension if present
        String cacheFileName = baseName + "_" + res.getResolution() + "." + format;
        String cachePath = OSUtil.normalizePath(albumDir, cacheFileName);

        String scale = getFfmpegScaleString(res, true);
        if (!OSUtil.checkTempFileExists(cachePath)) {
            // make sure parent dir exist
            OSUtil.createTempDir(albumDir);

            String outputPath = "/chunks" + cachePath;
            String ffmpegCmd = String.format(
                    "ffmpeg -y -hide_banner -loglevel info " +
                            "-i \"%s\" -vf %s -q:v 2 \"%s\"",
                    nginxUrl, scale, outputPath
            );
            String[] dockerCmd = {
                    "docker", "exec", "ffmpeg",
                    "bash", "-c", ffmpegCmd
            };
            OSUtil.runCommandAndLog(dockerCmd, null);
        }

        return getUrlAsRedirectResponse(cachePath, true);
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

    @Override
    protected MediaDescription getMediaDescription(long albumId) {
        MediaDescription mediaDescription = super.getMediaDescription(albumId);
        if (mediaDescription.hasKey())
            throw new IllegalStateException("Not an album, individual media found: " + albumId);
        return mediaDescription;
    }

    public MediaDescription getMediaDescriptionGeneral(long mediaId) {
        return super.getMediaDescription(mediaId);
    }

    public ResponseEntity<Void> getOriginalImageURLAsRedirectResponse(Long albumId, String key, int expiry) throws Exception {
        MediaDescription mediaDescription = getMediaDescription(albumId);
        String signedUrl = minIOService.getSignedUrlForHostNginx(mediaDescription.getBucket(),
                OSUtil.normalizePath(mediaDescription.getPath(), key), expiry);
        return getUrlAsRedirectResponse(signedUrl, false);
    }

    private ResponseEntity<Void> getUrlAsRedirectResponse(String signedUrl, boolean encodePath) {
        String encodedPath = encodePath ? UriUtils.encodePath(signedUrl, StandardCharsets.UTF_8) : signedUrl;
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(encodedPath));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    /**
     * Only free album image info. Videos in album is handled by video service free memory
     */
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
        Set<ZSetOperations.TypedTuple<Object>> lastAccessMediaJob = getAllAlbumCacheLastAccess(now);
        for (ZSetOperations.TypedTuple<Object> mediaJob : lastAccessMediaJob) {
            if (removingSpace <= 0) {
                enough = true;
                break;
            }

            long millisPassed = (long) (System.currentTimeMillis() - mediaJob.getScore());
            if (millisPassed < 60_000) {
                // zset is already sort, so if one found still being active - then the rest after is the same
                if (removingSpace - headRoom > 0) {
                    System.out.println("Most content is still being active, can't remove enough memory");
                    System.out.println("Serve original size from disk instead or wait");
                }
                break;
            }

            String mediaJobId = Objects.requireNonNull(mediaJob.getValue()).toString();
            System.out.println("Removing: " + mediaJobId);
            long albumId = Long.parseLong(mediaJobId.split(":")[0]);

            Long estimatedSize = 1L;//getCacheAlbumCreationSize(albumId);
            if (estimatedSize == null) {
                System.out.println("Failed to get size for album creation info");
                continue;
            }
            removingSpace = removingSpace - estimatedSize;

            if (mediaJobId.endsWith(":album")) {
                redisTemplate.opsForHash().delete(albumLastAccessKey);
            } else {
                String mediaMemoryPath = mediaJobId.replace(":", "/");
                OSUtil.deleteForceMemoryDirectory(mediaMemoryPath);
                removeCacheAlbumJobInfo(albumId, mediaJobId);
            }

            removeAlbumCacheLastAccess(mediaJobId);
        }
        if (!enough && removingSpace - headRoom <= 0)
            enough = true;
        OSUtil.updateUsableMemory(neededSpace - removingSpace); // removingSpace is negative or 0
        return enough;
    }
}
