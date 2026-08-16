package dev.chinh.streamingservice.filemanager.event;

import com.mongodb.client.result.UpdateResult;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.filemanager.config.KafkaConfig;
import dev.chinh.streamingservice.filemanager.service.FileService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class FileEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(FileEventConsumer.class);
    private final ObservationRegistry observationRegistry;

    private final FileEventConsumerService fileEventConsumerService;
    private final FileService fileService;

    private void onCreateFile(MediaUpdateEvent.FileCreated event) {
        log.info("Received create file event: {}", event.fileName());
        try {
            fileEventConsumerService.handleCreateFile(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create file", e);
        }
    }

    private void onDirectoryToAlbumMedia(MediaUpdateEvent.DirectoryToAlbumMediaInitiated event) {
        log.info("Received initiate directory to album media initiated event: {}", event.fileId());
        try {
            fileEventConsumerService.handleDirectoryToAlbumMedia(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initiate directory to album media", e);
        }
    }

    private void onNestedDirectoryToGrouperMedia(MediaUpdateEvent.NestedDirectoryToGrouperMediaInitiated event) {
        log.info("Received initiate nested directory to grouper media initiated event: {}", event.fileId());
        try {
            fileEventConsumerService.handleNestedDirectoryToGrouperMedia(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initiate nested directory to grouper media", e);
        }
    }

    private void onCompleteFileToMedia(MediaUpdateEvent.MediaCreatedReady event) {
        log.info("Received create media event: {} {}", event.fileId(), event.mediaId());
        try {
            UpdateResult result = fileEventConsumerService.handleCompleteFileToMedia(event);
            if (result.getModifiedCount() != 1)
                throw new RuntimeException("Failed to update file to media");
        } catch (Exception e) {
            throw new RuntimeException("Failed to update file to media", e);
        }
    }

    private void onInitiateUpdateMediaThumbnail(MediaUpdateEvent.MediaThumbnailUpdateInitiated event) {
        log.info("Received initiate update media thumbnail event: {}", event.mediaId());
        try {
            fileEventConsumerService.handleInitiateUpdateMediaThumbnail(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initiate update media thumbnail", e);
        }
    }

    private void onUpdateMediaThumbnail(MediaUpdateEvent.MediaThumbnailUpdatedReady event) {
        log.info("Received update media thumbnail name: {}", event.mediaId());
        try {
            fileEventConsumerService.handleUpdateMediaThumbnail(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update thumbnail name for media " + event.mediaId(), e);
        }
    }

    private void onDeleteFile(MediaUpdateEvent.FileDeleted event) {
        log.info("Received file delete event: {}", event.fileId());
        try {
            fileEventConsumerService.handleDeleteFile(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file", e);
        }
    }

    private void onMoveFile(MediaUpdateEvent.DirectoryMoved event) {
        log.info("Received file move event: from: {} to: {}", event.fileId(), event.newParentId());
        try {
            fileEventConsumerService.handleMoveDirectory(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to move file", e);
        }
    }


    private void handleFileEvents(MediaUpdateEvent event, Acknowledgment ack) {
        try {
            switch (event) {
                case MediaUpdateEvent.FileCreated e -> onCreateFile(e);
                case MediaUpdateEvent.FileDeleted e -> onDeleteFile(e);
                case MediaUpdateEvent.DirectoryToAlbumMediaInitiated e -> onDirectoryToAlbumMedia(e);
                case MediaUpdateEvent.NestedDirectoryToGrouperMediaInitiated e -> onNestedDirectoryToGrouperMedia(e);
                case MediaUpdateEvent.MediaCreatedReady e -> onCompleteFileToMedia(e);

                case MediaUpdateEvent.MediaThumbnailUpdateInitiated e -> onInitiateUpdateMediaThumbnail(e);
                case MediaUpdateEvent.MediaThumbnailUpdatedReady e -> onUpdateMediaThumbnail(e);
                case MediaUpdateEvent.DirectoryMoved e -> onMoveFile(e);

                case MediaUpdateEvent.ControlAddAsVideo e -> controlAddAsVideo(e.userId(), e.fileId());
                case MediaUpdateEvent.ControlAddAsAlbum e -> controlAddAsAlbum(e.userId(), e.fileId());
                case MediaUpdateEvent.ControlAddAsGrouper e -> controlAddAsGrouper(e.userId(), e.fileId());
                default ->
                        System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.warn("Failed to handle media event: {}", event, e);
            throw e;
        }
    }

    @KafkaListener(topics = {
            EventTopics.MEDIA_FILE_TOPIC,
            EventTopics.MEDIA_FILE_AND_BACKUP_TOPIC,
            EventTopics.MEDIA_FILE_SEARCH_AND_BACKUP_TOPIC,
            EventTopics.MEDIA_FILE_UPLOAD_SEARCH_AND_BACKUP_TOPIC
    }, groupId = KafkaConfig.MEDIA_GROUP_ID)
    public void handle(@Payload MediaUpdateEvent event, Acknowledgment ack) {
        String eventType = event.getClass().getSimpleName();
        Observation.createNotStarted("event.processing.time", observationRegistry)
                .contextualName("process-" + eventType)       // Names the span in Tempo
                .lowCardinalityKeyValue("event_type", eventType) // Adds a tag for Prometheus & Loki
                .observe(() -> handleFileEvents(event, ack));
    }

    @KafkaListener(
            topics = KafkaConfig.MEDIA_FILE_DLQ_TOPIC,
            groupId = "media-file-dlq-group",
            containerFactory = "dlqListenerContainerFactory"
    )
    public void handleDlq(@Payload MediaUpdateEvent event,
                          Acknowledgment ack,
                          @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) byte[] errorMessage) {
        log.error("DLQ EVENT DETECTED: {}", event);
        String message = errorMessage != null ? new String(errorMessage) : "No error message found";
        log.error("DLQ ERROR MESSAGE: {}", message);

        switch (event) {
            case MediaUpdateEvent.FileCreated e ->
                    log.info("File DLQ: Received create file event: {}", e.objectName());
            case MediaUpdateEvent.FileDeleted e ->
                    log.info("File DLQ: Received file delete event: {}", e.fileId());
            case MediaUpdateEvent.NestedDirectoryToGrouperMediaInitiated e ->
                    log.info("File DLQ: Received initiate nested directory to grouper media initiated event: {}", e.fileId());
            case MediaUpdateEvent.DirectoryToAlbumMediaInitiated e ->
                    log.info("File DLQ: Received initiate directory to album media initiated event: {}", e.fileId());
            case MediaUpdateEvent.MediaCreatedReady e ->
                    log.info("File DLQ: Received create media event: {} {}", e.fileId(), e.mediaId());

            case MediaUpdateEvent.MediaThumbnailUpdateInitiated e ->
                    log.info("File DLQ: Received initiate update media thumbnail event: {}", e.mediaId());
            case MediaUpdateEvent.MediaThumbnailUpdatedReady e ->
                    log.info("File DLQ: Received update media thumbnail name: {}", e.mediaId());
            case MediaUpdateEvent.DirectoryMoved e ->
                    log.info("File DLQ: Received file move event: from: {} to: {}", e.fileId(), e.newParentId());
            default -> {
                log.error("File DLQ: Unknown MediaUpdateEvent type: {}", event.getClass());
                ack.acknowledge();
            }
        }
        System.out.println("======= =======");
    }




    private void controlAddAsVideo(String userId, String fileId) {
        String result = fileService.addFileAsVideoMedia(userId, fileId, null);
        System.out.println(result);
    }

    private void controlAddAsAlbum(String userId, String fileId) {
        String result = fileService.addDirectoryAsAlbumMedia(userId, fileId);
        System.out.println(result);
    }

    private void controlAddAsGrouper(String userId, String fileId) {
        String result = fileService.addDirectoryAsGrouperMedia(userId, fileId);
        System.out.println(result);
    }
}
