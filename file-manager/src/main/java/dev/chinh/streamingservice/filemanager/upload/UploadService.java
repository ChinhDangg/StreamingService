package dev.chinh.streamingservice.filemanager.upload;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.common.validation.FileSystemValidator;
import dev.chinh.streamingservice.filemanager.event.FileEventProducer;
import dev.chinh.streamingservice.filemanager.service.DirectoryCacheService;
import dev.chinh.streamingservice.filemanager.service.FileService;
import dev.chinh.streamingservice.filemanager.service.MinIOService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.time.Duration;
import java.util.List;

@Service
@AllArgsConstructor
public class UploadService {

    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher publisher;

    private final FileService fileService;
    private final DirectoryCacheService directoryCacheService;

    private final ObjectUploadService objectUploadService;
    private final MinIOService minIOService;

    private final Duration uploadSessionTimeout = Duration.ofHours(1);

    public String getBucketOnMediaType(String filename) {
        MediaType mediaType = MediaType.detectMediaType(filename);
        return switch (mediaType) {
            case VIDEO -> ContentMetaData.VIDEO_BUCKET;
            case IMAGE -> ContentMetaData.IMAGE_BUCKET;
            default -> ContentMetaData.OTHER_BUCKET;
        };
    }

    public String initiateUploadRequest(String userId, String filePath) {
        var validatedFile = FileSystemValidator.isValidPath(filePath);
        if (validatedFile.errorMessage() != null) {
            throw new IllegalArgumentException(validatedFile.errorMessage());
        }
        String validatedPath = validatedFile.validatedPath();
        String[] pathParts = validatedPath.split("/");
        int partLength = pathParts.length;
        String parentId = fileService.getROOT_FOLDER_ID();
        for (int i = 0; i < partLength-1; i++) {
            String dirId = directoryCacheService.getCachedElseDbDirectoryId(parentId, pathParts[i], userId, true);
            if (dirId == null) {
                return initiateMultipartUploadRequest(userId, validatedPath);
            }
            parentId = dirId;
        }

        boolean exists = fileService.itemWithNameExists(userId, parentId, pathParts[partLength-1]);
        if (exists) throw new IllegalArgumentException("File already exists: " + validatedPath);
        return initiateMultipartUploadRequest(userId, validatedPath);
    }

    private String initiateMultipartUploadRequest(String userId, String fileName) {
        String objectName = generateUniqueObjectName(userId, fileName);
        String uploadId = objectUploadService.getMultipartUploadId(getBucketOnMediaType(fileName), objectName);
        addCacheFileUploadRequest(userId, uploadId, objectName, fileName);
        return uploadId;
    }

    private String getUploadIdAsKey(String userId, String uploadId) {
        return userId + "::" + uploadId;
    }
    private void addCacheFileUploadRequest(String userId, String uploadId, String objectName, String fileName) {
        // ensure objectName starts first, separated by a delimiter before fileName value to safely extract objectName or fileName as fileName can contain anything
        String value = objectName + ":|:" + fileName;
        redisTemplate.opsForValue().set(getUploadIdAsKey(userId, uploadId), value, uploadSessionTimeout);
    }

    private String getCachedFileUploadRequest(String uploadId) {
        return redisTemplate.opsForValue().getAndExpire(uploadId, uploadSessionTimeout);
    }

    private void removeCacheFileUploadRequest(String uploadId) {
        redisTemplate.delete(uploadId);
    }

    public String generatePresignedPartUrl(String userId, String uploadId, int partNumber) {
        String combinedName = getCachedFileUploadRequest(getUploadIdAsKey(userId, uploadId));
        if (combinedName == null) {
            throw new IllegalArgumentException("Upload ID not found: " + uploadId);
        }

        String objectName = combinedName.substring(0, combinedName.indexOf(":|:"));
        String fileName = combinedName.substring(combinedName.indexOf(":|:") + 3);
        String minioPresignedUrl = objectUploadService.getPresignedPartUrl(getBucketOnMediaType(fileName), objectName, uploadId, partNumber, uploadSessionTimeout);
        minioPresignedUrl = minioPresignedUrl.substring("http://localhost:9000".length());
        minioPresignedUrl = "/stream/upload" + minioPresignedUrl;
        return minioPresignedUrl;
    }

    public record UploadedPart(@Max(1500) int partNumber, @Size(max = 100) String etag) {}

    private String completeUpload(String userId, String uploadId, List<UploadedPart> parts) {
        String combinedName = getCachedFileUploadRequest(getUploadIdAsKey(userId, uploadId));
        if (combinedName == null) {
            throw new IllegalArgumentException("Upload ID not found: " + uploadId);
        }

        List<CompletedPart> completedParts = parts.stream()
                .map(p -> CompletedPart.builder()
                        .partNumber(p.partNumber)
                        .eTag(p.etag)
                        .build()
                )
                .toList();

        String objectName = combinedName.substring(0, combinedName.indexOf(":|:"));
        objectUploadService.completeMultipartUpload(getBucketOnMediaType(objectName), objectName, uploadId, completedParts);
        return combinedName;
    }

    public void saveFile(String userId, String uploadId, List<UploadedPart> parts, boolean isLast, boolean addAsVideo, String nameUpdateListAsJson) {
        String combinedName = completeUpload(userId, uploadId, parts);

        String objectName = combinedName.substring(0, combinedName.indexOf(":|:"));
        String fileName = combinedName.substring(combinedName.indexOf(":|:") + 3);
        String bucket = getBucketOnMediaType(fileName);

        long size = minIOService.getObjectSize(bucket, objectName);
        publisher.publishEvent(new FileEventProducer.ImmediateEventWrapper(
                EventTopics.MEDIA_FILE_AND_BACKUP_TOPIC,
                userId,
                new MediaUpdateEvent.FileCreated(userId, bucket, objectName, fileName, size, isLast, addAsVideo, nameUpdateListAsJson)
        ));

        removeCacheFileUploadRequest(getUploadIdAsKey(userId, uploadId));
    }


    private String generateUniqueObjectName(String userId, String fileName) {
        return userId + "/" + UuidCreator.getTimeOrderedEpoch().toString() + getFileExtension(fileName);
    }

    public static String getFileExtension(String name) {
        if (name == null) return "";
        name = name.toLowerCase();
        int lastDotIndex = name.lastIndexOf(".");
        if (lastDotIndex == -1 || lastDotIndex == 0) return "";
        return name.substring(lastDotIndex);
    }


}
