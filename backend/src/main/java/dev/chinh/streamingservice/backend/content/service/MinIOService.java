package dev.chinh.streamingservice.backend.content.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MinIOService {

    private final MinioClient minioClient;

    @Value("${minio.container.url}")
    private String minioContainerUrl;

    private String getSignedUrl(String bucket, String object, int expirySeconds) throws Exception {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucket)
                        .object(object)
                        .expiry(expirySeconds)
                        .build()
        );
    }

    public String getObjectUrlForContainer(String bucket, String object) {
        return minioContainerUrl + "/" + bucket + "/" + object;
    }

    public String getRedirectObjectUrl(String bucket, String object) {
        return "/stream/redirect/object/" + bucket + "/" + object;
    }

    public String getObjectUrl(String bucket, String object) {
        return "/stream/object/" + bucket + "/" + object;
    }
}
