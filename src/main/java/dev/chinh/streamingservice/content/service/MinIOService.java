package dev.chinh.streamingservice.content.service;

import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class MinIOService {

    private final MinioClient minioClient;

    public String getSignedUrl(String bucket, String object, int expirySeconds) throws Exception {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucket)
                        .object(object)
                        .expiry(expirySeconds)
                        .build()
        );
    }

    public String getSignedUrlForHostNginx(String bucket, String object, int expirySeconds) throws Exception {
        String signedUrl = getSignedUrl(bucket, object, expirySeconds);
        // Replace the MinIO base URL with your Nginx URL
        return signedUrl.replace("http://localhost:9000", "http://localhost/stream/minio");
    }

    public String getSignedUrlForContainerNginx(String bucket, String object, int expirySeconds) throws Exception {
        String signedUrl = getSignedUrl(bucket, object, expirySeconds);
        return signedUrl.replace("http://localhost:9000", "http://nginx/stream/minio");
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

    public InputStream getFile(String bucket, String object) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(object)
                        .build()
        );
    }

}
