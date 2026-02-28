package dev.chinh.streamingservice.mediaupload.upload.service;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.chinh.streamingservice.common.constant.MediaJobStatus;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.mediaupload.MediaBasicInfo;
import dev.chinh.streamingservice.mediaupload.event.MediaUploadEventProducer;
import dev.chinh.streamingservice.persistence.entity.*;
import dev.chinh.streamingservice.persistence.repository.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
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

    private final ObjectUploadService objectUploadService;
    private final MinIOService minIOService;

    private final ApplicationEventPublisher eventPublisher;

    private final MediaMetaDataRepository mediaRepository;
    private final MediaGroupMetaDataRepository mediaGroupMetaDataRepository;

    private final Duration uploadSessionTimeout = Duration.ofHours(1);
    private static final String defaultVidPath = "vid";
    private static final String defaultAlbumPath = "album";
    private static final String defaultGrouperPath = "grouper";

    public String getBucketOnMediaType(String filename) {
        MediaType mediaType = MediaType.detectMediaType(filename);
        return switch (mediaType) {
            case VIDEO -> ContentMetaData.VIDEO_BUCKET;
            case IMAGE -> ContentMetaData.IMAGE_BUCKET;
            default -> ContentMetaData.OTHER_BUCKET;
        };
    }

    public String initiateMultipartUploadRequest(String sessionId) {
        String fileName = getCachedSessionRequest(sessionId);
        if (fileName == null) {
            throw new IllegalArgumentException("Invalid session ID: " + sessionId);
        }
        String objectName = generateUniqueObjectName(fileName);
        String uploadId = objectUploadService.getMultipartUploadId(getBucketOnMediaType(fileName), objectName);
        addCacheFileUploadRequest(sessionId, uploadId, objectName, fileName);
        return uploadId;
    }

    public String generatePresignedPartUrl(String uploadId, int partNumber) {
        String combinedName = getCachedFileUploadRequest(uploadId);
        if (combinedName == null) {
            throw new IllegalArgumentException("Upload ID not found: " + uploadId);
        }

        String objectName = combinedName.substring(0, combinedName.indexOf(":|:"));
        String fileName = combinedName.substring(combinedName.indexOf(":|:") + 3);
        return objectUploadService.getPresignedPartUrl(getBucketOnMediaType(fileName), objectName, uploadId, partNumber, uploadSessionTimeout);
    }

    public record UploadedPart(@Max(1500) int partNumber, @Size(max = 100) String etag) {}

    private String completeUpload(String uploadId, List<UploadedPart> parts) {
        String combinedName = getCachedFileUploadRequest(uploadId);
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


    @Transactional(readOnly = true)
    public void saveFile(String uploadId, List<UploadedPart> parts) {
        String combinedName = completeUpload(uploadId, parts);

        String objectName = combinedName.substring(0, combinedName.indexOf(":|:"));
        String fileName = combinedName.substring(combinedName.indexOf(":|:") + 3);
        String bucket = getBucketOnMediaType(fileName);

        long size = minIOService.getObjectSize(bucket, objectName);
        eventPublisher.publishEvent(new MediaUploadEventProducer.EventWrapper(
                EventTopics.MEDIA_FILE_AND_BACKUP_TOPIC,
                new MediaUpdateEvent.FileCreated(bucket, objectName, fileName, size, null, null, null)
        ));

        removeCacheFileUploadRequest(uploadId);
    }


    public record MediaUploadRequest(String bucket, String objectName, String fileName, MediaType mediaType, boolean searchable) {}

    @Transactional
    public long saveAsVideoMedia(String uploadId, MediaBasicInfo basicInfo, List<UploadedPart> parts) {
        String combinedName = getCachedFileUploadRequest(uploadId);
        if (combinedName == null) {
            throw new IllegalArgumentException("Invalid upload ID: " + uploadId);
        }

        String fileName = combinedName.substring(combinedName.indexOf(":|:") + 3);
        if (validateObjectWithMediaType(fileName, MediaType.VIDEO)) {
            removeCacheFileUploadRequest(uploadId);
            throw new IllegalArgumentException("Invalid video media type with object: " + fileName);
        }

        completeUpload(uploadId, parts);

        String objectName = combinedName.substring(0, combinedName.indexOf(":|:"));
        long savedId = saveMedia(
                new MediaUploadRequest(ContentMetaData.VIDEO_BUCKET, objectName, fileName, MediaType.VIDEO, true),
                basicInfo,
                null,
                null
        );

        String bucket = getBucketOnMediaType(fileName);
        long size = minIOService.getObjectSize(bucket, objectName);
        eventPublisher.publishEvent(new MediaUploadEventProducer.EventWrapper(
                EventTopics.MEDIA_FILE_AND_BACKUP_TOPIC,
                new MediaUpdateEvent.FileCreated(
                        bucket, objectName,
                        fileName, size,
                        savedId, MediaType.VIDEO,
                        MediaUploadService.createMediaThumbnailString(MediaType.VIDEO, savedId, objectName))
        ));

        removeCacheFileUploadRequest(uploadId);

        return savedId;
    }

    @Transactional
    public long saveMedia(MediaUploadRequest upload, MediaBasicInfo basicInfo, Long parentMediaId, Integer childNum) {
        if (upload.mediaType == MediaType.OTHER || upload.mediaType == MediaType.IMAGE) {
            throw new IllegalArgumentException("Unsupported type to be a media: " + upload.mediaType);
        }
        MediaMetaData mediaMetaData = new MediaMetaData();
        mediaMetaData.setTitle(basicInfo.getTitle());
        mediaMetaData.setYear(basicInfo.getYear());
        mediaMetaData.setUploadDate(Instant.now());
        mediaMetaData.setBucket(upload.bucket);
        mediaMetaData.setMediaType(upload.mediaType);
        mediaMetaData.setAbsoluteFilePath(ContentMetaData.MEDIA_BUCKET + "/" + upload.fileName);
        mediaMetaData.setThumbnail(null);

        mediaMetaData.setFormat(MediaJobStatus.PROCESSING.name());
        mediaMetaData.setSize(-1L);
        mediaMetaData.setWidth(-1);
        mediaMetaData.setHeight(-1);
        mediaMetaData.setLength(-1);
        mediaMetaData.setKey(upload.objectName);

        if (upload.mediaType == MediaType.VIDEO) {
            mediaMetaData.setFrameRate((short) -1);
        }

        MediaMetaData saved = mediaRepository.save(mediaMetaData);
        long savedId = saved.getId();

        if (parentMediaId != null) {
            MediaMetaData grouperMedia = mediaRepository.findById(parentMediaId).orElse(null);
            if (grouperMedia != null) {
                MediaGroupMetaData mediaGroupInfo = new MediaGroupMetaData();
                mediaGroupInfo.setGrouperMetaData(grouperMedia.getGroupInfo());
                mediaGroupInfo.setMediaMetaData(mediaMetaData);
                mediaGroupInfo.setNumInfo(childNum);
                mediaMetaData.setGroupInfo(mediaGroupInfo);
            }
        }

        return savedId;
    }

    public String addMediaToGrouper(long grouperMediaId, long mediaId, Integer childNum) {
        MediaMetaData mediaMetaData = mediaRepository.findById(mediaId).orElse(null);
        if (mediaMetaData == null) {
            return "Media not found";
        }
        MediaMetaData grouperMedia = mediaRepository.findById(grouperMediaId).orElse(null);
        if (grouperMedia == null) {
            return "Grouper media not found";
        }
        MediaGroupMetaData mediaGroupInfo = new MediaGroupMetaData();
        mediaGroupInfo.setGrouperMetaData(grouperMedia.getGroupInfo());
        mediaGroupInfo.setMediaMetaData(mediaMetaData);
        mediaGroupInfo.setNumInfo(childNum);
        mediaMetaData.setGroupInfo(mediaGroupInfo);
        mediaRepository.save(mediaMetaData);
        return null;
    }

    public static String createMediaThumbnailString(MediaType mediaType, long mediaId, String objectName) {
        String extension = getFileExtension(objectName);
        if (MediaType.detectMediaType(extension) != MediaType.IMAGE)
            extension = ".jpg";
        if (mediaType == MediaType.VIDEO) {
            return defaultVidPath + "/" + mediaId + "_" + UUID.randomUUID() + "_thumb" + extension;
        } else if (mediaType == MediaType.ALBUM) {
            return defaultAlbumPath + "/" + mediaId + "_" + UUID.randomUUID() + "_thumb" + extension;
        } else if (mediaType == MediaType.GROUPER) {
            return defaultGrouperPath + "/" + mediaId + "_" + UUID.randomUUID() + "_thumb" + extension;
        }
        return null;
    }

    private String generateUniqueObjectName(String fileName) {
        return UuidCreator.getTimeOrderedEpoch().toString() + getFileExtension(fileName);
    }

    private void addCacheFileUploadRequest(String sessionId, String uploadId, String objectName, String fileName) {
        String key = "upload::" + sessionId;
        redisStringTemplate.delete(key);
        // ensure objectName starts first, separated by a delimiter before fileName value to safely extract objectName or fileName as fileName can contain anything
        String value = objectName + ":|:" + fileName;
        redisStringTemplate.opsForValue().set(uploadId, value, uploadSessionTimeout);
    }

    private String getCachedFileUploadRequest(String uploadId) {
        return redisStringTemplate.opsForValue().getAndExpire(uploadId, uploadSessionTimeout);
    }

    private void removeCacheFileUploadRequest(String uploadId) {
        redisStringTemplate.delete(uploadId);
    }

    public String getCachedSessionRequest(String sessionId) {
        String key = "upload::" + sessionId;
        return redisStringTemplate.opsForValue().get(key);
    }

    public static String getFileExtension(String name) {
        if (name == null) return "";
        name = name.toLowerCase();
        int lastDotIndex = name.lastIndexOf(".");
        if (lastDotIndex == -1) return "";
        return name.substring(lastDotIndex);
    }

    private boolean validateObjectWithMediaType(String object, MediaType mediaType) {
        MediaType detectedType = MediaType.detectMediaType(object);
        return detectedType == mediaType;
    }
}
