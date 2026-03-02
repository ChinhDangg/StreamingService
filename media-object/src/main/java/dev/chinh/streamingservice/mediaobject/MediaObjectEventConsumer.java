package dev.chinh.streamingservice.mediaobject;

import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.mediaobject.config.KafkaRedPandaConfig;
import dev.chinh.streamingservice.mediaobject.probe.ImageMetadata;
import dev.chinh.streamingservice.mediaobject.probe.MediaProbe;
import dev.chinh.streamingservice.mediaobject.probe.VideoMetadata;
import dev.chinh.streamingservice.persistence.entity.MediaMetaData;
import dev.chinh.streamingservice.persistence.repository.*;
import io.minio.messages.DeleteObject;
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

import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaObjectEventConsumer {

    private final MinIOService minIOService;
    private final MediaProbe mediaProbe;
    private final ThumbnailService thumbnailService;

    private final MediaMetaDataRepository mediaMetaDataRepository;

    private final ApplicationEventPublisher eventPublisher;


    Logger logger = LoggerFactory.getLogger(MediaObjectEventConsumer.class);

    private MediaMetaData getMediaMetadataById(long id) {
        return mediaMetaDataRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Media not found: " + id)
        );
    }

    @Transactional
    public void onMediaEnrich(MediaUpdateEvent.MediaEnriched event) throws Exception {
        logger.info("Received media enrichment update event: {} {}", event.mediaId(), event.mediaType());
        try {
            MediaMetaData mediaMetaData = getMediaMetadataById(event.mediaId());
            if (event.mediaType() == MediaType.VIDEO) {
                VideoMetadata videoMetadata = mediaProbe.parseMediaMetadata(mediaProbe.probeMediaInfo(mediaMetaData.getBucket(), mediaMetaData.getKey()), VideoMetadata.class);
                mediaMetaData.setFrameRate(videoMetadata.frameRate());
                mediaMetaData.setFormat(videoMetadata.format());
                mediaMetaData.setSize(videoMetadata.size());
                mediaMetaData.setWidth(videoMetadata.width());
                mediaMetaData.setHeight(videoMetadata.height());
                mediaMetaData.setLength((int) videoMetadata.durationSeconds());
                mediaMetaData.setThumbnail(thumbnailService.generateThumbnailFromVideo(mediaMetaData.getBucket(), mediaMetaData.getKey(), event.thumbnailObject(), mediaMetaData.getLength(), null));
            } else {
                mediaMetaData.setSize(event.size());
                mediaMetaData.setLength(event.length());

                MediaType thumbnailMediaType = MediaType.detectMediaType(mediaMetaData.getKey());
                if (thumbnailMediaType == MediaType.IMAGE) {
                    ImageMetadata imageMetadata = mediaProbe.parseMediaMetadata(mediaProbe.probeMediaInfo(mediaMetaData.getBucket(), mediaMetaData.getKey()), ImageMetadata.class);
                    mediaMetaData.setWidth(imageMetadata.width());
                    mediaMetaData.setHeight(imageMetadata.height());
                    mediaMetaData.setFormat(imageMetadata.format());
                    mediaMetaData.setThumbnail(thumbnailService.copyAlbumObjectToThumbnailBucket(mediaMetaData.getBucket(), mediaMetaData.getKey(), event.thumbnailObject()));
                } else if (thumbnailMediaType == MediaType.VIDEO) {
                    VideoMetadata videoMetadata = mediaProbe.parseMediaMetadata(mediaProbe.probeMediaInfo(mediaMetaData.getBucket(), mediaMetaData.getKey()), VideoMetadata.class);
                    mediaMetaData.setWidth(videoMetadata.width());
                    mediaMetaData.setHeight(videoMetadata.height());
                    mediaMetaData.setFormat(videoMetadata.format());
                    mediaMetaData.setFrameRate(videoMetadata.frameRate());
                    mediaMetaData.setThumbnail(thumbnailService.generateThumbnailFromVideo(mediaMetaData.getBucket(), mediaMetaData.getKey(), event.thumbnailObject(), (int) videoMetadata.durationSeconds(), null));
                }
            }
            mediaMetaDataRepository.save(mediaMetaData);

            String topic = event.searchable()
                    ? EventTopics.MEDIA_FILE_SEARCH_AND_BACKUP_TOPIC
                    : EventTopics.MEDIA_FILE_TOPIC; // not searchable - no thumbnail - no backup to save the thumbnail
            eventPublisher.publishEvent(new MediaObjectEventProducer.EventWrapper(
                    topic,
                    new MediaUpdateEvent.MediaCreatedReady(
                            event.fileId(),
                            event.mediaId(),
                            event.mediaType(),
                            mediaMetaData.getThumbnail(),
                            mediaMetaData.getLength())
            ));
        } catch (Exception e) {
            logger.error("Failed to update media enrichment: {} {}", event.mediaId(), event.mediaType());
            throw e;
        }
    }

    private void onDeleteObject(MediaUpdateEvent.ObjectDeleted event) {
        logger.info("Received media object delete event: {}: {} objects", event.bucket(), event.objectNames().size());
        try {
            List<DeleteObject> objects = event.objectNames().stream().map(DeleteObject::new).toList();
            if (event.bucket() == null) {
                System.err.println("Bucket is null, skipping delete objects");
                return;
            }
            minIOService.removeBulk(event.bucket(), objects);
        } catch (Exception e) {
            logger.error("Failed to delete objects: {}: {}", event.bucket(), event.objectNames().size());
            throw e;
        }
    }

    @Transactional
    public void onUpdateMediaThumbnail(MediaUpdateEvent.MediaThumbnailUpdated event) throws Exception {
        System.out.println("Received media thumbnail update event: " + event.mediaId() + " " + event.num());
        MediaMetaData mediaMetaData = getMediaMetadataById(event.mediaId());

        boolean sameName = event.thumbnailObject().equals(mediaMetaData.getThumbnail());

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
        } else if (!sameName) {
            thumbnailService.copyAlbumObjectToThumbnailBucket(event.bucket(), event.thumbnailObject(), event.thumbnailObject());
            minIOService.removeFile(event.bucket(), mediaMetaData.getThumbnail());

            mediaMetaDataRepository.updateMediaThumbnail(mediaMetaData.getId(), event.thumbnailObject());
        }

        String topic = sameName ? EventTopics.MEDIA_BACKUP_TOPIC : EventTopics.MEDIA_FILE_SEARCH_AND_BACKUP_TOPIC;
        eventPublisher.publishEvent(new MediaObjectEventProducer.EventWrapper(
                topic,
                new MediaUpdateEvent.MediaThumbnailUpdatedReady(event.mediaId(), mediaMetaData.getThumbnail(), event.thumbnailObject()))
        );
    }

    private void onDeleteThumbnail(MediaUpdateEvent.ThumbnailDeleted event) throws Exception {
        System.out.println("Received delete thumbnail object: " + event.objectName());
        try {
            minIOService.removeFile(ContentMetaData.THUMBNAIL_BUCKET, event.objectName());
        } catch (Exception e) {
            System.err.println("Failed to delete thumbnail object: " + event.objectName());
            throw e;
        }
    }


    @Transactional
    @KafkaListener(topics = {
            EventTopics.MEDIA_OBJECT_TOPIC,
            EventTopics.MEDIA_OBJECT_AND_BACKUP_TOPIC
    }, groupId = KafkaRedPandaConfig.MEDIA_GROUP_ID)
    public void handle(@Payload MediaUpdateEvent event, Acknowledgment ack) throws Exception {
        try {
            switch (event) {
                case MediaUpdateEvent.MediaEnriched e -> onMediaEnrich(e);
                case MediaUpdateEvent.ObjectDeleted e -> onDeleteObject(e);
                case MediaUpdateEvent.MediaThumbnailUpdated e -> onUpdateMediaThumbnail(e);
                case MediaUpdateEvent.ThumbnailDeleted e -> onDeleteThumbnail(e);
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
            case MediaUpdateEvent.MediaEnriched e ->
                System.out.println("Received media enrichment update event: " + e.mediaId());
            case MediaUpdateEvent.ObjectDeleted e ->
                System.out.println("Received media object delete event: " + e.bucket() + " " + e.objectNames());
            case MediaUpdateEvent.MediaThumbnailUpdated e ->
                System.out.println("Received media thumbnail update event: " + e.mediaId() + " " + e.num());
            case MediaUpdateEvent.ThumbnailDeleted e ->
                System.out.println("Received delete thumbnail object: " + e.objectName());
            default -> {
                System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
                //ack.acknowledge(); // ack on poison event to skip it
            }
        }
        System.out.println("======= =======");
        // ack or it will be re-read from the DLQ on restart or rehandle it manually.
        //ack.acknowledge();
    }

}
