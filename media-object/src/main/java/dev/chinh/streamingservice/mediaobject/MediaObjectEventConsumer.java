package dev.chinh.streamingservice.mediaobject;

import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.mediaobject.config.KafkaRedPandaConfig;
import dev.chinh.streamingservice.mediaobject.probe.ImageMetadata;
import dev.chinh.streamingservice.mediaobject.probe.MediaProbe;
import dev.chinh.streamingservice.mediaobject.probe.VideoMetadata;
import dev.chinh.streamingservice.persistence.entity.MediaMetaData;
import dev.chinh.streamingservice.persistence.entity.MediaNameEntity;
import dev.chinh.streamingservice.persistence.entity.MediaNameEntityWithThumbnail;
import dev.chinh.streamingservice.persistence.repository.*;
import io.minio.Result;
import io.minio.messages.Item;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MediaObjectEventConsumer {

    private final MinIOService minIOService;
    private final MediaProbe mediaProbe;
    private final ThumbnailService thumbnailService;

    private final MediaMetaDataRepository mediaMetaDataRepository;
    private final MediaAuthorRepository mediaAuthorRepository;
    private final MediaCharacterRepository mediaCharacterRepository;
    private final MediaUniverseRepository mediaUniverseRepository;
    private final MediaTagRepository mediaTagRepository;

    private final ApplicationEventPublisher eventPublisher;

    Logger logger = LoggerFactory.getLogger(MediaObjectEventConsumer.class);

    private MediaMetaData getMediaMetadataById(long id) {
        return mediaMetaDataRepository.findByIdWithAllInfo(id).orElseThrow(
                () -> new IllegalArgumentException("Media not found: " + id)
        );
    }

    private void onDeleteMediaObject(MediaUpdateEvent.MediaDeleted event) throws Exception {
        System.out.println("Received media object delete event: " + event.mediaType() + " " + event.path());
        MediaType mediaType = event.mediaType();

        try {
            if (event.hasThumbnail())
                minIOService.removeFile(ContentMetaData.THUMBNAIL_BUCKET, event.thumbnail());
        } catch (Exception e) {
            System.err.println("Failed to delete thumbnail file: " + event.thumbnail());
            throw e;
        }

        if (mediaType == MediaType.VIDEO) {
            try {
                minIOService.removeFile(event.bucket(), event.path());
            } catch (Exception e) {
                System.err.println("Failed to delete media file: " + event.path());
                throw e;
            }
        } else if (mediaType == MediaType.ALBUM) {
            try {
                String prefix = event.path();
                // there is no filesystem in object storage, so we need to add the trailing slash to delete the exact oldThumbnail
                if (!prefix.endsWith("/"))
                    prefix += "/";
                Iterable<Result<Item>> results = minIOService.getAllItemsInBucketWithPrefix(event.bucket(), prefix);
                for (Result<Item> result : results) {
                    String objectName = result.get().objectName();
                    if (!objectName.startsWith(prefix)) {
                        // this should not happen but for safeguard
                        logger.warn("Skipping unsafe delete: {}", objectName);
                        continue;
                    }
                    minIOService.removeFile(event.bucket(), objectName);
                }
            } catch (Exception e) {
                System.err.println("Failed to delete media files in album: " + event.path());
                throw e;
            }
        }
    }

    @Transactional
    public void onCreateMedia(MediaUpdateEvent.MediaCreated event) throws Exception {
        System.out.println("Received media enrichment update event: " + event.mediaId());
        try {
            MediaMetaData mediaMetaData = getMediaMetadataById(event.mediaId());
            if (event.mediaType() == MediaType.VIDEO) {
                VideoMetadata videoMetadata = mediaProbe.parseMediaMetadata(mediaProbe.probeMediaInfo(mediaMetaData.getBucket(), mediaMetaData.getPath()), VideoMetadata.class);
                mediaMetaData.setFrameRate(videoMetadata.frameRate());
                mediaMetaData.setFormat(videoMetadata.format());
                mediaMetaData.setSize(videoMetadata.size());
                mediaMetaData.setWidth(videoMetadata.width());
                mediaMetaData.setHeight(videoMetadata.height());
                mediaMetaData.setLength((int) videoMetadata.durationSeconds());
                mediaMetaData.setThumbnail(thumbnailService.generateThumbnailFromVideo(mediaMetaData.getBucket(), mediaMetaData.getPath(), event.thumbnailObject(), mediaMetaData.getLength(), null));
            } else if (event.mediaType() == MediaType.ALBUM) {
                var results = minIOService.getAllItemsInBucketWithPrefix(mediaMetaData.getBucket(), mediaMetaData.getPath());
                int count = 0;
                long totalSize = 0;
                String firstImage = null;
                String firstVideo = null;
                for (Result<Item> result : results) {
                    count++;
                    Item item = result.get();
                    if (MediaType.detectMediaType(item.objectName()) == MediaType.IMAGE) {
                        if (firstImage == null)
                            firstImage = item.objectName();
                        totalSize += item.size();
                    }
                    if (firstImage == null && firstVideo == null && MediaType.detectMediaType(item.objectName()) == MediaType.VIDEO) {
                        firstVideo = item.objectName();
                    }
                }
                mediaMetaData.setSize(totalSize);
                mediaMetaData.setLength(count);
                if (firstImage != null) {
                    ImageMetadata imageMetadata = mediaProbe.parseMediaMetadata(mediaProbe.probeMediaInfo(mediaMetaData.getBucket(), firstImage), ImageMetadata.class);
                    mediaMetaData.setWidth(imageMetadata.width());
                    mediaMetaData.setHeight(imageMetadata.height());
                    mediaMetaData.setFormat(imageMetadata.format());
                    mediaMetaData.setThumbnail(thumbnailService.copyAlbumObjectToThumbnailBucket(mediaMetaData.getBucket(), firstImage, event.thumbnailObject()));
                } else if (firstVideo != null) {
                    VideoMetadata videoMetadata = mediaProbe.parseMediaMetadata(mediaProbe.probeMediaInfo(mediaMetaData.getBucket(), firstVideo), VideoMetadata.class);
                    mediaMetaData.setWidth(videoMetadata.width());
                    mediaMetaData.setHeight(videoMetadata.height());
                    mediaMetaData.setFormat(videoMetadata.format());
                    mediaMetaData.setFrameRate(videoMetadata.frameRate());
                    mediaMetaData.setThumbnail(thumbnailService.generateThumbnailFromVideo(mediaMetaData.getBucket(), firstVideo, event.thumbnailObject(), (int) videoMetadata.durationSeconds(), null));
                }
            } else if (event.mediaType() == MediaType.GROUPER) {
                ImageMetadata imageMetadata = mediaProbe.parseMediaMetadata(mediaProbe.probeMediaInfo(ContentMetaData.THUMBNAIL_BUCKET, mediaMetaData.getThumbnail()), ImageMetadata.class);
                mediaMetaData.setSize(imageMetadata.size());
                mediaMetaData.setWidth(imageMetadata.width());
                mediaMetaData.setHeight(imageMetadata.height());
                mediaMetaData.setFormat(imageMetadata.format());
                String oldThumbnail = mediaMetaData.getThumbnail();
                mediaMetaData.setThumbnail(thumbnailService.copyAlbumObjectToThumbnailBucket(ContentMetaData.THUMBNAIL_BUCKET, oldThumbnail, event.thumbnailObject()));
                minIOService.removeFile(ContentMetaData.THUMBNAIL_BUCKET, oldThumbnail);
            }

            // send event to save media search in search and backup (2 groups)
            if (event.mediaType() != MediaType.GROUPER) {
                eventPublisher.publishEvent(new MediaUpdateEvent.MediaCreatedReady(
                        event.mediaId(),
                        event.mediaType(),
                        mediaMetaData.getBucket(),
                        mediaMetaData.getPath(),
                        mediaMetaData.getAbsoluteFilePath(),
                        mediaMetaData.getThumbnail()
                ));
            }
        } catch (Exception e) {
            System.err.println("Failed to update media enrichment: " + event.mediaId());
            throw e;
        }
    }

    @Transactional
    public void onUpdateMediaThumbnail(MediaUpdateEvent.MediaThumbnailUpdated event) throws Exception {
        System.out.println("Received media thumbnail update event: " + event.mediaId() + " " + event.num());
        MediaMetaData mediaMetaData = getMediaMetadataById(event.mediaId());

        if (event.num() == null) {
            String oldThumbnail = mediaMetaData.getThumbnail();
            minIOService.removeFile(ContentMetaData.THUMBNAIL_BUCKET, oldThumbnail);
            mediaMetaData.setThumbnail(event.thumbnailObject());

            eventPublisher.publishEvent(new MediaUpdateEvent.MediaThumbnailUpdatedReady(event.mediaId(), oldThumbnail, event.thumbnailObject()));
            return;
        }

        String oldThumbnail = null;
        String newThumbnail = null;
        if (event.mediaType() == MediaType.VIDEO) {
            if (event.num() >= 0 && event.num() < mediaMetaData.getLength()) {
                newThumbnail = thumbnailService.generateThumbnailFromVideo(
                        mediaMetaData.getBucket(),
                        mediaMetaData.getPath(),
                        event.thumbnailObject(),
                        mediaMetaData.getLength(),
                        event.num()
                );
                oldThumbnail = mediaMetaData.getThumbnail();
                mediaMetaDataRepository.updateMediaThumbnail(mediaMetaData.getId(), newThumbnail);
            }
        } else if (event.mediaType() == MediaType.ALBUM) {
            int albumNum = event.num().intValue();
            if (albumNum >= 0 && albumNum < mediaMetaData.getLength()) {
                var results = minIOService.getAllItemsInBucketWithPrefix(mediaMetaData.getBucket(), mediaMetaData.getPath());
                int count = 0;
                String objectAtNum = null;
                for (Result<Item> result : results) {
                    if (count == albumNum) {
                        objectAtNum = result.get().objectName();
                        break;
                    }
                    count++;
                }
                if (objectAtNum != null) {
                    MediaType mediaType = MediaType.detectMediaType(objectAtNum);
                    if (mediaType == MediaType.IMAGE) {
                        newThumbnail = thumbnailService.copyAlbumObjectToThumbnailBucket(mediaMetaData.getBucket(), objectAtNum, event.thumbnailObject());
                    } else if (mediaType == MediaType.VIDEO) {
                        VideoMetadata videoMetadata = mediaProbe.parseMediaMetadata(mediaProbe.probeMediaInfo(mediaMetaData.getBucket(), objectAtNum), VideoMetadata.class);
                        newThumbnail = thumbnailService.generateThumbnailFromVideo(
                                mediaMetaData.getBucket(), objectAtNum, event.thumbnailObject(), (int) videoMetadata.durationSeconds(), null);
                    }
                    if (newThumbnail != null) {
                        oldThumbnail = mediaMetaData.getThumbnail();
                        mediaMetaDataRepository.updateMediaThumbnail(mediaMetaData.getId(), newThumbnail);
                    }
                }
            }
        }
        eventPublisher.publishEvent(new MediaUpdateEvent.MediaThumbnailUpdatedReady(event.mediaId(), oldThumbnail, newThumbnail));
    }

    @Transactional
    public void onCreateNameEntity(MediaUpdateEvent.NameEntityCreated event) throws Exception {
        System.out.println("Received create name entity: " + event.nameEntityConstant() + " nameEntityId: " + event.nameEntityId());
        MediaNameEntity nameEntity = getNameEntityNameById(event.nameEntityConstant(), event.nameEntityId());
        try {
            if (event.thumbnailObject() != null && event.thumbnailPath() != null) {
                MediaNameEntityWithThumbnail nameEntityWithThumbnail = (MediaNameEntityWithThumbnail) nameEntity;
                nameEntityWithThumbnail.setThumbnail(
                        thumbnailService.copyAlbumObjectToThumbnailBucket(ContentMetaData.THUMBNAIL_BUCKET, event.thumbnailObject(), event.thumbnailPath())
                );
            }
            eventPublisher.publishEvent(new MediaUpdateEvent.NameEntityCreatedReady(event.nameEntityId(), event.nameEntityConstant(), event.thumbnailPath()));
        } catch (Exception e) {
            System.err.println("Failed to create name entity thumbnail: " + event.nameEntityConstant() + " nameEntityId: " + event.nameEntityId());
            throw e;
        }
    }

    private void onDeleteNameEntity(MediaUpdateEvent.NameEntityDeleted event) throws Exception {
        System.out.println("Received delete name entity: " + event.nameEntityConstant() + " nameEntityId: " + event.nameEntityId());
        try {
            minIOService.removeFile(ContentMetaData.THUMBNAIL_BUCKET, event.thumbnailPath());
        } catch (Exception e) {
            System.err.println("Failed to delete name entity thumbnail: " + event.nameEntityConstant() + " nameEntityId: " + event.nameEntityId());
            throw e;
        }
    }

    private MediaNameEntity getNameEntityNameById(MediaNameEntityConstant type, long id) {
        var optional = switch (type) {
            case MediaNameEntityConstant.AUTHORS -> mediaAuthorRepository.findById(id);
            case MediaNameEntityConstant.CHARACTERS -> mediaCharacterRepository.findById(id);
            case MediaNameEntityConstant.TAGS -> mediaTagRepository.findById(id);
            case MediaNameEntityConstant.UNIVERSES -> mediaUniverseRepository.findById(id);
        };
        return optional.orElseThrow(() -> new EntityNotFoundException("Not found"));
    }


    @Transactional
    @KafkaListener(topics = {
            EventTopics.MEDIA_ALL_TOPIC,
            EventTopics.MEDIA_OBJECT_TOPIC
    }, groupId = KafkaRedPandaConfig.MEDIA_GROUP_ID)
    public void handle(@Payload MediaUpdateEvent event, Acknowledgment ack) throws Exception {
        try {
            switch (event) {
                case MediaUpdateEvent.MediaDeleted e -> onDeleteMediaObject(e);
                case MediaUpdateEvent.MediaCreated e -> onCreateMedia(e);
                case MediaUpdateEvent.MediaThumbnailUpdated e -> onUpdateMediaThumbnail(e);
                case MediaUpdateEvent.NameEntityCreated e -> onCreateNameEntity(e);
                case MediaUpdateEvent.NameEntityDeleted e -> onDeleteNameEntity(e);
                default ->
                    // unknown event type → log and skip
                    System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
            }
            ack.acknowledge();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            // throwing the exception lets DefaultErrorHandler apply retry
            throw e;
        }
    }


    // listen to DLQ and print out the event details for now
    @KafkaListener(
            topics = KafkaRedPandaConfig.MEDIA_OBJECT_DLQ_TOPIC,
            groupId = "media-object-dlq-group",
            containerFactory = "dlqListenerContainerFactory"
    )
    public void handleDlq(@Payload MediaUpdateEvent event,
                          Acknowledgment ack,
                          @Header(name = "x-exception-message", required = false) String errorMessage) {
        System.out.println("======= DLQ EVENT DETECTED =======");
        System.out.printf("Error Message: %s\n", errorMessage);

        // Accessing the POJO data directly
        switch (event) {
            case MediaUpdateEvent.MediaDeleted e ->
                System.out.println("Received media object delete event: " + e.mediaType() + " " + e.path());
            case MediaUpdateEvent.MediaCreated e ->
                System.out.println("Received media enrichment update event: " + e.mediaId());
            case MediaUpdateEvent.MediaThumbnailUpdated e ->
                System.out.println("Received media thumbnail update event: " + e.mediaId() + " " + e.num());
            default -> {
                System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
                ack.acknowledge(); // ack on poison event to skip it
            }
        }
        System.out.println("======= =======");
        // ack or it will be re-read from the DLQ on restart or rehandle it manually.
        //ack.acknowledge();
    }

}
