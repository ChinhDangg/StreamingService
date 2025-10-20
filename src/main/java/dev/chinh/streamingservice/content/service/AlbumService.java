package dev.chinh.streamingservice.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.OSUtil;
import dev.chinh.streamingservice.content.constant.MediaType;
import dev.chinh.streamingservice.content.constant.Resolution;
import dev.chinh.streamingservice.data.MediaMetaDataRepository;
import dev.chinh.streamingservice.data.entity.MediaDescription;
import dev.chinh.streamingservice.exception.ResourceNotFoundException;
import io.minio.Result;
import io.minio.messages.Item;
import jakarta.servlet.http.HttpServletRequest;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class AlbumService extends MediaService {

    public AlbumService(RedisTemplate<String, Object> redisTemplate, MinIOService minIOService,
                        ObjectMapper objectMapper, MediaMetaDataRepository mediaRepository) {
        super(redisTemplate, minIOService, objectMapper, mediaRepository);
    }

    public record MediaUrlPairs(List<MediaUrl> mediaUrlList, String bucket, List<String> pathList) {}
    public record MediaUrl(MediaType type, String url) {}

    public List<MediaUrl> getAllMediaInAnAlbum(Long albumId, Resolution resolution, HttpServletRequest request) throws Exception {
        String albumCreationId = albumId + ":" + resolution;
        var cacheUrls = getCacheAlbumCreation(albumCreationId);
        if (cacheUrls != null) {
            return cacheUrls.mediaUrlList;
        }

        MediaDescription mediaDescription = getMediaDescription(albumId);
        Iterable<Result<Item>> results = minIOService.getAllItemsInBucketWithPrefix(mediaDescription.getBucket(), mediaDescription.getPath());
        MediaUrlPairs mediaUrlPairs = (resolution == Resolution.original) ?
                getAlbumOriginalUrls(mediaDescription, results) :
                getAlbumResizedUrls(mediaDescription, albumId, resolution, results, request);

        addCacheLastAccess(albumCreationId, System.currentTimeMillis() + 60 * 60 * 1000);
        addCacheAlbumCreation(albumCreationId, mediaUrlPairs);
        return mediaUrlPairs.mediaUrlList;
    }

    private void addCacheAlbumCreation(String albumCreateId, MediaUrlPairs mediaUrlPairs) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(mediaUrlPairs);
        redisTemplate.opsForValue().set(albumCreateId, json, Duration.ofHours(1));
    }

    public MediaUrlPairs getCacheAlbumCreation(String albumCreateId) throws JsonProcessingException {
        Object value = redisTemplate.opsForValue().get(albumCreateId);
        if (value == null)
            return null;
        return objectMapper.readValue((String) value, new TypeReference<>() {});
    }

    /**
     * Return nginx urls for resized image - may not have actual image.
     * Get the url list first then call for actual images or videos resize later.
     */
    private MediaUrlPairs getAlbumResizedUrls(MediaDescription mediaDescription, long albumId, Resolution res,
                                               Iterable<Result<Item>> results, HttpServletRequest request) throws Exception {
        Path albumDir = Paths.get("/album-cache" + "/" + albumId + "/" + res.name());
        String acceptHeader = request.getHeader("Accept");

        List<String> pathList = new ArrayList<>(); // to get actual minio path for later resize
        List<MediaUrl> albumUrlList = new ArrayList<>();
        for (Result<Item> result : results) {
            Item item = result.get();
            String key = item.objectName().replace(mediaDescription.getPath() + "/", "");

            pathList.add(item.objectName());

            MediaType mediaType = MediaType.detectMediaType(key);

            if (mediaType == MediaType.VIDEO) {
                String videoDir = albumId + "/" + key + "/" + Resolution.p720;
                albumUrlList.add(new MediaUrl(mediaType, getNginxVideoStreamUrl(videoDir)));
                continue;
            }

            // Append resolution + format to the file name
            String originalExtension = key.contains(".") ? key.substring(key.lastIndexOf(".") + 1)
                    .toLowerCase() : "jpg";
            String baseName = key.replaceAll("\\.[^.]+$", ""); // strip extension if present
            String format = (acceptHeader != null && acceptHeader.contains("image/webp")) ? "webp" : originalExtension;
            String cacheFileName = baseName + "_" + res.getResolution() + "." + format;
            Path cachePath = albumDir.resolve(cacheFileName);
            albumUrlList.add(new MediaUrl(mediaType, cachePath.toString()));
        }
        return new MediaUrlPairs(albumUrlList, mediaDescription.getBucket(), pathList);
    }

    private MediaUrlPairs getAlbumOriginalUrls(MediaDescription mediaDescription, Iterable<Result<Item>> results) throws Exception {
        List<MediaUrl> albumUrls = new ArrayList<>();
        for (Result<Item> result : results) {
            Item item = result.get();

            MediaType mediaType = MediaType.detectMediaType(item.objectName());
            String originalVideoUrl = minIOService.getSignedUrlForHostNginx(mediaDescription.getBucket(),
                    item.objectName(), 60 * 60);
            albumUrls.add(new MediaUrl(mediaType, originalVideoUrl));
        }
        return new MediaUrlPairs(albumUrls, mediaDescription.getBucket(), null);
    }

    public void processResizedImagesInBatch(Resolution resolution, MediaUrlPairs mediaUrlPairs, int offset, int batch) throws InterruptedException, IOException {
        int size = mediaUrlPairs.mediaUrlList.size();
        if (offset > size)
            return;

        // Start one persistent bash session inside ffmpeg container
        ProcessBuilder pb = new ProcessBuilder("docker", "exec", "-i", "ffmpeg", "bash").redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {

            Path path = Paths.get(mediaUrlPairs.mediaUrlList.getFirst().url);
            if (!OSUtil.createTempDir(path.getParent().toString())) {
                throw new IOException("Failed to create temporary directory: " + path.getParent());
            }

            String scale = getFfmpegScaleString(resolution);
            int stop = Math.min(size, offset + batch);
            for (int i = offset; i < stop; i++) {
                String input = minIOService.getSignedUrlForContainerNginx(mediaUrlPairs.bucket, mediaUrlPairs.pathList.get(i),
                        60 * 60);
                String output = "/chunks" + mediaUrlPairs.mediaUrlList.get(i).url;
                String ffmpegCmd = String.format(
                        "ffmpeg -y -hide_banner -loglevel info " +
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
//        try (BufferedReader reader = new BufferedReader(
//                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
//            reader.lines().forEach(System.out::println);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }

        int exit = process.waitFor();
        System.out.println("ffmpeg exited with code " + exit);
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
