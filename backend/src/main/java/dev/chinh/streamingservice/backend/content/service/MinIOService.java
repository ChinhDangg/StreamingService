package dev.chinh.streamingservice.backend.content.service;

import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class MinIOService {

    private final MinioClient minioClient;

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

    /**
     * Get signed URL and replace the MinIO signed base URL with Nginx URL.
    */
    public String getSignedUrlForHostNginx(String bucket, String object, int expirySeconds) throws Exception {
        String signedUrl = getSignedUrl(bucket, object, expirySeconds);
        return signedUrl.replace("http://localhost:9000", "http://localhost/stream/minio");
    }

    /**
     * Get signed URL and replace the MinIO signed base URL with Nginx container URL.
     */
    public String getSignedUrlForContainerNginx(String bucket, String object, int expirySeconds) throws Exception {
        String signedUrl = getSignedUrl(bucket, object, expirySeconds);
        return signedUrl.replace("http://localhost:9000", "http://nginx/stream/minio");
    }
}
