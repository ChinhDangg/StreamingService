package dev.chinh.streamingservice.mediaobject;

import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.mediaobject.config.KafkaRedPandaConfig;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaObjectEventConsumer {

    private final MinIOService minIOService;

    Logger logger = LoggerFactory.getLogger(MediaObjectEventConsumer.class);

    private void onDeleteMediaObject(MediaUpdateEvent.MediaObjectDeleted event) throws Exception {
        System.out.println("Received media object delete event: " + event.mediaType() + " " + event.path());
        MediaType mediaType = event.mediaType();

        if (mediaType == MediaType.VIDEO || mediaType == MediaType.GROUPER) {
            try {
                if (event.hasThumbnail())
                    minIOService.removeFile(ContentMetaData.THUMBNAIL_BUCKET, event.thumbnail());
            } catch (Exception e) {
                System.out.println("Failed to delete thumbnail file: " + event.thumbnail());
                throw e;
            }
        }

        if (mediaType == MediaType.VIDEO) {
            try {
                minIOService.removeFile(event.bucket(), event.path());
            } catch (Exception e) {
                System.out.println("Failed to delete media file: " + event.path());
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
                System.out.println("Failed to delete media files in album: " + event.path());
                throw e;
            }
        }
    }

    private void onDeleteThumbnailObject(MediaUpdateEvent.ThumbnailObjectDeleted event) throws Exception {
        System.out.println("Received thumbnail object delete event: " + event.path());
        try {
            minIOService.removeFile(ContentMetaData.THUMBNAIL_BUCKET, event.path());
        } catch (Exception e) {
            System.out.println("Failed to delete thumbnail file: " + event.path());
            throw e;
        }
    }


    @KafkaListener(topics = KafkaRedPandaConfig.MEDIA_OBJECT_TOPIC, groupId = KafkaRedPandaConfig.MEDIA_GROUP_ID)
    public void handle(@Payload MediaUpdateEvent event, Acknowledgment acknowledgment) throws Exception {
        try {
            if (event instanceof MediaUpdateEvent.MediaObjectDeleted e) {
                onDeleteMediaObject(e);
            } else if (event instanceof MediaUpdateEvent.ThumbnailObjectDeleted e) {
                onDeleteThumbnailObject(e);
            } else {
                // unknown event type → log and skip
                System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            System.err.println(e.getMessage());
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
            default -> {
                System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
                ack.acknowledge(); // ack on poison event to skip it
            }
        }

        // ack or it will be re-read from the DLQ on restart or rehandle it manually.
        //ack.acknowledge();
    }

}
