package dev.chinh.streamingservice.mediaobject;

import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.mediaobject.config.KafkaRedPandaConfig;
import dev.chinh.streamingservice.mediaobject.probe.ImageMetadata;
import dev.chinh.streamingservice.mediaobject.probe.MediaProbe;
import dev.chinh.streamingservice.mediaobject.probe.VideoMetadata;
import dev.chinh.streamingservice.persistence.entity.MediaMetaData;
import dev.chinh.streamingservice.persistence.repository.MediaMetaDataRepository;
import io.minio.Result;
import io.minio.messages.Item;
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

    private final ApplicationEventPublisher eventPublisher;

    Logger logger = LoggerFactory.getLogger(MediaObjectEventConsumer.class);

    private void onDeleteMediaObject(MediaUpdateEvent.MediaObjectDeleted event) throws Exception {
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
                // there is no filesystem in object storage, so we need to add the trailing slash to delete the exact path
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

    private void onDeleteThumbnailObject(MediaUpdateEvent.ThumbnailObjectDeleted event) throws Exception {
        System.out.println("Received thumbnail object delete event: " + event.path());
        try {
            minIOService.removeFile(ContentMetaData.THUMBNAIL_BUCKET, event.path());
        } catch (Exception e) {
            System.err.println("Failed to delete thumbnail file: " + event.path());
            throw e;
        }
    }

    @Transactional
    public void onUpdateMediaEnrichment(MediaUpdateEvent.MediaUpdateEnrichment event) throws Exception {
        System.out.println("Received media enrichment update event: " + event.mediaId());
        try {
            MediaMetaData mediaMetaData = mediaMetaDataRepository.findByIdWithAllInfo(event.mediaId()).orElseThrow(
                    () -> new IllegalArgumentException("Media not found: " + event.mediaId())
            );
            if (event.mediaType() == MediaType.VIDEO) {
                VideoMetadata videoMetadata = mediaProbe.parseMediaMetadata(mediaProbe.probeMediaInfo(mediaMetaData.getBucket(), mediaMetaData.getPath()), VideoMetadata.class);
                mediaMetaData.setFrameRate(videoMetadata.frameRate());
                mediaMetaData.setFormat(videoMetadata.format());
                mediaMetaData.setSize(videoMetadata.size());
                mediaMetaData.setWidth(videoMetadata.width());
                mediaMetaData.setHeight(videoMetadata.height());
                mediaMetaData.setLength((int) videoMetadata.durationSeconds());
                mediaMetaData.setThumbnail(thumbnailService.generateThumbnailFromVideo(
                        mediaMetaData.getId(), mediaMetaData.getBucket(), mediaMetaData.getPath(), mediaMetaData.getLength(), null));
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
                    mediaMetaData.setThumbnail(thumbnailService.copyAlbumObjectToThumbnailBucket(mediaMetaData.getId(), mediaMetaData.getBucket(), firstImage));
                } else if (firstVideo != null) {
                    VideoMetadata videoMetadata = mediaProbe.parseMediaMetadata(mediaProbe.probeMediaInfo(mediaMetaData.getBucket(), firstVideo), VideoMetadata.class);
                    mediaMetaData.setWidth(videoMetadata.width());
                    mediaMetaData.setHeight(videoMetadata.height());
                    mediaMetaData.setFormat(videoMetadata.format());
                    mediaMetaData.setFrameRate(videoMetadata.frameRate());
                    mediaMetaData.setThumbnail(thumbnailService.generateThumbnailFromVideo(
                            mediaMetaData.getId(), mediaMetaData.getBucket(), firstVideo, (int) videoMetadata.durationSeconds(), null));
                }
            } else if (event.mediaType() == MediaType.GROUPER) {
                ImageMetadata imageMetadata = mediaProbe.parseMediaMetadata(mediaProbe.probeMediaInfo(ContentMetaData.THUMBNAIL_BUCKET, mediaMetaData.getThumbnail()), ImageMetadata.class);
                mediaMetaData.setSize(imageMetadata.size());
                mediaMetaData.setWidth(imageMetadata.width());
                mediaMetaData.setHeight(imageMetadata.height());
                mediaMetaData.setFormat(imageMetadata.format());
            }

            // send event to update the media search index again with new metadata
            if (event.mediaSearchTopic() != null && event.mediaCreatedEvent() != null) {
                eventPublisher.publishEvent(event);
            }
        } catch (Exception e) {
            System.err.println("Failed to update media enrichment: " + event.mediaId());
            throw e;
        }
    }

    @Transactional
    public void onUpdateMediaThumbnail(MediaUpdateEvent.MediaThumbnailUpdated event) throws Exception {
        System.out.println("Received media thumbnail update event: " + event.mediaId() + " " + event.num());
        MediaMetaData mediaMetaData = mediaMetaDataRepository.findByIdWithAllInfo(event.mediaId()).orElseThrow(
                () -> new IllegalArgumentException("Media not found: " + event.mediaId())
        );
        if (event.mediaType() == MediaType.VIDEO) {
            if (event.num() >= 0 && event.num() < mediaMetaData.getLength()) {
                String oldThumbnail = mediaMetaData.getThumbnail();
                mediaMetaData.setThumbnail(thumbnailService.generateThumbnailFromVideo(
                        mediaMetaData.getId(),
                        mediaMetaData.getBucket(),
                        mediaMetaData.getPath(),
                        mediaMetaData.getLength(),
                        (double) event.num()
                ));
                minIOService.removeFile(ContentMetaData.THUMBNAIL_BUCKET, oldThumbnail);
            }
        } else if (event.mediaType() == MediaType.ALBUM) {
            if (event.num() >= 0 && event.num() < mediaMetaData.getLength()) {
                var results = minIOService.getAllItemsInBucketWithPrefix(mediaMetaData.getBucket(), mediaMetaData.getPath());
                int count = 0;
                String objectAtNum = null;
                for (Result<Item> result : results) {
                    if (count == event.num()) {
                        objectAtNum = result.get().objectName();
                        break;
                    }
                    count++;
                }
                if (objectAtNum != null) {
                    MediaType mediaType = MediaType.detectMediaType(objectAtNum);
                    String oldThumbnail = mediaMetaData.getThumbnail();
                    if (mediaType == MediaType.IMAGE) {
                        mediaMetaData.setThumbnail(thumbnailService.copyAlbumObjectToThumbnailBucket(mediaMetaData.getId(), mediaMetaData.getBucket(), objectAtNum));
                        minIOService.removeFile(ContentMetaData.THUMBNAIL_BUCKET, oldThumbnail);
                    } else if (mediaType == MediaType.VIDEO) {
                        VideoMetadata videoMetadata = mediaProbe.parseMediaMetadata(mediaProbe.probeMediaInfo(mediaMetaData.getBucket(), objectAtNum), VideoMetadata.class);
                        mediaMetaData.setThumbnail(thumbnailService.generateThumbnailFromVideo(
                                mediaMetaData.getId(), mediaMetaData.getBucket(), objectAtNum, (int) videoMetadata.durationSeconds(), null));
                        minIOService.removeFile(ContentMetaData.THUMBNAIL_BUCKET, oldThumbnail);
                    }
                }
            }
        }
    }


    @Transactional
    @KafkaListener(topics = KafkaRedPandaConfig.MEDIA_OBJECT_TOPIC, groupId = KafkaRedPandaConfig.MEDIA_GROUP_ID)
    public void handle(@Payload MediaUpdateEvent event, Acknowledgment ack) throws Exception {
        try {
            switch (event) {
                case MediaUpdateEvent.MediaObjectDeleted e -> onDeleteMediaObject(e);
                case MediaUpdateEvent.ThumbnailObjectDeleted e -> onDeleteThumbnailObject(e);
                case MediaUpdateEvent.MediaUpdateEnrichment e -> onUpdateMediaEnrichment(e);
                case MediaUpdateEvent.MediaThumbnailUpdated e -> onUpdateMediaThumbnail(e);
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
            case MediaUpdateEvent.MediaObjectDeleted e ->
                System.out.println("Received media object delete event: " + e.mediaType() + " " + e.path());
            case MediaUpdateEvent.ThumbnailObjectDeleted e ->
                System.out.println("Received thumbnail object delete event: " + e.path());
            case MediaUpdateEvent.MediaUpdateEnrichment e ->
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
