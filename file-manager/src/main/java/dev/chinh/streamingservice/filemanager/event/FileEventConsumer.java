package dev.chinh.streamingservice.filemanager.event;

import com.mongodb.client.result.UpdateResult;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.filemanager.config.KafkaConfig;
import dev.chinh.streamingservice.filemanager.service.FileConsumerService;
import lombok.AllArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class FileEventConsumer {

    private final FileConsumerService fileConsumerService;

    private void onCreateFile(MediaUpdateEvent.FileCreated event) {
        System.out.println("Received create file event");
        try {
            fileConsumerService.handleCreateFile(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create file", e);
        }
    }

    private void onDirectoryToMedia(MediaUpdateEvent.DirectoryToMediaInitiated event) {
        System.out.println("Received initiate directory to media initiated event");
        try {
            fileConsumerService.handleDirectoryToMedia(event);
        } catch (Exception e) {
            System.err.println("Failed to initiate directory to media");
            throw e;
        }
    }

    private void onNestedDirectoryToMedia(MediaUpdateEvent.NestedDirectoryToMediaInitiated event) {
        System.out.println("Received initiate nested directory to media initiated event");
        try {
            fileConsumerService.handleNestedDirectoryToMedia(event);
        } catch (Exception e) {
            System.err.println("Failed to initiate nested directory to media");
            throw e;
        }
    }

    private void onCompleteFileToMedia(MediaUpdateEvent.MediaCreatedReady event) {
        System.out.println("Received media create event from file: " + event.mediaId());
        try {
            UpdateResult result = fileConsumerService.handleCompleteFileToMedia(event);
            if (result.getModifiedCount() != 1)
                throw new RuntimeException("Failed to update file to media");
        } catch (Exception e) {
            System.err.println("Failed to update file to media");
            throw e;
        }
    }

    private void onInitiateUpdateMediaThumbnail(MediaUpdateEvent.MediaThumbnailUpdateInitiated event) {
        System.out.println("Received initiate update media thumbnail event");
        try {
            fileConsumerService.handleInitiateUpdateMediaThumbnail(event);
        } catch (Exception e) {
            System.err.println("Failed to initiate update media thumbnail");
            throw e;
        }
    }

    private void onUpdateMediaThumbnail(MediaUpdateEvent.MediaThumbnailUpdatedReady event) {
        System.out.println("Received update media thumbnail name: " + event.mediaId());
        try {
            fileConsumerService.handleUpdateMediaThumbnail(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update thumbnail name for media " + event.mediaId(), e);
        }
    }

    private void onDeleteFile(MediaUpdateEvent.FileDeleted event) {
        System.out.println("Received file delete event: " + event.fileId());
        try {
            fileConsumerService.handleDeleteFile(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file", e);
        }
    }

    private void onMoveFile(MediaUpdateEvent.DirectoryMoved event) {
        System.out.println("Received file move event: from: " + event.fileId() + " to: " + event.parentId());
        try {
            fileConsumerService.handleMoveDirectory(event.userId(), event.fileId(), event.parentId(), event.oldIdPath());
        } catch (Exception e) {
            throw new RuntimeException("Failed to move file", e);
        }
    }


    @KafkaListener(topics = {
            EventTopics.MEDIA_FILE_TOPIC,
            EventTopics.MEDIA_FILE_AND_BACKUP_TOPIC,
            EventTopics.MEDIA_FILE_SEARCH_AND_BACKUP_TOPIC,
            EventTopics.MEDIA_FILE_UPLOAD_SEARCH_AND_BACKUP_TOPIC
    }, groupId = KafkaConfig.MEDIA_GROUP_ID)
    public void handle(@Payload MediaUpdateEvent event, Acknowledgment ack) {
        try {
            switch (event) {
                case MediaUpdateEvent.FileCreated e -> onCreateFile(e);
                case MediaUpdateEvent.FileDeleted e -> onDeleteFile(e);
                case MediaUpdateEvent.DirectoryToMediaInitiated e -> onDirectoryToMedia(e);
                case MediaUpdateEvent.NestedDirectoryToMediaInitiated e -> onNestedDirectoryToMedia(e);
                case MediaUpdateEvent.MediaCreatedReady e -> onCompleteFileToMedia(e);
                case MediaUpdateEvent.MediaThumbnailUpdateInitiated e -> onInitiateUpdateMediaThumbnail(e);
                case MediaUpdateEvent.MediaThumbnailUpdatedReady e -> onUpdateMediaThumbnail(e);
                case MediaUpdateEvent.DirectoryMoved e -> onMoveFile(e);
                default ->
                    System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
            }
            ack.acknowledge();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @KafkaListener(
            topics = KafkaConfig.MEDIA_FILE_DLQ_TOPIC,
            groupId = "media-file-dlq-group",
            containerFactory = "dlqListenerContainerFactory"
    )
    public void handleDlq(@Payload MediaUpdateEvent event,
                          Acknowledgment ack,
                          @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) byte[] errorMessage) {
        System.out.println("======= DLQ EVENT DETECTED =======");
        String message = errorMessage != null ? new String(errorMessage) : "No error message found";
        System.out.printf("Error Message: %s\n", message);

        switch (event) {
            case MediaUpdateEvent.FileCreated e ->
                    System.out.println("Received create file event: " + e.objectName());
            case MediaUpdateEvent.FileDeleted e ->
                    System.out.println("Received file delete event: " + e.fileId());
            case MediaUpdateEvent.DirectoryToMediaInitiated e ->
                    System.out.println("Received initiate directory to media initiated event: " + e.fileId());
            case MediaUpdateEvent.NestedDirectoryToMediaInitiated e ->
                    System.out.println("Received initiate nested directory to media initiated event: " + e.fileId());
            case MediaUpdateEvent.MediaCreatedReady e ->
                    System.out.println("Received create media event: " + e.mediaId());
            case MediaUpdateEvent.MediaThumbnailUpdateInitiated e ->
                    System.out.println("Received initiate update media thumbnail event: " + e.mediaId());
            case MediaUpdateEvent.MediaThumbnailUpdatedReady e ->
                    System.out.println("Received update media thumbnail name: " + e.mediaId());
            case MediaUpdateEvent.DirectoryMoved e ->
                    System.out.println("Received file move event: from: " + e.fileId() + " to: " + e.parentId());
            default -> {
                System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
                ack.acknowledge();
            }
        }
        System.out.println("======= =======");
    }
}
