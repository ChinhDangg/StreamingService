package dev.chinh.streamingservice.content.service;

import dev.chinh.streamingservice.content.constant.Resolution;
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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class ImageService extends MediaService {

    public ImageService(RedisTemplate<String, Object> redisTemplate, MinIOService minIOService) {
        super(redisTemplate, minIOService);
    }

    @PostConstruct
    public void init() {
        ImageIO.scanForPlugins();  // ensures WebP plugin is discovered
//        System.out.println("Registered writer formats: " + Arrays.toString(ImageIO.getWriterFormatNames()));
//        System.out.println("Registered reader formats: " + Arrays.toString(ImageIO.getReaderFormatNames()));
    }

    public ResponseEntity<Void> getResizedImageToResponse(String bucket, String key, Resolution res,
                                                          HttpServletRequest request) throws Exception {
        String originalExtension = key.contains(".") ? key.substring(key.lastIndexOf(".") + 1)
                .toLowerCase() : "jpg";
        //String acceptHeader = request.getHeader("Accept");
        //String format = (acceptHeader != null && acceptHeader.contains("image/webp")) ? "webp" : originalExtension;
        // sejda doesnt work mac silicon so skipping webp for now
        String format = originalExtension;

        // 2. Build cache path inside RAMDISK: /Volumes/RAMDISK/image-cache/{bucket}/{key}_{res}.{format}
        Path cacheRoot = Paths.get("/Volumes/RAMDISK/image-cache");
        Path bucketDir = cacheRoot.resolve(bucket);
        Files.createDirectories(bucketDir);

        // Append resolution + format to the file name
        String baseName = key.replaceAll("\\.[^.]+$", ""); // strip extension if present
        String cacheFileName = baseName + "_" + res.getResolution() + "." + format;
        Path cachePath = bucketDir.resolve(cacheFileName);

        // make sure parent dir exist
        Files.createDirectories(cachePath.getParent());

        if (!Files.exists(cachePath)) {
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
