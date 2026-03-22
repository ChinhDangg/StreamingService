package dev.chinh.streamingservice.mediaobject;

import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.mediaobject.config.KafkaRedPandaConfig;
import dev.chinh.streamingservice.mediapersistence.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaObjectEventConsumer {

    private final MediaObjectService mediaObjectService;

    Logger logger = LoggerFactory.getLogger(MediaObjectEventConsumer.class);

    public void onMediaEnrich(MediaUpdateEvent.MediaEnriched event) throws Exception {
        logger.info("Received media enrichment update event: {} {}", event.mediaId(), event.mediaType());
        try {
            mediaObjectService.handleMediaEnrich(event);
        } catch (Exception e) {
            logger.error("Failed to update media enrichment: {} {}", event.mediaId(), event.mediaType());
            throw e;
        }
    }

    private void onDeleteObject(MediaUpdateEvent.ObjectDeleted event) {
        logger.info("Received media object delete event: {}: {} objects", event.bucket(), event.objectNames().size());
        try {
            mediaObjectService.handleDeleteObject(event);
        } catch (Exception e) {
            logger.error("Failed to delete objects: {}: {}", event.bucket(), event.objectNames().size());
            throw e;
        }
    }

    public void onUpdateMediaThumbnail(MediaUpdateEvent.MediaThumbnailUpdated event) throws Exception {
        System.out.println("Received media thumbnail update event: " + event.mediaId() + " " + event.num());
        try {
            mediaObjectService.handleUpdateMediaThumbnail(event);
        } catch (Exception e) {
            System.err.println("Failed to update media thumbnail: " + event.mediaId() + " " + event.num());
            throw e;
        }
    }

    private void onDeleteThumbnail(MediaUpdateEvent.ThumbnailDeleted event) throws Exception {
        System.out.println("Received delete thumbnail object: " + event.objectName());
        try {
            mediaObjectService.handleDeleteThumbnail(event);
        } catch (Exception e) {
            System.err.println("Failed to delete thumbnail object: " + event.objectName());
            throw e;
        }
    }


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
                          @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) byte[] errorMessage) {
        System.out.println("======= DLQ EVENT DETECTED =======");
        System.out.printf("Error Message: %s\n", errorMessage == null ? "No error message found" : new String(errorMessage));

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
