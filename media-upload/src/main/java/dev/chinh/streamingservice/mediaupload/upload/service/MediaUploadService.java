package dev.chinh.streamingservice.mediaupload.upload.service;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.chinh.streamingservice.common.constant.MediaJobStatus;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.mediaupload.MediaBasicInfo;
import dev.chinh.streamingservice.mediaupload.event.MediaUploadEventProducer;
import dev.chinh.streamingservice.mediaupload.modify.service.MediaMetadataModifyService;
import dev.chinh.streamingservice.mediapersistence.entity.*;
import dev.chinh.streamingservice.mediapersistence.repository.*;
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
import java.time.ZoneOffset;
import java.util.*;

@RequiredArgsConstructor
@Service
public class MediaUploadService {

    private final RedisTemplate<String, String> redisStringTemplate;

    private final ObjectUploadService objectUploadService;
    private final MinIOService minIOService;
    private final MediaMetadataModifyService mediaMetadataModifyService;

    private final ApplicationEventPublisher publisher;

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

    public String initiateMultipartUploadRequest(String userId, String sessionId) {
        String fileName = getCachedSessionRequest(sessionId);
        if (fileName == null) {
            throw new IllegalArgumentException("Invalid session ID: " + sessionId);
        }
        String objectName = generateUniqueObjectName(userId, fileName);
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
        String minioPresignedUrl = objectUploadService.getPresignedPartUrl(getBucketOnMediaType(fileName), objectName, uploadId, partNumber, uploadSessionTimeout);
        minioPresignedUrl = minioPresignedUrl.substring("http://localhost:9000".length());
        minioPresignedUrl = "/stream/upload" + minioPresignedUrl;
        return minioPresignedUrl;
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
    public void saveFile(String userId, String uploadId, List<UploadedPart> parts, boolean isLast) {
        String combinedName = completeUpload(uploadId, parts);

        String objectName = combinedName.substring(0, combinedName.indexOf(":|:"));
        String fileName = combinedName.substring(combinedName.indexOf(":|:") + 3);
        String bucket = getBucketOnMediaType(fileName);

        long size = minIOService.getObjectSize(bucket, objectName);
        publisher.publishEvent(new MediaUploadEventProducer.EventWrapper(
                EventTopics.MEDIA_FILE_AND_BACKUP_TOPIC,
                new MediaUpdateEvent.FileCreated(userId, bucket, objectName, fileName, size, null, null, null, isLast)
        ));

        removeCacheFileUploadRequest(uploadId);
    }


    public record MediaUploadRequest(String bucket, String objectName, String fileName, MediaType mediaType, boolean searchable) {}

    @Transactional
    public long saveAsVideoMedia(String userId, String uploadId, MediaBasicInfo basicInfo, List<UploadedPart> parts, List<MediaMetadataModifyService.UpdateList> nameUpdateList, boolean isLast) {
        String combinedName = getCachedFileUploadRequest(uploadId);
        if (combinedName == null) {
            throw new IllegalArgumentException("Invalid upload ID: " + uploadId);
        }

        String fileName = combinedName.substring(combinedName.indexOf(":|:") + 3);
        if (!validateObjectWithMediaType(fileName, MediaType.VIDEO)) {
            throw new IllegalArgumentException("Invalid video media type with object: " + fileName);
        }

        completeUpload(uploadId, parts);

        String objectName = combinedName.substring(0, combinedName.indexOf(":|:"));
        long savedId = saveMedia(
                userId,
                new MediaUploadRequest(ContentMetaData.VIDEO_BUCKET, objectName, fileName, MediaType.VIDEO, true),
                basicInfo,
                null
        );

        String bucket = getBucketOnMediaType(fileName);
        long size = minIOService.getObjectSize(bucket, objectName);

        if (nameUpdateList != null)
            mediaMetadataModifyService.updateNameEntityInMediaInBatch(userId, nameUpdateList, savedId, false);

        publisher.publishEvent(new MediaUploadEventProducer.EventWrapper(
                EventTopics.MEDIA_FILE_AND_BACKUP_TOPIC,
                new MediaUpdateEvent.FileCreated(
                        userId,
                        bucket, objectName,
                        fileName, size,
                        savedId, MediaType.VIDEO,
                        MediaUploadService.createMediaThumbnailString(userId, MediaType.VIDEO, savedId, objectName),
                        isLast
                )
        ));

        removeCacheFileUploadRequest(uploadId);

        return savedId;
    }

    @Transactional
    public long saveMedia(String userId, MediaUploadRequest upload, MediaBasicInfo basicInfo, Long parentMediaId) {
        if (upload.mediaType == MediaType.OTHER || upload.mediaType == MediaType.IMAGE) {
            throw new IllegalArgumentException("Unsupported type to be a media: " + upload.mediaType);
        }
        MediaMetaData mediaMetaData = new MediaMetaData();
        mediaMetaData.setTitle(basicInfo.getTitle());
        mediaMetaData.setYear(basicInfo.getYear());
        mediaMetaData.setUploadDate(Instant.now());
        mediaMetaData.setBucket(upload.bucket);
        mediaMetaData.setMediaType(upload.mediaType);
        mediaMetaData.setThumbnail(null);
        mediaMetaData.setUserId(Long.parseLong(userId));

        mediaMetaData.setFormat(MediaJobStatus.PROCESSING.name());
        mediaMetaData.setSize(0L);
        mediaMetaData.setWidth(0);
        mediaMetaData.setHeight(0);
        mediaMetaData.setLength(0);
        mediaMetaData.setKey(upload.objectName);

        if (upload.mediaType == MediaType.VIDEO) {
            mediaMetaData.setFrameRate((short) -1);
        }

        MediaMetaData saved = mediaRepository.save(mediaMetaData);
        long savedId = saved.getId();

        if (upload.mediaType == MediaType.GROUPER) {
            MediaGroupMetaData mediaGroupInfo = new MediaGroupMetaData();
            mediaGroupInfo.setGrouperMetaData(null);
            mediaGroupInfo.setMediaMetaData(saved);
            saved.setGroupInfo(mediaGroupInfo);
            mediaRepository.save(saved);
        }

        if (parentMediaId != null) {
            MediaMetaData grouperMedia = mediaRepository.findById(parentMediaId).orElse(null);
            if (grouperMedia != null) {
                MediaGroupMetaData mediaGroupInfo = new MediaGroupMetaData();
                mediaGroupInfo.setGrouperMetaData(grouperMedia.getGroupInfo());
                mediaGroupInfo.setMediaMetaData(mediaMetaData);
                mediaGroupInfo.setNumInfo(upload.fileName);
                mediaMetaData.setGroupInfo(mediaGroupInfo);
            }
        }

        return savedId;
    }

    public String addMediaToGrouper(long grouperMediaId, long mediaId, String fileName) {
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
        mediaGroupInfo.setNumInfo(fileName);
        mediaMetaData.setGroupInfo(mediaGroupInfo);
        mediaRepository.save(mediaMetaData);
        return null;
    }

    @Transactional
    public void handleInitiateFileToMedia(MediaUpdateEvent.FileToMediaInitiated event) {
        if (event.childMediaId() != null) {
            if (event.parentMediaId() != null) {
                String error = addMediaToGrouper(event.parentMediaId(), event.childMediaId(), event.fileName());
                if (error != null)
                    System.err.println(error);
                return;
            }
            System.out.println("File event is given with media ID but not part of a grouper, skipping file to media");
            return;
        }

        MediaUploadService.MediaUploadRequest uploadRequest = new MediaUploadService.MediaUploadRequest(
                event.bucket(), event.objectName(), event.fileName(), event.mediaType(), event.searchable()
        );
        int lastDotIndex = event.fileName().lastIndexOf(".");
        lastDotIndex = lastDotIndex == -1 ? event.fileName().length() : lastDotIndex;
        MediaBasicInfo mediaBasicInfo = new MediaBasicInfo(
                event.fileName().substring(0, lastDotIndex).replaceAll("[-_]", " "),
                (short) event.uploadDate().atOffset(ZoneOffset.UTC).getYear()
        );
        long mediaId = saveMedia(event.userId(), uploadRequest, mediaBasicInfo, event.parentMediaId());

        if (event.parentMediaId() != null && event.updateParentLength())
            mediaMetadataModifyService.updateMediaLengthWithDelta(Long.parseLong(event.userId()), event.parentMediaId(), 1);

        // upload service can send media enriched for a single file
        // for multiple files as one media like ALBUM or GROUPER need file service to retrieve all contents
        if (event.mediaType() == MediaType.VIDEO) {
            publisher.publishEvent(new MediaUploadEventProducer.EventWrapper(
                    EventTopics.MEDIA_OBJECT_TOPIC,
                    new MediaUpdateEvent.MediaEnriched(
                            event.userId(),
                            event.fileId(),
                            mediaId,
                            event.mediaType(),
                            MediaUploadService.createMediaThumbnailString(event.userId(), event.mediaType(), mediaId, event.objectName()),
                            event.searchable(),
                            -1,
                            -1
                    )
            ));
        } else if (event.mediaType() == MediaType.ALBUM) {
            publisher.publishEvent(new MediaUploadEventProducer.EventWrapper(
                    EventTopics.MEDIA_FILE_TOPIC,
                    new MediaUpdateEvent.DirectoryToMediaInitiated(
                            event.userId(),
                            event.fileId(),
                            mediaId,
                            event.mediaType(),
                            event.searchable(),
                            event.updateParentLength(),
                            event.searchable() ? MediaUploadService.createMediaThumbnailString(event.userId(), event.mediaType(), mediaId, event.objectName()) : null,
                            0,
                            0
                    )
            ));
        } else if (event.mediaType() == MediaType.GROUPER) {
            publisher.publishEvent(new MediaUploadEventProducer.EventWrapper(
                    EventTopics.MEDIA_FILE_TOPIC,
                    new MediaUpdateEvent.NestedDirectoryToMediaInitiated(
                            event.userId(),
                            event.fileId(),
                            mediaId,
                            MediaType.GROUPER,
                            MediaType.ALBUM,
                            false,
                            MediaUploadService.createMediaThumbnailString(event.userId(), event.mediaType(), mediaId, event.objectName()),
                            0
                    )
            ));
        }
    }

    public static String createMediaThumbnailString(String userId, MediaType mediaType, long mediaId, String objectName) {
        String extension = getFileExtension(objectName);
        if (MediaType.detectMediaType(extension) != MediaType.IMAGE)
            extension = ".jpg";
        if (mediaType == MediaType.VIDEO) {
            return userId + "/" + defaultVidPath + "/" + mediaId + "_" + UUID.randomUUID() + "_thumb" + extension;
        } else if (mediaType == MediaType.ALBUM) {
            return userId + "/" + defaultAlbumPath + "/" + mediaId + "_" + UUID.randomUUID() + "_thumb" + extension;
        } else if (mediaType == MediaType.GROUPER) {
            return userId + "/" + defaultGrouperPath + "/" + mediaId + "_" + UUID.randomUUID() + "_thumb" + extension;
        }
        return null;
    }

    private String generateUniqueObjectName(String userId, String fileName) {
        return userId + "/" + UuidCreator.getTimeOrderedEpoch().toString() + getFileExtension(fileName);
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
