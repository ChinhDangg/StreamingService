package dev.chinh.streamingservice.filemanager.service;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class MinIOService {

    private final MinioClient minioClient;

    @Value("${minio.container.url}")
    private String minioContainerUrl;

    public Iterable<Result<Item>> getAllItemsInBucketWithPrefix(String bucketName, String prefix) {
        return minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .prefix(prefix)
                        .recursive(true)    // list everything under this prefix
                        .build()
        );
    }

    public boolean objectExists(String bucket, String key) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .build()
            );
            return true;  // No exception → object exists
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                return false; // object not found
            }
            throw new RuntimeException("Error checking object existence", e);
        } catch (Exception e) {
            throw new RuntimeException("Error checking object existence", e);
        }
    }

    private String encodeUriPathSegment(String str) {
        return UriUtils.encodePathSegment(str, StandardCharsets.UTF_8);
    }

    private String encodeUriPath(String str) {
        return UriUtils.encodePath(str, StandardCharsets.UTF_8); // preserve '/'
    }

    public String getObjectUrlForContainer(String bucket, String object) {
        return minioContainerUrl + "/" + encodeUriPathSegment(bucket) + "/" + encodeUriPath(object);
    }
}
