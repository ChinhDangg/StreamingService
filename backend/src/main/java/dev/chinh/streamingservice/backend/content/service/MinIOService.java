package dev.chinh.streamingservice.backend.content.service;

import io.minio.*;
import io.minio.http.Method;
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
}
