package dev.chinh.streamingservice.workers.service;

import io.minio.*;
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

    private String encodeUriPathSegment(String str) {
        return UriUtils.encodePathSegment(str, StandardCharsets.UTF_8);
    }

    private String encodeUriPath(String str) {
        return UriUtils.encodePath(str, StandardCharsets.UTF_8); // preserve '/'
    }

    public String getObjectUrlForContainer(String bucket, String object) {
        return minioContainerUrl + "/" + encodeUriPathSegment(bucket) + "/" + encodeUriPath(object);
    }

    public String getRedirectObjectUrl(String bucket, String object) {
        return "/stream/redirect/object/" + encodeUriPathSegment(bucket) + "/" + encodeUriPath(object);
    }

    public String getObjectUrl(String bucket, String object) {
        return "/stream/object/" + encodeUriPathSegment(bucket) + "/" + encodeUriPath(object);
    }

    public Iterable<Result<Item>> getAllItemsInBucketWithPrefix(String bucketName, String prefix) {
        return minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .prefix(prefix)
                        .recursive(true)    // list everything under this prefix
                        .build()
        );
    }

    public long getObjectSize(String bucket, String object) {
        try {
            StatObjectResponse response = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(object)
                            .build()
            );
            return response.size();
        } catch (Exception e) {
            throw new RuntimeException("Error checking object existence", e);
        }
    }

    public void removeFile(String bucket, String object) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(object)
                        .build()
        );
        System.out.println("Removed object " + bucket + "/" + object);
    }

    public void moveFileToObject(String bucket, String object, String filePath) throws Exception {
        minioClient.uploadObject(
                UploadObjectArgs.builder()
                        .bucket(bucket)
                        .object(object)
                        .filename(filePath)
                        .build()
        );
    }
}
