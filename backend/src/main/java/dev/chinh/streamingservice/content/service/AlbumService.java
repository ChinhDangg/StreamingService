package dev.chinh.streamingservice.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.OSUtil;
import dev.chinh.streamingservice.content.constant.MediaJobStatus;
import dev.chinh.streamingservice.content.constant.MediaType;
import dev.chinh.streamingservice.content.constant.Resolution;
import dev.chinh.streamingservice.data.repository.MediaMetaDataRepository;
import dev.chinh.streamingservice.data.entity.MediaDescription;
import dev.chinh.streamingservice.exception.ResourceNotFoundException;
import dev.chinh.streamingservice.search.service.MediaSearchService;
import io.minio.Result;
import io.minio.messages.Item;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.coyote.BadRequestException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AlbumService extends MediaService {

    private final VideoService videoService;

    public AlbumService(RedisTemplate<String, Object> redisTemplate, MinIOService minIOService,
                        ObjectMapper objectMapper, MediaMetaDataRepository mediaRepository,
                        VideoService videoService) {
        super(redisTemplate, minIOService, objectMapper, mediaRepository);
        this.videoService = videoService;
    }

    public record AlbumUrlInfo(List<MediaUrl> mediaUrlList, List<String> buckets, Resolution resolution, List<String> pathList) {}
    public record MediaUrl(MediaType type, String url) {}

    public List<MediaUrl> getAllMediaInAnAlbum(Long albumId, Resolution resolution, HttpServletRequest request) throws Exception {
        String albumCreationId = getCacheMediaJobId(albumId, resolution);
        var cacheUrls = getCacheAlbumCreationInfo(albumId, albumCreationId);
        cacheLastAccessForAlbum(albumCreationId, albumId);
        if (cacheUrls != null) {
            return cacheUrls.mediaUrlList;
        }

        AlbumUrlInfo albumUrlInfo = getAlbumUrlInfo(albumId, resolution, request);
        addCacheAlbumCreationInfo(albumId, albumCreationId, albumUrlInfo, true);

        processResizedAlbumImages(albumId, resolution, 0, 5, request);
        return albumUrlInfo.mediaUrlList;
    }

    private AlbumUrlInfo getAlbumUrlInfo(long albumId, Resolution resolution, HttpServletRequest request) throws Exception {
        MediaDescription mediaDescription = getMediaDescription(albumId);
        Iterable<Result<Item>> results = minIOService.getAllItemsInBucketWithPrefix(mediaDescription.getBucket(), mediaDescription.getPath());
        return (resolution == Resolution.original) ?
                getAlbumOriginalUrls(mediaDescription, results) :
                getAlbumResizedUrls(mediaDescription, albumId, resolution, results, request);
    }

    public void cacheLastAccessForAlbum(String albumCreationId, long albumId) {
        long now = System.currentTimeMillis() + 60 * 60 * 1000;
        addCacheLastAccess(albumCreationId, now);
        addCacheLastAccess(getAlbumCacheHashId(albumId), now);
    }

    private String getAlbumCacheHashId(long albumId) {
        return albumId + ":album";
    }

    // cautious as modifying albumUrlInfo list inside
    private void addCacheAlbumCreationInfo(long albumId, String albumCreateId, AlbumUrlInfo albumUrlInfo, boolean isInitial) throws JsonProcessingException {
        String albumHashId = getAlbumCacheHashId(albumId);

        if (isInitial) {
            String bucketJson = objectMapper.writeValueAsString(albumUrlInfo.buckets);
            redisTemplate.opsForHash().put(albumHashId, "buckets", bucketJson);
        }
        albumUrlInfo.buckets.clear();

        if (isInitial && albumUrlInfo.pathList != null) {
            String pathListJson = objectMapper.writeValueAsString(albumUrlInfo.pathList);
            redisTemplate.opsForHash().put(albumHashId, "pathList", pathListJson);
            albumUrlInfo.pathList.clear();
        }

        String json = objectMapper.writeValueAsString(albumUrlInfo);
        redisTemplate.opsForHash().put(albumHashId, albumCreateId, json);
    }

    public AlbumUrlInfo getCacheAlbumCreationInfo(long albumId, String albumCreateId) throws JsonProcessingException {
        String albumHashId = getAlbumCacheHashId(albumId);
        Object value = redisTemplate.opsForHash().get(albumHashId, albumCreateId);
        if (value == null)
            return null;

        AlbumUrlInfo albumUrlInfo = objectMapper.readValue((String) value, new TypeReference<>() {});
        List<String> bucketList = objectMapper.readValue(
                (String) redisTemplate.opsForHash().get(albumHashId, "buckets"), new TypeReference<>() {}
        );
        List<String> pathList = objectMapper.readValue(
                (String) redisTemplate.opsForHash().get(albumHashId, "pathList"), new TypeReference<>() {}
        );
        albumUrlInfo.buckets.addAll(bucketList);
        albumUrlInfo.pathList.addAll(pathList);

        return albumUrlInfo;
    }

    /**
     * Return nginx urls for resized image - may not have actual image.
     * Get the url list first then call for actual images or videos resize later.
     */
    private AlbumUrlInfo getAlbumResizedUrls(MediaDescription mediaDescription, long albumId, Resolution res,
                                             Iterable<Result<Item>> results, HttpServletRequest request) throws Exception {
        String albumDir = "/" + albumId + "/" + res.name();
        String acceptHeader = request.getHeader("Accept");

        List<String> pathList = new ArrayList<>(); // to get actual minio path for later resize
        List<MediaUrl> albumUrlList = new ArrayList<>();
        for (Result<Item> result : results) {
            Item item = result.get();
            String key = item.objectName().replace(mediaDescription.getPath() + "/", "");

            pathList.add(item.objectName());

            MediaType mediaType = MediaType.detectMediaType(key);

            if (mediaType == MediaType.VIDEO) {
                String videoDir = "/api/album/" + albumId + "/vid/" + albumUrlList.size();
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
        return new AlbumUrlInfo(albumUrlList, new ArrayList<>(List.of(mediaDescription.getBucket())), res, pathList);
    }

    private AlbumUrlInfo getAlbumOriginalUrls(MediaDescription mediaDescription, Iterable<Result<Item>> results) throws Exception {
        List<MediaUrl> albumUrls = new ArrayList<>();
        for (Result<Item> result : results) {
            Item item = result.get();

            MediaType mediaType = MediaType.detectMediaType(item.objectName());
            String originalVideoUrl = minIOService.getSignedUrlForHostNginx(mediaDescription.getBucket(),
                    item.objectName(), 60 * 60);
            albumUrls.add(new MediaUrl(mediaType, originalVideoUrl));
        }
        return new AlbumUrlInfo(albumUrls, new ArrayList<>(List.of(mediaDescription.getBucket())), Resolution.original, null);
    }

    public AlbumUrlInfo getMixThumbnailImagesAsAlbumUrls(List<MediaDescription> mediaDescriptionList, Resolution resolution) {
        List<String> pathList = new ArrayList<>();
        List<MediaUrl> albumUrlList = new ArrayList<>();
        List<String> bucketList = new ArrayList<>();
        for (MediaDescription mediaDescription : mediaDescriptionList) {
            if (!mediaDescription.hasThumbnail())
                continue;

            bucketList.add(mediaDescription.getBucket());
            pathList.add(mediaDescription.getThumbnail());

            String pathString = MediaSearchService.getThumbnailPath(mediaDescription.getId(), resolution, mediaDescription.getThumbnail());
            albumUrlList.add(new MediaUrl(MediaType.IMAGE, pathString));
        }
        return new AlbumUrlInfo(albumUrlList, bucketList, resolution, pathList);
    }

    public void processResizedAlbumImages(long albumId, Resolution resolution, int offset, int batch,
                                          HttpServletRequest request) throws Exception {
        String albumCreationId = getCacheMediaJobId(albumId, resolution);
        AlbumUrlInfo albumUrlInfo = getCacheAlbumCreationInfo(albumId, albumCreationId);
        if (albumUrlInfo == null) {
            System.out.println(("No cache found with albumId " + albumCreationId));
            albumUrlInfo = getAlbumUrlInfo(albumId, resolution, request);
        }

        boolean processed = processResizedImagesInBatch(albumUrlInfo, offset, batch, true);
        cacheLastAccessForAlbum(albumCreationId, albumId);

        if (processed)
            addCacheAlbumCreationInfo(albumId, albumCreationId, albumUrlInfo, false);
    }

    /**
     * Will modify the albumUrlInfo mediaUrlList in place to cache what image is already resized.
     */
    public boolean processResizedImagesInBatch(AlbumUrlInfo albumUrlInfo, int offset, int batch, boolean isAlbum) throws InterruptedException, IOException {
        int size = albumUrlInfo.mediaUrlList.size();
        if (offset >= size)
            return false;

        int stop = Math.min(size, offset + batch);
        Map<Integer, String> notResized = new HashMap<>();
        for (int i = offset; i < stop; i++) {
            MediaUrl currentMediaUrl = albumUrlInfo.mediaUrlList.get(i);
            String output = currentMediaUrl.url;
            if (output.indexOf("/chunks/") == 0) { // will use /chunks/ at start to check whether url is resized
                continue;
            }
            if (currentMediaUrl.type != MediaType.IMAGE) {
                continue;
            }
            output = "/chunks" + output;
            albumUrlInfo.mediaUrlList.set(i, new MediaUrl(currentMediaUrl.type, output));
            notResized.put(i, output);
        }

        if (notResized.isEmpty())
            return false;

        // Start one persistent bash session inside ffmpeg container
        ProcessBuilder pb = new ProcessBuilder("docker", "exec", "-i", "ffmpeg", "bash").redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {

            String path = albumUrlInfo.mediaUrlList.stream()
                    .filter(m -> m.type == MediaType.IMAGE)
                    .findFirst()
                    .orElse(albumUrlInfo.mediaUrlList.getFirst())
                    .url
                    .replaceFirst("/api/album", "")
                    .replaceFirst("/chunks", "");
            OSUtil.createTempDir(path.substring(0, path.lastIndexOf("/")));

            String scale = getFfmpegScaleString(albumUrlInfo.resolution, true);
            for (int i = offset; i < stop; i++) {
                String output = notResized.get(i);
                if (output == null) {
                    continue;
                }

                String bucket = isAlbum ? albumUrlInfo.buckets.getFirst() : albumUrlInfo.buckets.get(i);
                String input = minIOService.getSignedUrlForContainerNginx(bucket, albumUrlInfo.pathList.get(i),
                        60 * 60);

                String ffmpegCmd = String.format(
                        "ffmpeg -n -hide_banner -loglevel info " +
                                "-i \"%s\" -vf %s -q:v 2 \"%s\"",
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
        return true;
    }

    public String getAlbumVidCacheJobIdString(long albumId, int vidNum, Resolution resolution) {
        return albumId + ":" + vidNum + ":" + resolution;
    }

    public String getAlbumPartialVideoUrl(long albumId, int vidNum, Resolution res,
                                          HttpServletRequest request) throws Exception {
        final String albumCreationId = getCacheMediaJobId(albumId, Resolution.p1080);
        AlbumUrlInfo albumUrlInfo = getCacheAlbumCreationInfo(albumId, albumCreationId);
        if (albumUrlInfo == null) {
            System.out.println(("No cache found with albumId " + albumCreationId));
            albumUrlInfo = getAlbumUrlInfo(albumId, Resolution.p1080, request);
        }

        final String albumVidCacheJobId = getAlbumVidCacheJobIdString(albumId, vidNum, res);

        String videoPath = albumUrlInfo.pathList.get(vidNum);
        if (albumUrlInfo.mediaUrlList.get(vidNum).type != MediaType.VIDEO)
            throw new BadRequestException("Invalid video path: " + albumVidCacheJobId);

        long now = System.currentTimeMillis();
        addCacheLastAccess(albumVidCacheJobId, now);
        cacheLastAccessForAlbum(albumCreationId, albumId);

        final String videoDir = albumVidCacheJobId.replace(":", "/");

        MediaJobStatus mediaJobStatus = getJobStatus(albumVidCacheJobId);
        boolean prevJobStopped = false;
        if (mediaJobStatus != null) {
            if (!mediaJobStatus.equals(MediaJobStatus.STOPPED))
                return getNginxVideoStreamUrl(videoDir);
            else
                prevJobStopped = true;
        }

        String bucket = albumUrlInfo.buckets.getFirst();
        String input = minIOService.getSignedUrlForContainerNginx(bucket, videoPath, 60 * 60);

        OSUtil.createTempDir(videoDir);

        String containerDir = "/chunks/" + videoDir;
        String outPath = containerDir + masterFileName;

        String scale = getFfmpegScaleString(res, false);
        String partialVideoJobId = videoService.createPartialVideo(input, scale, videoDir, outPath, prevJobStopped, albumVidCacheJobId);

        long objectSize = minIOService.getObjectSize(bucket, videoPath);
        videoService.addCacheTempVideoJobStatus(albumVidCacheJobId, partialVideoJobId, objectSize, MediaJobStatus.RUNNING);
        videoService.addCacheRunningJob(albumVidCacheJobId);

        videoService.checkPlaylistCreated(videoDir + masterFileName);

        return getNginxVideoStreamUrl("album-vid/" + videoDir);
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

        String nginxUrl = minIOService.getSignedUrlForContainerNginx(
                mediaDescription.getBucket(), mediaPath, 30 * 60);

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
}
