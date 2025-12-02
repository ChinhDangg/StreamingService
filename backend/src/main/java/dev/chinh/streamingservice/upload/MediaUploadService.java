package dev.chinh.streamingservice.upload;

import dev.chinh.streamingservice.content.constant.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class MediaUploadService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final RedisTemplate<String, Object> redisTemplate;

    private final String mediaBucket = "media";

    public String initiateMediaUploadRequest(String objectName, MediaType mediaType) {
        String validatedObject = validateObject(objectName);
        if (mediaType == MediaType.VIDEO && MediaType.detectMediaType(validatedObject) != MediaType.VIDEO) {
            throw new IllegalArgumentException("Invalid video type: " + validatedObject);
        }
        else if (mediaType == MediaType.ALBUM && !isValidDirectoryPath(validatedObject)) {
            throw new IllegalArgumentException("Album must be a valid directory path");
        }

        return cacheMediaUploadRequest(mediaType, validatedObject);
    }

    public String initiateMultipartUploadRequest(String sessionId, String object, MediaType mediaType) {
        var uploadRequest = getCachedMediaUploadRequest(sessionId);
        if (uploadRequest == null) {
            throw new IllegalArgumentException("Invalid session ID: " + sessionId);
        }

        String validatedObject = validateObject(object);
        validateObjectWithMediaType(validatedObject, mediaType);

        validateMediaSessionInfoWithRequest(uploadRequest, validatedObject, mediaType);

        CreateMultipartUploadRequest multipartUploadRequest = CreateMultipartUploadRequest.builder()
                .bucket(mediaBucket)
                .key(validatedObject)
                .build();

        CreateMultipartUploadResponse response = s3Client.createMultipartUpload(multipartUploadRequest);
        String uploadId = response.uploadId();
        cacheFileUploadRequest(uploadId, mediaType);
        return uploadId;
    }

    public String generatePresignedPartUrl(String object, String uploadId, int partNumber) {
        MediaType mediaType = getCachedFileUploadRequest(uploadId);
        if (mediaType == null) {
            throw new IllegalArgumentException("Upload ID not found: " + uploadId);
        }
        String validatedObject = validateObject(object);
        validateObjectWithMediaType(validatedObject, mediaType);

        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .bucket(mediaBucket)
                .key(validatedObject)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();

        PresignedUploadPartRequest presigned = s3Presigner.presignUploadPart(builder -> builder
                .signatureDuration(Duration.ofHours(1))
                .uploadPartRequest(uploadPartRequest)
        );
        return presigned.url().toString();
    }

    public record UploadedPart(int partNumber, String etag) {}

    public void completeMultipartUpload(String object, String uploadId, List<UploadedPart> uploadedParts) {
        MediaType mediaType = getCachedFileUploadRequest(uploadId);
        if (mediaType == null) {
            throw new IllegalArgumentException("Upload ID not found: " + uploadId);
        }
        String validatedObject = validateObject(object);
        validateObjectWithMediaType(validatedObject, mediaType);

        List<CompletedPart> completedParts = uploadedParts.stream()
                .map(p -> CompletedPart.builder()
                        .partNumber(p.partNumber)
                        .eTag(p.etag)
                        .build()
                )
                .toList();

        CompletedMultipartUpload upload = CompletedMultipartUpload.builder()
                .parts(completedParts)
                .build();

        CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(mediaBucket)
                .key(validatedObject)
                .uploadId(uploadId)
                .multipartUpload(upload)
                .build();

        s3Client.completeMultipartUpload(completeRequest);
        removeCacheFileUploadRequest(uploadId);
    }

    private void validateMediaSessionInfoWithRequest(MediaUploadRequest request, String object, MediaType mediaType) {
        if (request.mediaType != mediaType) {
            throw new IllegalArgumentException("Mismatched media type: " + object);
        }
        if (!object.startsWith(request.objectName)) {
            throw new IllegalArgumentException("Mismatched object name: " + object);
        }
    }

    private String validateObject(String object) {
        if (object == null) {
            throw new IllegalArgumentException("Object name must not be null");
        }

        object = object.trim();

        // Normalize separators
        object = object.replace("\\", "/");

        // Collapse multiple slashes
        object = object.replaceAll("/+", "/");

        // Remove trailing slash unless root
        if (object.endsWith("/") && object.length() > 1) {
            object = object.substring(0, object.length() - 1);
        }

        if (object.isBlank()) {
            throw new IllegalArgumentException("Object name must not be empty");
        }

        // Block directory traversal
        if (object.contains("..")) {
            throw new IllegalArgumentException("Parent directory reference '..' is not allowed");
        }

        // KEEP:
        // - Unicode letters
        // - Numbers
        // - underscore, hyphen, dot, slash
        // REMOVE everything else
        object = object.replaceAll("[^\\p{L}0-9._/-]", "");

        if (object.isBlank()) {
            throw new IllegalArgumentException("Object name is blank after sanitization");
        }

        return object;
    }

    private void validateObjectWithMediaType(String object, MediaType mediaType) {
        MediaType detectedType = MediaType.detectMediaType(object);
        if (detectedType == MediaType.OTHER) {
            throw new IllegalArgumentException("Invalid file type: " + object);
        }
        if (mediaType == MediaType.VIDEO && detectedType != MediaType.VIDEO) {
            throw new IllegalArgumentException("Invalid video type: " + object);
        }
    }

    private void cacheFileUploadRequest(String uploadId, MediaType mediaType) {
        String id = "file::" + uploadId;
        redisTemplate.opsForValue().set(id, mediaType.name(), Duration.ofHours(1));
    }

    private MediaType getCachedFileUploadRequest(String uploadId) {
        String id = "file::" + uploadId;
        Object value = redisTemplate.opsForValue().get(id);
        return value == null ? null : MediaType.valueOf((String) value);
    }

    private void removeCacheFileUploadRequest(String uploadId) {
        String id = "file::" + uploadId;
        redisTemplate.delete(id);
    }

    public record MediaUploadRequest(String objectName, MediaType mediaType) {}

    private String cacheMediaUploadRequest(MediaType mediaType, String objectName) {
        String id = Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
        String redisKey = "upload::" + id;

        redisTemplate.opsForHash().put(redisKey, "objectName", objectName);
        redisTemplate.opsForHash().put(redisKey, "mediaType", mediaType.name());
        return id;
    }

    private MediaUploadRequest getCachedMediaUploadRequest(String mediaUploadId) {
        String key = "upload::" + mediaUploadId;
        Map<Object, Object> cached = redisTemplate.opsForHash().entries(key);

        if (cached.isEmpty()) return null;

        return new MediaUploadRequest(
                (String) cached.get("objectName"),
                MediaType.valueOf((String) cached.get("mediaType"))
        );
    }

    public boolean isValidDirectoryPath(String path) {
        if (path == null) return false;

        // Trim whitespace
        path = path.trim();

        // Normalize slashes
        path = path.replace("\\", "/");

        // Cannot start or end with '/'
        if (path.startsWith("/") || path.endsWith("/")) {
            return false;
        }

        // Split into segments
        String[] segments = path.split("/");

        // Regex: allow unicode letters, digits, underscores, hyphens, spaces
        // NO filesystem-reserved characters
        String allowed = "^[\\p{L}\\p{N}_\\- ]+$";

        for (String segment : segments) {
            // Empty segments = invalid ("dir1//dir2")
            if (segment.isBlank()) {
                return false;
            }

            // Validate each directory name
            if (!segment.matches(allowed)) {
                return false;
            }
        }

        return true;
    }


}
