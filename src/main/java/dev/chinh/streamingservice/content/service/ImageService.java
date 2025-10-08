package dev.chinh.streamingservice.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.OSUtil;
import dev.chinh.streamingservice.content.constant.MediaType;
import dev.chinh.streamingservice.content.constant.Resolution;
import dev.chinh.streamingservice.data.MediaMetaDataRepository;
import dev.chinh.streamingservice.data.entity.MediaDescriptor;
import io.minio.Result;
import io.minio.errors.*;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ImageService extends MediaService {

    public ImageService(RedisTemplate<String, Object> redisTemplate, MinIOService minIOService,
                        ObjectMapper objectMapper, MediaMetaDataRepository mediaRepository) {
        super(redisTemplate, minIOService, objectMapper, mediaRepository);
    }

    @PostConstruct
    public void init() {
        ImageIO.scanForPlugins();  // ensures WebP plugin is discovered
//        System.out.println("Registered writer formats: " + Arrays.toString(ImageIO.getWriterFormatNames()));
//        System.out.println("Registered reader formats: " + Arrays.toString(ImageIO.getReaderFormatNames()));
    }

    public record MediaUrl(MediaType type, String url) {}

    public List<MediaUrl> getAllMediaInAnAlbum(Long albumId, Resolution resolution) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        MediaDescriptor mediaDescriptor = getMediaDescriptor(albumId);
        Iterable<Result<Item>> results = minIOService.getAllItemsInBucketWithPrefix(mediaDescriptor.getBucket(), mediaDescriptor.getPath());
        List<MediaUrl> imageUrls = new ArrayList<>();
        for (Result<Item> result : results) {
            Item item = result.get();
            if (resolution == Resolution.original) {
                imageUrls.add(new MediaUrl(
                        MediaType.detectMediaType(item.objectName()),
                        String.format("/original/%s?key=%s", mediaDescriptor.getBucket(), item.objectName())
                ));
            } else {
                imageUrls.add(new MediaUrl(
                        MediaType.detectMediaType(item.objectName()),
                        String.format("/resize/%s?res=%s&key=%s", mediaDescriptor.getBucket(), Resolution.p1080, item.objectName())
                ));
            }
        }
        return imageUrls;
    }

    private MediaDescriptor getMediaDescriptor(Long albumId) {
        MediaDescriptor mediaDescriptor = getCachedMediaSearchItem(String.valueOf(albumId));
        if (mediaDescriptor == null) {
            mediaDescriptor = findMediaMetaDataAllInfo(albumId);
        }
        if (mediaDescriptor.hasKey())
            throw new IllegalStateException("Not an album, individual media found: " + albumId);
        return mediaDescriptor;
    }

    public ResponseEntity<Void> getResizedImageURL(String bucket, String key, Resolution res,
                                                   HttpServletRequest request) throws Exception {
        String originalExtension = key.contains(".") ? key.substring(key.lastIndexOf(".") + 1)
                .toLowerCase() : "jpg";
        //String acceptHeader = request.getHeader("Accept");
        //String format = (acceptHeader != null && acceptHeader.contains("image/webp")) ? "webp" : originalExtension;
        // sejda doesnt work mac silicon so skipping webp for now
        String format = originalExtension;

        // 2. Build cache path inside RAMDISK: /Volumes/RAMDISK/image-cache/{bucket}/{key}_{res}.{format}
        Path cacheRoot = Paths.get("/image-cache");
        Path bucketDir = cacheRoot.resolve(bucket);
        if (!OSUtil.createTempDir(bucketDir.toString())) {
            throw new IOException("Failed to create temporary directory: " + bucketDir);
        }

        // Append resolution + format to the file name
        String baseName = key.replaceAll("\\.[^.]+$", ""); // strip extension if present
        String cacheFileName = baseName + "_" + res.getResolution() + "." + format;
        Path cachePath = bucketDir.resolve(cacheFileName);

        if (!OSUtil.checkTempFileExists(cachePath.toString())) {
            // make sure parent dir exist
            if (!OSUtil.createTempDir(cachePath.getParent().toString())) {
                throw new IOException("Failed to create temporary directory: " + bucketDir);
            }
            try (InputStream is = minIOService.getFile(bucket, key);
                OutputStream os = Files.newOutputStream(cachePath)) {
                Thumbnails.of(is)
                        .size(res.getResolution(), res.getResolution())
                        .outputFormat(format)
                        .toOutputStream(os);
            }
        }

        String cachedImageUrl = "/cache/" + bucket + "/" + cacheFileName;
        HttpHeaders headers = new HttpHeaders();
        String encodedPath = UriUtils.encodePath(cachedImageUrl, StandardCharsets.UTF_8);
        headers.setLocation(URI.create(encodedPath));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    public ResponseEntity<Void> getOriginalImageURL(String bucket, String key, int expiry) throws Exception {
        String signedUrl = minIOService.getSignedUrlForHostNginx(bucket, key, expiry);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(signedUrl));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
