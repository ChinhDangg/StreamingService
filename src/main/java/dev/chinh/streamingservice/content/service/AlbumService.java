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
import io.minio.errors.*;
import io.minio.messages.Item;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class ImageService extends MediaService {

    public ImageService(RedisTemplate<String, Object> redisTemplate, MinIOService minIOService,
                        ObjectMapper objectMapper, MediaMetaDataRepository mediaRepository) {
        super(redisTemplate, minIOService, objectMapper, mediaRepository);
    }

    public record MediaUrl(MediaType type, String url) {}

    public List<MediaUrl> getAllMediaInAnAlbum(Long albumId, Resolution resolution) throws Exception {
        String albumCreationId = albumId + ":" + resolution;
        var cacheUrls = getCacheAlbumCreation(albumCreationId);
        if (cacheUrls != null) {
            return cacheUrls;
        }

        MediaDescription mediaDescription = getMediaDescription(albumId);
        Iterable<Result<Item>> results = minIOService.getAllItemsInBucketWithPrefix(mediaDescription.getBucket(), mediaDescription.getPath());
        List<MediaUrl> imageUrls = new ArrayList<>();
        for (Result<Item> result : results) {
            Item item = result.get();
            if (resolution == Resolution.original) {
                imageUrls.add(new MediaUrl(
                        MediaType.detectMediaType(item.objectName()),
                        String.format("/original/%s?key=%s", albumId,
                                item.objectName().replace(mediaDescription.getPath() + "/", ""))
                ));
            } else {
                imageUrls.add(new MediaUrl(
                        MediaType.detectMediaType(item.objectName()),
                        String.format("/resize/%s?res=%s&key=%s", albumId, Resolution.p1080,
                                item.objectName().replace(mediaDescription.getPath() + "/", ""))
                ));
            }
        }
        addCacheLastAccess(albumCreationId, System.currentTimeMillis() + 60 * 60 * 1000);
        addCacheAlbumCreation(albumCreationId, imageUrls);
        return imageUrls;
    }

    private void addCacheAlbumCreation(String albumCreateId, List<MediaUrl> imageUrls) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(imageUrls);
        redisTemplate.opsForValue().set(albumCreateId, json, Duration.ofHours(1));
    }

    private List<MediaUrl> getCacheAlbumCreation(String albumCreateId) throws JsonProcessingException {
        Object value = redisTemplate.opsForValue().get(albumCreateId);
        if (value == null)
            return null;
        return objectMapper.readValue((String) value, new TypeReference<>() {});
    }

    public ResponseEntity<Void> getResizedImageURL(Long albumId, String key, Resolution res,
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

        String scale = String.format("\"scale='if(gt(iw,ih),-2,min(ih,%d))':'if(gt(ih,iw),-2,min(iw,%d))'\"",
                res.getResolution(), res.getResolution());
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

    public ResponseEntity<Void> getOriginalImageURL(Long albumId, String key, int expiry) throws Exception {
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
