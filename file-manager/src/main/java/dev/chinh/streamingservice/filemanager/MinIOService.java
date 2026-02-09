package dev.chinh.streamingservice.filemanager;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MinIOService {

    private final MinioClient minioClient;

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
}
