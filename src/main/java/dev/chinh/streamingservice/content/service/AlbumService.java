package dev.chinh.streamingservice.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.OSUtil;
import dev.chinh.streamingservice.content.constant.MediaJobStatus;
import dev.chinh.streamingservice.content.constant.MediaType;
import dev.chinh.streamingservice.content.constant.Resolution;
import dev.chinh.streamingservice.data.MediaMetaDataRepository;
import dev.chinh.streamingservice.data.entity.MediaDescription;
import dev.chinh.streamingservice.exception.ResourceNotFoundException;
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
        var cacheUrls = getCacheAlbumCreationInfo(albumCreationId);
        if (cacheUrls != null) {
            return cacheUrls.mediaUrlList;
        }

        MediaDescription mediaDescription = getMediaDescription(albumId);
        Iterable<Result<Item>> results = minIOService.getAllItemsInBucketWithPrefix(mediaDescription.getBucket(), mediaDescription.getPath());
        AlbumUrlInfo albumUrlInfo = (resolution == Resolution.original) ?
                getAlbumOriginalUrls(mediaDescription, results) :
                getAlbumResizedUrls(mediaDescription, albumId, resolution, results, request);

        addCacheLastAccess(albumCreationId, System.currentTimeMillis() + 60 * 60 * 1000);
        addCacheAlbumCreationInfo(albumCreationId, albumUrlInfo);
        processResizedAlbumImages(albumId, resolution, 0, 5);
        return albumUrlInfo.mediaUrlList;
    }

    private void addCacheAlbumCreationInfo(String albumCreateId, AlbumUrlInfo albumUrlInfo) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(albumUrlInfo);
        redisTemplate.opsForValue().set(albumCreateId, json);
    }

    public AlbumUrlInfo getCacheAlbumCreationInfo(String albumCreateId) throws JsonProcessingException {
        Object value = redisTemplate.opsForValue().get(albumCreateId);
        if (value == null)
            return null;
        return objectMapper.readValue((String) value, new TypeReference<>() {});
    }

    /**
     * Return nginx urls for resized image - may not have actual image.
     * Get the url list first then call for actual images or videos resize later.
     */
    private AlbumUrlInfo getAlbumResizedUrls(MediaDescription mediaDescription, long albumId, Resolution res,
                                             Iterable<Result<Item>> results, HttpServletRequest request) throws Exception {
        Path albumDir = Paths.get("/album-cache" + "/" + albumId + "/" + res.name());
        String acceptHeader = request.getHeader("Accept");

        int vidCount = 0;
        List<String> pathList = new ArrayList<>(); // to get actual minio path for later resize
        List<MediaUrl> albumUrlList = new ArrayList<>();
        for (Result<Item> result : results) {
            Item item = result.get();
            String key = item.objectName().replace(mediaDescription.getPath() + "/", "");

            pathList.add(item.objectName());

            MediaType mediaType = MediaType.detectMediaType(key);

            if (mediaType == MediaType.VIDEO) {
                String videoDir = "/api/videos/album-vid/" + albumId + "/vid/" + ++vidCount;
                albumUrlList.add(new MediaUrl(mediaType, getNginxVideoStreamUrl(videoDir)));
                continue;
            }

            Path cachePath = getFileUrlPath(key, acceptHeader, res, albumDir);
            albumUrlList.add(new MediaUrl(mediaType, cachePath.toString()));
        }
        return new AlbumUrlInfo(albumUrlList, List.of(mediaDescription.getBucket()), res, pathList);
    }

    private Path getFileUrlPath(String key, String acceptHeader, Resolution res, Path albumDir) {
        // Append resolution + format to the file name
        String originalExtension = key.contains(".") ? key.substring(key.lastIndexOf(".") + 1)
                .toLowerCase() : "jpg";
        String baseName = key.replaceAll("\\.[^.]+$", ""); // strip extension if present
        String format = (acceptHeader != null && acceptHeader.contains("image/webp")) ? "webp" : originalExtension;
        String cacheFileName = baseName + "_" + res.getResolution() + "." + format;
        return albumDir.resolve(cacheFileName);
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
        return new AlbumUrlInfo(albumUrls, List.of(mediaDescription.getBucket()), null, null);
    }

    private AlbumUrlInfo getMixThumbnailImagesAsAlbumUrls(List<MediaDescription> mediaDescriptionList, Resolution resolution,
                                                          String acceptHeader) {
        Path albumDir = Paths.get("/thumbnail-cache/" + resolution.name());

        List<String> pathList = new ArrayList<>();
        List<MediaUrl> albumUrlList = new ArrayList<>();
        List<String> bucketList = new ArrayList<>();
        for (MediaDescription mediaDescription : mediaDescriptionList) {
            bucketList.add(mediaDescription.getBucket());
            pathList.add(mediaDescription.getThumbnail());

            Path cachePath = getFileUrlPath(String.valueOf(mediaDescription.getId()), acceptHeader, resolution, albumDir);
            albumUrlList.add(new MediaUrl(MediaType.IMAGE, cachePath.toString()));
        }
        return new AlbumUrlInfo(albumUrlList, bucketList, resolution, pathList);
    }

    public void processResizedAlbumImages(long albumId, Resolution resolution, int offset, int batch) throws IOException, InterruptedException {
        String albumCreationId = getCacheMediaJobId(albumId, resolution);
        AlbumUrlInfo albumUrlInfo = getCacheAlbumCreationInfo(albumCreationId);

        if (albumUrlInfo == null)
            throw new RuntimeException("No cache found with albumId " + albumCreationId);
        boolean processed = processResizedImagesInBatch(albumUrlInfo, offset, batch, true);

        if (processed)
            addCacheAlbumCreationInfo(albumCreationId, albumUrlInfo);
    }

    public boolean processResizedImagesInBatch(AlbumUrlInfo albumUrlInfo, int offset, int batch, boolean isAlbum) throws InterruptedException, IOException {
        int size = albumUrlInfo.mediaUrlList.size();
        if (offset > size)
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

            Path path = Paths.get(albumUrlInfo.mediaUrlList.getFirst().url.replaceFirst("/chunks", ""));
            if (!OSUtil.createTempDir(path.getParent().toString())) {
                throw new IOException("Failed to create temporary directory: " + path.getParent());
            }

            String scale = getFfmpegScaleString(albumUrlInfo.resolution);
            //int stop = Math.min(size, offset + batch);
            for (int i = offset; i < stop; i++) {
                String bucket = isAlbum ? albumUrlInfo.buckets.getFirst() : albumUrlInfo.buckets.get(i);
                String input = minIOService.getSignedUrlForContainerNginx(bucket, albumUrlInfo.pathList.get(i),
                        60 * 60);
                String output = notResized.get(i);
                if (output == null) {
                    continue;
                }

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

    public String getAlbumPartialVideoUrl(long albumId, int vidNum, Resolution res) throws Exception {
        final String albumCreationId = getCacheMediaJobId(albumId, res);
        AlbumService.AlbumUrlInfo albumUrlInfo = getCacheAlbumCreationInfo(albumCreationId);
        if (albumUrlInfo == null)
            throw new RuntimeException("No cache found with albumId " + albumCreationId);

        final String albumVidCacheJobId = albumId + ":" + vidNum + ":" + res;

        String videoPath = albumUrlInfo.pathList.get(vidNum);
        if (MediaType.detectMediaType(videoPath) != MediaType.VIDEO)
            throw new BadRequestException("Invalid video path: " + albumVidCacheJobId);

        long now = System.currentTimeMillis();
        addCacheLastAccess(albumVidCacheJobId, now);
        addCacheLastAccess(albumCreationId, now);

        final String videoDir = albumVidCacheJobId.replace(":", "/");

        MediaJobStatus mediaJobStatus = getJobStatus(albumVidCacheJobId);
        boolean prevJobStopped = false;
        if (mediaJobStatus != null) {
            if (!mediaJobStatus.equals(MediaJobStatus.STOPPED))
                return "/stream/" + videoDir + masterFileName;
            else
                prevJobStopped = true;
        }

        String bucket = albumUrlInfo.buckets.getFirst();
        String input = minIOService.getSignedUrlForContainerNginx(bucket, videoPath, 60 * 60);

        if (!OSUtil.createTempDir(videoDir)) {
            throw new IOException("Failed to create temporary directory: " + videoDir);
        }

        String containerDir = "/chunks/" + videoDir;
        String outPath = containerDir + masterFileName;

        String scale = getFfmpegScaleString(res);
        String partialVideoJobId = videoService.createPartialVideo(input, scale, videoDir, outPath, prevJobStopped, albumVidCacheJobId);

        long objectSize = minIOService.getObjectSize(bucket, videoPath);
        videoService.addCacheTempVideoJobStatus(albumVidCacheJobId, partialVideoJobId, objectSize, MediaJobStatus.RUNNING);
        videoService.addCacheRunningJob(albumVidCacheJobId);

        videoService.checkPlaylistCreated(videoDir + masterFileName);

        return getNginxVideoStreamUrl(videoDir);
    }

    public ResponseEntity<Void> processResizedImageURLAsRedirectResponse(Long albumId, String key, Resolution res,
                                                                         HttpServletRequest request) throws Exception {
        MediaDescription mediaDescription = getMediaDescription(albumId);

        String originalExtension = key.contains(".") ? key.substring(key.lastIndexOf(".") + 1)
                .toLowerCase() : "jpg";
        String acceptHeader = request.getHeader("Accept");
        String format = (acceptHeader != null && acceptHeader.contains("image/webp")) ? "webp" : originalExtension;

        Path mediaPath = Paths.get(mediaDescription.getPath(), key);
        if (!minIOService.objectExists(mediaDescription.getBucket(), mediaPath.toString())) {
            throw new ResourceNotFoundException("Object not found: " + key);
        }

        String nginxUrl = minIOService.getSignedUrlForContainerNginx(
                mediaDescription.getBucket(), mediaPath.toString(), 30 * 60);

        // 2. Build cache path {temp dir}/album-cache/{res}/{key}_{res}.{format}
        Path cacheRoot = Paths.get("/album-cache");
        Path albumIdDir = cacheRoot.resolve(String.valueOf(albumId));
        Path albumDir = albumIdDir.resolve(res.name());

        // Append resolution + format to the file name
        String baseName = key.replaceAll("\\.[^.]+$", ""); // strip extension if present
        String cacheFileName = baseName + "_" + res.getResolution() + "." + format;
        Path cachePath = albumDir.resolve(cacheFileName);

        String scale = getFfmpegScaleString(res);
        if (!OSUtil.checkTempFileExists(cachePath.toString())) {
            // make sure parent dir exist
            if (!OSUtil.createTempDir(cachePath.getParent().toString())) {
                throw new IOException("Failed to create temporary directory: " + cachePath.getParent());
            }

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
            runAndLog(dockerCmd, null);
        }

        String cachedImageUrl = String.valueOf(cachePath);
        return getUrlAsRedirectResponse(cachedImageUrl, true);
    }

    private String getFfmpegScaleString(Resolution resolution) {
        return String.format(
                "\"scale='if(gte(iw,ih),-2,min(iw,%1$d))':'if(gte(ih,iw),-2,min(ih,%1$d))'\"",
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

    public ResponseEntity<Void> getOriginalImageURLAsRedirectResponse(Long albumId, String key, int expiry) throws Exception {
        MediaDescription mediaDescription = getMediaDescription(albumId);
        String signedUrl = minIOService.getSignedUrlForHostNginx(mediaDescription.getBucket(),
                Paths.get(mediaDescription.getPath(), key).toString(), expiry);
        return getUrlAsRedirectResponse(signedUrl, false);
    }

    private ResponseEntity<Void> getUrlAsRedirectResponse(String signedUrl, boolean encodePath) {
        String encodedPath = encodePath ? UriUtils.encodePath(signedUrl, StandardCharsets.UTF_8) : signedUrl;
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(encodedPath));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
