package dev.chinh.streamingservice.mediahandler.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.mediahandler.MediaMapper;
import dev.chinh.streamingservice.mediapersistence.entity.MediaGroupMetaData;
import dev.chinh.streamingservice.mediapersistence.entity.MediaMetaData;
import dev.chinh.streamingservice.mediapersistence.repository.MediaMetaDataRepository;
import dev.chinh.streamingservice.mediahandler.MediaBasicInfo;
import dev.chinh.streamingservice.mediahandler.event.probe.ImageMetadata;
import dev.chinh.streamingservice.mediahandler.event.probe.MediaProbe;
import dev.chinh.streamingservice.mediahandler.event.probe.VideoMetadata;
import dev.chinh.streamingservice.mediahandler.modify.service.MediaMetadataModifyService;
import dev.chinh.streamingservice.mediahandler.MinIOService;
import io.minio.messages.DeleteObject;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class MediaHandlerService {

    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;

    private final MediaMetadataModifyService mediaMetadataModifyService;
    private final MinIOService minIOService;
    private final MediaProbe mediaProbe;
    private final ThumbnailService thumbnailService;

    private final MediaMetaDataRepository mediaMetaDataRepository;
    private final MediaMetaDataRepository mediaRepository;
    private final ObjectMapper objectMapper;
    private final MediaMapper mediaMapper;


    /**
     * Nested directory includes a top directory acts as parent.
     * This parent needs to be created first with an id returned in the event for the child directories.
     */
    @Transactional
    public void handleNestedDirectoryToGrouperMedia(MediaUpdateEvent.NestedDirectoryToGrouperMediaInitiated event) {
        MediaSavedRequest saveRequest = new MediaSavedRequest(
                event.bucket(), event.objectName(), event.fileName(), MediaType.GROUPER
        );
        String title = event.fileName().replaceAll("[-_]", " ");
        MediaBasicInfo mediaBasicInfo = new MediaBasicInfo(
                title,
                (short) event.uploadDate().atOffset(ZoneOffset.UTC).getYear()
        );
        var saved = saveMedia(null, event.userId(), saveRequest, mediaBasicInfo, null);

        eventPublisher.publishEvent(new MediaHandlerEventProducer.EventWrapper(
                EventTopics.MEDIA_FILE_TOPIC,
                event.userId(),
                new MediaUpdateEvent.NestedDirectoryToGrouperMediaInitiated(
                        event.userId(),
                        event.fileId(),
                        saved.getId(),
                        event.bucket(), event.objectName(),
                        event.fileName(), event.uploadDate(),
                        event.childSearchable(),
                        event.childMediaType(),
                        event.length()
                )
        ));
    }

    @Transactional
    public void handleGrouperMediaCreated(MediaUpdateEvent.GrouperMediaCreatedReady event) throws Exception {
        MediaMetaData mediaMetaData = getMediaMetadataById(Long.parseLong(event.userId()), event.mediaId());
        if (event.bucket() != null && event.objectName() != null)
            probeAndFillMediaMetadata(mediaMetaData, event.bucket(), event.objectName(), MediaType.detectMediaType(event.objectName()), createMediaThumbnailString(event.userId(), MediaType.GROUPER, event.objectName()));
        mediaMetaData.setLength(event.length());

        eventPublisher.publishEvent(new MediaHandlerEventProducer.EventWrapper(
                EventTopics.MEDIA_FILE_AND_BACKUP_TOPIC,
                event.userId(),
                new MediaUpdateEvent.MediaCreatedReady(
                        event.userId(),
                        event.fileid(),
                        event.mediaId(),
                        MediaType.GROUPER,
                        mediaMetaData.getThumbnail(),
                        mediaMetaData.getLength(),
                        mediaMetaData.getWidth(),
                        mediaMetaData.getHeight()
                )
        ));

        MediaUpdateEvent.MediaCreatedReadyForSearch eventForSearch = mediaMapper.map(mediaMetaData);
        System.out.println(eventForSearch);
        eventPublisher.publishEvent(new MediaHandlerEventProducer.EventWrapper(
                EventTopics.MEDIA_SEARCH_TOPIC,
                event.userId(),
                eventForSearch
        ));
    }

    @Transactional
    public void handleDirectoryToAlbumMedia(MediaUpdateEvent.DirectoryToAlbumMediaInitiated event) throws Exception {
        MediaSavedRequest saveRequest = new MediaSavedRequest(
                event.bucket(), event.objectName(), event.fileName(), MediaType.ALBUM
        );
        String title = event.fileName().replaceAll("[-_]", " ");
        MediaBasicInfo mediaBasicInfo = new MediaBasicInfo(
                title,
                (short) event.uploadDate().atOffset(ZoneOffset.UTC).getYear()
        );

        MediaMetaData mediaMetaData = new MediaMetaData();
        mediaMetaData.setSize(event.size());
        mediaMetaData.setLength(event.length());
        if (event.searchable())
            probeAndFillMediaMetadata(mediaMetaData, event.bucket(), event.objectName(), MediaType.detectMediaType(event.objectName()), createMediaThumbnailString(event.userId(), MediaType.ALBUM, event.objectName()));
        var saved = saveMedia(mediaMetaData, event.userId(), saveRequest, mediaBasicInfo, event.parentMediaId());

        String topic = event.searchable()
                ? EventTopics.MEDIA_FILE_AND_BACKUP_TOPIC
                : EventTopics.MEDIA_FILE_TOPIC; // not searchable - no thumbnail - no backup to save the thumbnail
        eventPublisher.publishEvent(new MediaHandlerEventProducer.EventWrapper(
                topic,
                event.userId(),
                new MediaUpdateEvent.MediaCreatedReady(
                        event.userId(),
                        event.fileId(),
                        saved.getId(),
                        MediaType.ALBUM,
                        mediaMetaData.getThumbnail(),
                        mediaMetaData.getLength(),
                        mediaMetaData.getWidth(),
                        mediaMetaData.getHeight()
                )
        ));

        MediaUpdateEvent.MediaCreatedReadyForSearch eventForSearch = mediaMapper.map(saved);
        eventPublisher.publishEvent(new MediaHandlerEventProducer.EventWrapper(
                EventTopics.MEDIA_SEARCH_TOPIC,
                event.userId(),
                eventForSearch
        ));
    }

    @Transactional
    public void handleFileToVideoMedia(MediaUpdateEvent.FileToVideoMediaInitiated event) throws Exception {
        MediaSavedRequest saveRequest = new MediaSavedRequest(
                event.bucket(), event.objectName(), event.fileName(), MediaType.VIDEO
        );

        int lastDotIndex = event.fileName().lastIndexOf(".");
        lastDotIndex = lastDotIndex == -1 ? event.fileName().length() : lastDotIndex;
        String title = event.fileName().substring(0, lastDotIndex).replaceAll("[-_]", " ");
        MediaBasicInfo mediaBasicInfo = new MediaBasicInfo(
                title,
                (short) event.uploadDate().atOffset(ZoneOffset.UTC).getYear()
        );

        MediaMetaData mediaMetaData = new MediaMetaData();
        probeAndFillMediaMetadata(mediaMetaData, event.bucket(), event.objectName(), MediaType.VIDEO, createMediaThumbnailString(event.userId(), MediaType.VIDEO, event.objectName()));

        var saved = saveMedia(mediaMetaData, event.userId(), saveRequest, mediaBasicInfo, null);

        if (event.nameUpdateListAsJson() != null) {
            List<MediaMetadataModifyService.UpdateList> updateLists = objectMapper.readValue(event.nameUpdateListAsJson(), new TypeReference<>() {});
            mediaMetadataModifyService.updateNameEntityInMediaInBatch(event.userId(), updateLists, saved.getId(), false);
        }

        eventPublisher.publishEvent(new MediaHandlerEventProducer.EventWrapper(
                EventTopics.MEDIA_FILE_AND_BACKUP_TOPIC,
                event.userId(),
                new MediaUpdateEvent.MediaCreatedReady(
                        event.userId(),
                        event.fileId(),
                        saved.getId(),
                        MediaType.VIDEO,
                        mediaMetaData.getThumbnail(),
                        mediaMetaData.getLength(),
                        mediaMetaData.getWidth(),
                        mediaMetaData.getHeight()
                )
        ));

        MediaUpdateEvent.MediaCreatedReadyForSearch eventForSearch = mediaMapper.map(saved);
        eventPublisher.publishEvent(new MediaHandlerEventProducer.EventWrapper(
                EventTopics.MEDIA_SEARCH_TOPIC,
                event.userId(),
                eventForSearch
        ));
    }

    private void probeAndFillMediaMetadata(MediaMetaData mediaMetaData, String bucket, String objectKey, MediaType mediaType, String thumbnailObject) throws Exception {
        if (mediaType == MediaType.IMAGE) {
            ImageMetadata imageMetadata = mediaProbe.parseMediaMetadata(mediaProbe.probeMediaInfo(bucket, objectKey), ImageMetadata.class);
            mediaMetaData.setWidth(imageMetadata.width());
            mediaMetaData.setHeight(imageMetadata.height());
            mediaMetaData.setFormat(imageMetadata.format());
            mediaMetaData.setFrameRate((short) 1);
            mediaMetaData.setThumbnail(thumbnailService.copyAlbumObjectToThumbnailBucket(bucket, objectKey, thumbnailObject));
            mediaMetaData.setSize(imageMetadata.size());
        } else if (mediaType == MediaType.VIDEO) {
            VideoMetadata videoMetadata = mediaProbe.parseMediaMetadata(mediaProbe.probeMediaInfo(bucket, objectKey), VideoMetadata.class);
            mediaMetaData.setWidth(videoMetadata.width());
            mediaMetaData.setHeight(videoMetadata.height());
            mediaMetaData.setFormat(videoMetadata.format());
            mediaMetaData.setFrameRate(videoMetadata.frameRate());
            mediaMetaData.setThumbnail(thumbnailService.generateThumbnailFromVideo(bucket, objectKey, thumbnailObject, (int) videoMetadata.durationSeconds(), null));
            mediaMetaData.setSize(videoMetadata.size());
            mediaMetaData.setLength((int) videoMetadata.durationSeconds());
        }
    }


    @Transactional
    public void handleMoveGrouperItem(MediaUpdateEvent.GrouperItemMoved event) {
        Long newGroupInfoId = null;
        if (event.parentMediaId() != null) {
            newGroupInfoId = addMediaToGrouper(event.parentMediaId(), event.childMediaId(), event.fileName());
            System.out.println(newGroupInfoId);
        } else {
            // a grouper item is moved out of a grouper and not into another grouper - delete media info
            mediaMetadataModifyService.deleteMedia(event.userId(), event.childMediaId());
        }
        eventPublisher.publishEvent(new MediaHandlerEventProducer.EventWrapper(
                EventTopics.MEDIA_SEARCH_TOPIC,
                event.userId(),
                new MediaUpdateEvent.GrouperItemMoved(
                        event.userId(),
                        event.childMediaId(),
                        event.parentMediaId(),
                        event.fileName(),
                        event.oldParentIsGrouper(),
                        newGroupInfoId
                )
        ));
    }

    public void handleDeleteObject(MediaUpdateEvent.ObjectDeleted event) {
        List<DeleteObject> objects = event.objectNames().stream().map(DeleteObject::new).toList();
        if (event.bucket() == null) {
            System.err.println("Bucket is null, skipping delete objects");
            return;
        }
        minIOService.removeBulk(event.bucket(), objects);
    }

    @Transactional
    public void handleUpdateMediaThumbnail(MediaUpdateEvent.MediaThumbnailUpdated event) throws Exception {
        MediaMetaData mediaMetaData = getMediaMetadataById(Long.parseLong(event.userId()), event.mediaId());

        String oldExtension = getFileExtension(mediaMetaData.getThumbnail());
        String newExtension = getFileExtension(event.thumbnailObject());
        boolean sameName = newExtension.equals(oldExtension);
        String newThumbnailName = sameName ? mediaMetaData.getThumbnail() : event.num() != null ? createMediaThumbnailString(event.userId(), event.mediaType(), event.thumbnailObject()) : event.thumbnailObject();

        if (event.num() != null && event.mediaType() == MediaType.VIDEO) {
            thumbnailService.generateThumbnailFromVideo(
                    mediaMetaData.getBucket(),
                    mediaMetaData.getKey(),
                    event.thumbnailObject(),
                    mediaMetaData.getLength(),
                    event.num()
            );
        } else if (event.num() != null && event.mediaType() == MediaType.ALBUM) {
            MediaType thumbnailType = MediaType.detectMediaType(event.thumbnailObject());
            if (thumbnailType == MediaType.IMAGE) {
                ImageMetadata imageMetadata = mediaProbe.parseMediaMetadata(mediaProbe.probeMediaInfo(mediaMetaData.getBucket(), event.thumbnailObject()), ImageMetadata.class);
                mediaMetaData.setWidth(imageMetadata.width());
                mediaMetaData.setHeight(imageMetadata.height());
                mediaMetaData.setFormat(imageMetadata.format());
            } else if (thumbnailType == MediaType.VIDEO) {
                VideoMetadata videoMetadata = mediaProbe.parseMediaMetadata(mediaProbe.probeMediaInfo(mediaMetaData.getBucket(), event.thumbnailObject()), VideoMetadata.class);
                mediaMetaData.setWidth(videoMetadata.width());
                mediaMetaData.setHeight(videoMetadata.height());
            }
            minIOService.copyObjectToAnotherBucket(event.bucket(), event.thumbnailObject(), ContentMetaData.THUMBNAIL_BUCKET, newThumbnailName);
        }
        if (!sameName) {
            minIOService.removeFile(ContentMetaData.THUMBNAIL_BUCKET, mediaMetaData.getThumbnail());

            mediaMetaDataRepository.updateMediaThumbnail(Long.parseLong(event.userId()), mediaMetaData.getId(), newThumbnailName);
        }

        String topic = sameName ? EventTopics.MEDIA_BACKUP_TOPIC : EventTopics.MEDIA_FILE_SEARCH_AND_BACKUP_TOPIC;
        eventPublisher.publishEvent(new MediaHandlerEventProducer.EventWrapper(
                topic,
                event.userId(),
                new MediaUpdateEvent.MediaThumbnailUpdatedReady(event.mediaId(), mediaMetaData.getThumbnail(), newThumbnailName))
        );

        String baseThumbnailCache = "/thumbnail-cache/";
        String fileThumbnailCache = event.userId() + "/" + event.mediaId() + "_p144" + oldExtension;
        String searchThumbnailCache = event.userId() + "/" + event.mediaId() + "_p360" + oldExtension;
        String fileThumbnailWithBase = baseThumbnailCache.concat(fileThumbnailCache);
        String searchThumbnailWithBase = baseThumbnailCache.concat(searchThumbnailCache);

        OSUtil.deleteForceMemoryDirectory(fileThumbnailWithBase, null);
        OSUtil.deleteForceMemoryDirectory(searchThumbnailWithBase, null);
        redisTemplate.opsForZSet().remove("thumbnail-cache", fileThumbnailCache);
        redisTemplate.opsForZSet().remove("thumbnail-cache", searchThumbnailCache);
    }

    public void handleDeleteThumbnail(MediaUpdateEvent.ThumbnailDeleted event) throws Exception {
        minIOService.removeFile(ContentMetaData.THUMBNAIL_BUCKET, event.objectName());
    }


    private MediaMetaData getMediaMetadataById(long userId, long id) {
        return mediaMetaDataRepository.findByUserIdAndId(userId, id).orElseThrow(
                () -> new IllegalArgumentException("Media not found: " + id)
        );
    }

    public static String getFileExtension(String name) {
        if (name == null) return "";
        name = name.toLowerCase();
        int lastDotIndex = name.lastIndexOf(".");
        if (lastDotIndex == -1) return "";
        return name.substring(lastDotIndex);
    }

    private static final String defaultVidPath = "vid";
    private static final String defaultAlbumPath = "album";
    private static final String defaultGrouperPath = "grouper";
    public static String createMediaThumbnailString(String userId, MediaType mediaType, String objectName) {
        String extension = getFileExtension(objectName);
        if (MediaType.detectMediaType(extension) != MediaType.IMAGE)
            extension = ".jpg";
        if (mediaType == MediaType.VIDEO) {
            return userId + "/" + defaultVidPath + "/" + UUID.randomUUID() + "_thumb" + extension;
        } else if (mediaType == MediaType.ALBUM) {
            return userId + "/" + defaultAlbumPath + "/" + UUID.randomUUID() + "_thumb" + extension;
        } else if (mediaType == MediaType.GROUPER) {
            return userId + "/" + defaultGrouperPath + "/" + UUID.randomUUID() + "_thumb" + extension;
        }
        return null;
    }


    public record MediaSavedRequest(String bucket, String objectName, String fileName, MediaType mediaType) {}

    @Transactional
    public MediaMetaData saveMedia(MediaMetaData base, String userId, MediaSavedRequest upload, MediaBasicInfo basicInfo, Long parentMediaId) {
        if (upload.mediaType == MediaType.OTHER || upload.mediaType == MediaType.IMAGE) {
            throw new IllegalArgumentException("Unsupported type to be a media: " + upload.mediaType);
        }
        MediaMetaData mediaMetaData = base == null ? new MediaMetaData() : base;
        mediaMetaData.setTitle(basicInfo.getTitle());
        mediaMetaData.setYear(basicInfo.getYear());
        mediaMetaData.setUploadDate(Instant.now());
        mediaMetaData.setBucket(upload.bucket);
        mediaMetaData.setMediaType(upload.mediaType);
        mediaMetaData.setUserId(Long.parseLong(userId));
        mediaMetaData.setKey(upload.objectName);

        if (upload.mediaType == MediaType.GROUPER) {
            MediaGroupMetaData mediaGroupInfo = new MediaGroupMetaData();
            mediaGroupInfo.setGrouperMetaData(null);
            mediaGroupInfo.setMediaMetaData(mediaMetaData);
            mediaMetaData.setGroupInfo(mediaGroupInfo);
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

        return mediaRepository.save(mediaMetaData);
    }

    /**
     * @return new media group info id if successful, null if failed.
     */
    @Transactional
    public Long addMediaToGrouper(long grouperMediaId, long mediaId, String fileName) {
        MediaMetaData mediaMetaData = mediaRepository.findById(mediaId).orElse(null);
        if (mediaMetaData == null) {
            System.err.println("Media not found: " + mediaId);
            return null;
        }
        MediaMetaData grouperMedia = mediaRepository.findById(grouperMediaId).orElse(null);
        if (grouperMedia == null) {
            System.err.println("Grouper media not found: " + grouperMediaId);
            return null;
        }
        mediaMetadataModifyService.updateMediaLengthWithDelta(grouperMedia.getUserId(), List.of(grouperMediaId), 1);
        MediaGroupMetaData mediaGroupInfo = new MediaGroupMetaData();
        mediaGroupInfo.setGrouperMetaData(grouperMedia.getGroupInfo());
        mediaGroupInfo.setMediaMetaData(mediaMetaData);
        mediaGroupInfo.setNumInfo(fileName);
        mediaMetaData.setGroupInfo(mediaGroupInfo);
        var saved = mediaRepository.save(mediaMetaData);
        return saved.getGroupInfo().getId();
    }
}
