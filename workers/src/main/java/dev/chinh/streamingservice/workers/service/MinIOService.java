package dev.chinh.streamingservice.workers.service;

import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
