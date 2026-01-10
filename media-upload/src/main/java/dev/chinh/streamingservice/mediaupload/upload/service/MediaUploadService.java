package dev.chinh.streamingservice.mediaupload.upload.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.constant.MediaJobStatus;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.common.exception.ResourceNotFoundException;
import dev.chinh.streamingservice.mediaupload.MediaBasicInfo;
import dev.chinh.streamingservice.mediaupload.event.config.KafkaRedPandaConfig;
import dev.chinh.streamingservice.persistence.entity.*;
import dev.chinh.streamingservice.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@RequiredArgsConstructor
@Service
public class MediaUploadService {

    private final RedisTemplate<String, String> redisStringTemplate;

    private final ObjectMapper objectMapper;
    private final ObjectUploadService objectUploadService;
    private final MinIOService minIOService;
    private final MediaDisplayService mediaDisplayService;
    private final MediaSearchCacheService mediaSearchCacheService;

    private final ApplicationEventPublisher eventPublisher;

    private final MediaMetaDataRepository mediaRepository;
    private final MediaGroupMetaDataRepository mediaGroupMetaDataRepository;

    private final String mediaBucket = ContentMetaData.MEDIA_BUCKET;
    public static final String defaultVidPath = "vid";
    private final Duration uploadSessionTimeout = Duration.ofHours(1);

    @Value("${backup.enabled}")
    private String backupEnabled;

    public String initiateMediaUploadRequest(String objectName, MediaType mediaType) {
        String validatedObject = validateObject(objectName, mediaType);
        if (mediaType == MediaType.VIDEO && MediaType.detectMediaType(validatedObject) != MediaType.VIDEO) {
            throw new IllegalArgumentException("Invalid video type: " + validatedObject);
        }
        else if (mediaType == MediaType.ALBUM && !isValidDirectoryPath(validatedObject)) {
            throw new IllegalArgumentException("Album must be a valid directory path");
        }

        return addCacheMediaUploadRequest(mediaType, validatedObject);
    }

    public String initiateMultipartUploadRequest(String sessionId, String object, MediaType mediaType) {
        var uploadRequest = getCachedMediaUploadRequest(sessionId);
        if (uploadRequest == null) {
            throw new IllegalArgumentException("Invalid session ID: " + sessionId);
        }

        String validatedObject = validateObject(object, mediaType);
        validateObjectWithMediaType(validatedObject, mediaType);

        validateMediaSessionInfoWithRequest(uploadRequest, validatedObject, mediaType);

        if (minIOService.objectExists(mediaBucket, validatedObject)) {
            // Personal system - so only check for name to ensure uniqueness (not content itself).
            // For wide usage - generate unique names for every object and keep all content
            // or check etag but multipart upload must ensure same part number and size.
            throw new IllegalArgumentException("Object already exists: " + validatedObject);
        }

        String uploadId = objectUploadService.getMultipartUploadId(mediaBucket, validatedObject);
        addCacheFileUploadRequest(sessionId, uploadId, validatedObject);
        return uploadId;
    }

    public String generatePresignedPartUrl(String sessionId, String uploadId, String object, int partNumber) {
        MediaType mediaType = getCachedFileUploadRequest(sessionId, uploadId);
        if (mediaType == null) {
            throw new IllegalArgumentException("Upload ID not found: " + uploadId);
        }
        String validatedObject = validateObject(object, mediaType);
        validateObjectWithMediaType(validatedObject, mediaType);

        addUploadSessionCacheLastAccess(sessionId, uploadSessionTimeout);

        return objectUploadService.getPresignedPartUrl(mediaBucket, validatedObject, uploadId, partNumber, uploadSessionTimeout);
    }

    public record UploadedPart(int partNumber, String etag) {}

    public void markCompletingMultipartUpload(String sessionId, String uploadId, String object, List<UploadedPart> uploadedParts) throws JsonProcessingException {
        MediaType mediaType = getCachedFileUploadRequest(sessionId, uploadId);
        if (mediaType == null) {
            throw new IllegalArgumentException("Upload ID not found: " + uploadId);
        }
        String validatedObject = validateObject(object, mediaType);
        validateObjectWithMediaType(validatedObject, mediaType);

        addCacheFileUploadParts(sessionId, uploadId, uploadedParts);
        addUploadSessionCacheLastAccess(sessionId, uploadSessionTimeout);
    }


    @Transactional
    public long saveGrouperMedia(MediaBasicInfo basicInfo) {
        MediaMetaData mediaMetaData = new MediaMetaData();
        mediaMetaData.setTitle(basicInfo.getTitle());
        mediaMetaData.setYear(basicInfo.getYear());
        mediaMetaData.setUploadDate(Instant.now());
        mediaMetaData.setBucket(mediaBucket);
        mediaMetaData.setAbsoluteFilePath(mediaBucket + "/grouper-not-media");
        mediaMetaData.setParentPath("grouper-not-media");
        mediaMetaData.setKey("grouper-not-media");
        mediaMetaData.setLength(0);

        MediaGroupMetaData mediaGroupInfo = new MediaGroupMetaData();
        mediaGroupInfo.setNumInfo(0);
        mediaGroupInfo.setMediaMetaData(mediaMetaData);
        mediaMetaData.setGroupInfo(mediaGroupInfo);

        String extension = basicInfo.getThumbnail().getOriginalFilename() == null ? ".jpg"
                : basicInfo.getThumbnail().getOriginalFilename().substring(basicInfo.getThumbnail().getOriginalFilename().lastIndexOf("."));
        String path = "grouper/" + mediaMetaData.getUploadDate() + "_" + validateObject(basicInfo.getTitle(), MediaType.ALBUM) + extension;

        try {
            minIOService.uploadFile(ContentMetaData.THUMBNAIL_BUCKET, path, basicInfo.getThumbnail());
            mediaMetaData.setThumbnail(path);
            mediaMetaData.setSize(-1L);
            mediaMetaData.setWidth(-1);
            mediaMetaData.setHeight(-1);
            mediaMetaData.setFormat(MediaJobStatus.PROCESSING.name());

            MediaMetaData saved = mediaRepository.save(mediaMetaData);
            long savedId = saved.getId();

            MediaUpdateEvent.MediaCreated mediaCreatedEvent = new MediaUpdateEvent.MediaCreated(savedId, true);
            eventPublisher.publishEvent(new MediaUpdateEvent.MediaUpdateEnrichment(savedId, MediaType.GROUPER, KafkaRedPandaConfig.MEDIA_SEARCH_TOPIC, mediaCreatedEvent));

            eventPublisher.publishEvent(mediaCreatedEvent);

            return savedId;
        } catch (Exception e) {
            try {
                minIOService.removeFile(ContentMetaData.THUMBNAIL_BUCKET, path);
            } catch (Exception ex) {
                throw new RuntimeException("Critical: Failed to delete thumbnail from MinIO to rollback", ex);
            }
            throw new RuntimeException("Failed to save media grouper metadata", e);
        }
    }

//    You always see your own writes in a transaction.
//    READ_COMMITTED still allows this.
//    REPEATABLE_READ also allows this.
//    SERIALIZABLE of course does.
    @Transactional
    public long saveMediaInGrouper(String sessionId, long grouperMediaId, String title) throws Exception {
        MediaUploadRequest upload = getCachedMediaUploadRequest(sessionId);
        if (upload == null) {
            throw new IllegalArgumentException("Invalid session ID: " + sessionId);
        }

        MediaMetaData grouperMedia = mediaRepository.findById(grouperMediaId).orElseThrow(
                () -> new ResourceNotFoundException("Media not found: " + grouperMediaId)
        );
        if (!grouperMedia.isGrouper()) {
            throw new IllegalArgumentException("Media is not a grouper media: " + grouperMediaId);
        }

        completeUpload(sessionId);

        MediaGroupMetaData grouperGroupInfo = grouperMedia.getGroupInfo();

        MediaMetaData mediaMetaData = new MediaMetaData();
        mediaMetaData.setTitle(title);
        mediaMetaData.setYear(grouperMedia.getYear());
        mediaMetaData.setUploadDate(Instant.now());
        mediaMetaData.setBucket(mediaBucket);
        mediaMetaData.setAbsoluteFilePath(mediaBucket + "/" + upload.objectName);
        mediaMetaData.setParentPath(upload.objectName);

        mediaMetaData.setSize(-1L);
        mediaMetaData.setLength(-1);
        mediaMetaData.setThumbnail(MediaJobStatus.PROCESSING.name());
        mediaMetaData.setWidth(-1);
        mediaMetaData.setHeight(-1);
        mediaMetaData.setFormat(MediaJobStatus.PROCESSING.name());

//        mediaRepository.incrementLength(grouperMedia.getId());
//        mediaGroupMetaDataRepository.incrementNumInfo(grouperGroupInfo.getId());

        Integer updatedNumInfo = mediaGroupMetaDataRepository.incrementNumInfoReturning(grouperGroupInfo.getId());
        Integer updatedLength = mediaRepository.incrementLengthReturning(grouperMedia.getId());

        MediaGroupMetaData mediaGroupInfo = new MediaGroupMetaData();
        mediaGroupInfo.setGrouperMetaData(grouperGroupInfo);
        mediaGroupInfo.setMediaMetaData(mediaMetaData);
        mediaGroupInfo.setNumInfo(updatedNumInfo);
        mediaMetaData.setGroupInfo(mediaGroupInfo);

        Long savedId = mediaRepository.save(mediaMetaData).getId();

        eventPublisher.publishEvent(new MediaUpdateEvent.MediaUpdateEnrichment(savedId, MediaType.ALBUM, null, null));

        eventPublisher.publishEvent(new MediaUpdateEvent.LengthUpdated(grouperMedia.getId(), updatedLength));

        if (Boolean.parseBoolean(backupEnabled))
            eventPublisher.publishEvent(new MediaUpdateEvent.MediaBackupCreated(mediaBucket, mediaMetaData.getPath(), mediaMetaData.getAbsoluteFilePath(), upload.mediaType));

        mediaSearchCacheService.removeCachedMediaSearchItem(grouperMedia.getId());
        mediaDisplayService.removeCacheGroupOfMedia(grouperMedia.getId());

        removeCacheMediaSessionRequest(sessionId);

        return savedId;
    }

    @Transactional
    public long saveMedia(String sessionId, MediaBasicInfo basicInfo) throws Exception {
        MediaUploadRequest upload = getCachedMediaUploadRequest(sessionId);
        if (upload == null) {
            throw new IllegalArgumentException("Invalid session ID: " + sessionId);
        }

        completeUpload(sessionId);

        MediaMetaData mediaMetaData = new MediaMetaData();
        mediaMetaData.setTitle(basicInfo.getTitle());
        mediaMetaData.setYear(basicInfo.getYear());
        mediaMetaData.setUploadDate(Instant.now());
        mediaMetaData.setBucket(mediaBucket);
        mediaMetaData.setAbsoluteFilePath(mediaBucket + "/" + upload.objectName);
        mediaMetaData.setThumbnail(MediaJobStatus.PROCESSING.name());

        mediaMetaData.setFormat(MediaJobStatus.PROCESSING.name());
        mediaMetaData.setSize(-1L);
        mediaMetaData.setWidth(-1);
        mediaMetaData.setHeight(-1);
        mediaMetaData.setLength(-1);

        if (upload.mediaType == MediaType.VIDEO) {
            int index = upload.objectName.lastIndexOf("/");
            mediaMetaData.setParentPath(upload.objectName.substring(0, index));
            mediaMetaData.setKey(upload.objectName.substring(index + 1));
            mediaMetaData.setFrameRate((short) -1);
        } else if (upload.mediaType == MediaType.ALBUM) {
            mediaMetaData.setParentPath(upload.objectName);
        }

        MediaMetaData saved = mediaRepository.save(mediaMetaData);
        long savedId = saved.getId();

        MediaUpdateEvent.MediaCreated mediaCreatedEvent = new MediaUpdateEvent.MediaCreated(savedId, false);
        eventPublisher.publishEvent(new MediaUpdateEvent.MediaUpdateEnrichment(savedId, upload.mediaType, KafkaRedPandaConfig.MEDIA_SEARCH_TOPIC, mediaCreatedEvent));

        eventPublisher.publishEvent(mediaCreatedEvent);

        if (Boolean.parseBoolean(backupEnabled))
            eventPublisher.publishEvent(new MediaUpdateEvent.MediaBackupCreated(mediaBucket, mediaMetaData.getPath(), mediaMetaData.getAbsoluteFilePath(), upload.mediaType));

        removeCacheMediaSessionRequest(sessionId);

        return savedId;
    }

    private void completeUpload(String sessionId) throws JsonProcessingException {
        Map<Object, Object> uploadedParts = redisStringTemplate.opsForHash().entries("upload::" + sessionId);
        for (Map.Entry<Object, Object> entry : uploadedParts.entrySet()) {
            String uploadIdPart = (String) entry.getKey();
            if (!uploadIdPart.startsWith("parts:"))
                continue;

            String uploadId = uploadIdPart.substring("parts:".length());
            //System.out.println("Upload ID: " + uploadId);
            String objectName = redisStringTemplate.opsForHash().get("upload::" + sessionId, uploadId).toString();
            //System.out.println("Object name: " + objectName);

            List<UploadedPart> parts = objectMapper.readValue(entry.getValue().toString(), new TypeReference<>() {});
            //System.out.println("Parts: " + parts);

            List<CompletedPart> completedParts = parts.stream()
                    .map(p -> CompletedPart.builder()
                            .partNumber(p.partNumber)
                            .eTag(p.etag)
                            .build()
                    )
                    .toList();

            objectUploadService.completeMultipartUpload(mediaBucket, objectName, uploadId, completedParts);
        }
    }


    private void addCacheFileUploadParts(String sessionId, String uploadId, List<UploadedPart> parts) throws JsonProcessingException {
        String key = "upload::" + sessionId;
        String json = objectMapper.writeValueAsString(parts);
        redisStringTemplate.opsForHash().put(key, "parts:" + uploadId, json);
    }

    private void addCacheFileUploadRequest(String sessionId, String uploadId, String object) {
        redisStringTemplate.opsForHash().put("upload::" + sessionId, uploadId, object);
    }

    private MediaType getCachedFileUploadRequest(String sessionId, String uploadId) {
        String key = "upload::" + sessionId;
        Object cached = redisStringTemplate.opsForHash().get(key, uploadId);

        if (cached == null) return null;

        return MediaType.valueOf((String) redisStringTemplate.opsForHash().get(key, "mediaType"));
    }

    public record MediaUploadRequest(String objectName, MediaType mediaType) {}

    private String addCacheMediaUploadRequest(MediaType mediaType, String objectName) {
        String id = Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
        String redisKey = "upload::" + id;

        redisStringTemplate.opsForHash().put(redisKey, "objectName", objectName);
        redisStringTemplate.opsForHash().put(redisKey, "mediaType", mediaType.name());
        addUploadSessionCacheLastAccess(id, uploadSessionTimeout);
        return id;
    }

    public MediaUploadRequest getCachedMediaUploadRequest(String mediaUploadId) {
        String key = "upload::" + mediaUploadId;
        Object cached = redisStringTemplate.opsForHash().get(key, "objectName");

        if (cached == null) return null;

        return new MediaUploadRequest(
                (String) cached,
                MediaType.valueOf((String) redisStringTemplate.opsForHash().get(key, "mediaType"))
        );
    }

    public void removeCacheMediaSessionRequest(String sessionId) {
        String key = "upload::" + sessionId;
        redisStringTemplate.delete(key);
    }

    private void addUploadSessionCacheLastAccess(String sessionId, Duration duration) {
        String key = "upload::" + sessionId;
        redisStringTemplate.expire(key, duration);
    }


    private void validateMediaSessionInfoWithRequest(MediaUploadRequest request, String object, MediaType mediaType) {
        if (request.mediaType != mediaType) {
            throw new IllegalArgumentException("Mismatched media type: " + object);
        }
        if (!object.startsWith(request.objectName)) {
            throw new IllegalArgumentException("Mismatched object name: " + object);
        }
    }

    private String validateObject(String object, MediaType mediaType) {
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

        if (mediaType == MediaType.VIDEO) {
            int pathIndex = object.lastIndexOf("/");
            if (pathIndex == -1) { // no base dir for vid, seem to vid folder - later save to user path or whatever
                object = OSUtil.normalizePath(defaultVidPath, object);
            }
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
