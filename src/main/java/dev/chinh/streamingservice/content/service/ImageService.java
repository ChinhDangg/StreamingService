package dev.chinh.streamingservice.content.service;

import dev.chinh.streamingservice.content.constant.Resolution;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
public class ImageService {

    private final MinIOService minIOService;

    @PostConstruct
    public void init() {
        ImageIO.scanForPlugins();  // ensures WebP plugin is discovered
    }

    public ResponseEntity<Void> getResizedImageToResponse(String bucket, String key, Resolution res,
                                                          HttpServletRequest request) throws Exception {
        String acceptHeader = request.getHeader("Accept");
        String originalExtension = key.contains(".") ? key.substring(key.lastIndexOf(".") + 1)
                .toLowerCase() : "jpg";
        String format = (acceptHeader != null && acceptHeader.contains("image/webp")) ? "webp" : originalExtension;

        // 2. Build cache path inside RAMDISK: /Volumes/RAMDISK/image-cache/{bucket}/{key}_{res}.{format}
        Path cacheRoot = Paths.get("/Volumes/RAMDISK/image-cache");
        Path bucketDir = cacheRoot.resolve(bucket);
        Files.createDirectories(bucketDir);

        // Append resolution + format to the file name
        String baseName = key.replaceAll("\\.[^.]+$", ""); // strip extension if present
        String cacheFileName = baseName + "_" + res.getResolution() + "." + format;
        Path cachePath = bucketDir.resolve(cacheFileName);

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
        headers.setLocation(URI.create(cachedImageUrl));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
