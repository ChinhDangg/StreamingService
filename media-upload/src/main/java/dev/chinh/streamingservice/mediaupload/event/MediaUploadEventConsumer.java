package dev.chinh.streamingservice.mediaupload.event;

import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.mediapersistence.projection.NameEntityDTO;
import dev.chinh.streamingservice.mediaupload.event.config.KafkaRedPandaConfig;
import dev.chinh.streamingservice.mediaupload.modify.service.MediaMetadataModifyService;
import dev.chinh.streamingservice.mediaupload.modify.service.NameEntityModifyService;
import dev.chinh.streamingservice.mediaupload.upload.service.MediaUploadService;
import lombok.AllArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@AllArgsConstructor
public class MediaUploadEventConsumer {

    private final MediaUploadService mediaUploadService;
    private final MediaMetadataModifyService mediaMetadataModifyService;

    public void onInitiateFileToMedia(MediaUpdateEvent.FileToMediaInitiated event) {
        System.out.println("Received file to media initiated event: " + event.fileId());
        try {
            mediaUploadService.handleInitiateFileToMedia(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initiate file to media", e);
        }
    }

    private void onDeleteMedia(MediaUpdateEvent.FileDeleted event) {
        if (event.mediaId() == null) {
            System.out.println("Media ID is null, skipping delete media event");
            return;
        }
        System.out.println("Received delete media event: " + event.mediaId());
        try {
            mediaMetadataModifyService.deleteMedia(event.userId(), event.mediaId());
        } catch (Exception e) {
            System.err.println("Failed to delete media: " + event.mediaId());
            throw e;
        }
    }

    private void onMoveGrouperItem(MediaUpdateEvent.GrouperItemMoved event) {
        System.out.println("Received moving grouper item: " + event.childMediaId());
        try {
            if (event.parentMediaId() != null) {
                String error = mediaUploadService.addMediaToGrouper(event.parentMediaId(), event.childMediaId(), event.fileName());
                if (error != null)
                    System.err.println(error);
            } else {
                // a grouper item is moved out of a grouper and not into another grouper - delete media info
                mediaMetadataModifyService.deleteMedia(event.userId(), event.childMediaId());
            }
        } catch (Exception e) {
            System.err.println("Failed to move grouper item outside grouper: " + event.childMediaId());
            throw e;
        }
    }

    private void onUpdateMediaLength(MediaUpdateEvent.MediaFileLengthUpdate event) {
        System.out.println("Received update media length: " + event.mediaId() + " " + event.length());
        try {
            mediaMetadataModifyService.updateMediaLengthWithDelta(Long.parseLong(event.userId()), event.mediaId(), event.length());
        } catch (Exception e) {
            System.err.println("Failed to update media length: " + event.mediaId() + " " + event.length());
            throw e;
        }
    }

    private void onUpdateMediaPreview(MediaUpdateEvent.MediaPreviewUpdated event) {
        System.out.println("Received create media preview: " + event.mediaId());
        try {
            mediaMetadataModifyService.updateMediaPreview(event.userId(), event.mediaId(), event.previewObject());
        } catch (Exception e) {
            System.err.println("Failed to create media preview: " + event.mediaId());
            throw e;
        }
    }



    @KafkaListener(topics = {
            EventTopics.MEDIA_UPLOAD_TOPIC,
            EventTopics.MEDIA_FILE_UPLOAD_SEARCH_AND_BACKUP_TOPIC
    }, groupId = KafkaRedPandaConfig.MEDIA_GROUP_ID)
    public void handle(@Payload MediaUpdateEvent event, Acknowledgment ack) {
        try {
            switch (event) {
                case MediaUpdateEvent.FileToMediaInitiated e -> onInitiateFileToMedia(e);
                case MediaUpdateEvent.FileDeleted e -> onDeleteMedia(e);
                case MediaUpdateEvent.GrouperItemMoved e -> onMoveGrouperItem(e);
                case MediaUpdateEvent.MediaFileLengthUpdate e -> onUpdateMediaLength(e);
                case MediaUpdateEvent.MediaPreviewUpdated e -> onUpdateMediaPreview(e);


                case MediaUpdateEvent.ControlAddAuthor e -> controlAddAuthor(e.userId(), e.author());
                case MediaUpdateEvent.ControlAddTag e -> controlAddTag(e.userId(), e.tag());
                case MediaUpdateEvent.ControlAddNameEntitiesToMedia e -> controlAddNameEntitiesToMedia(e.userId(), e.mediaId(), e.nameEntityIds(), e.nameEntityConstant());
                default -> {
                    System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
                }
            }
            ack.acknowledge();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @KafkaListener(
            topics = KafkaRedPandaConfig.MEDIA_UPLOAD_DLQ_TOPIC,
            groupId = "media-upload-dlq-group",
            containerFactory = "dlqListenerContainerFactory"
    )
    public void handleDlq(@Payload MediaUpdateEvent event,
                          Acknowledgment ack,
                          @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) byte[] errorMessage) {
        System.out.println("======= DLQ EVENT DETECTED =======");
        String message = errorMessage != null ? new String(errorMessage) : "No error message found";
        System.out.printf("Error Message: %s\n", message);
        switch (event) {
            case MediaUpdateEvent.FileToMediaInitiated e ->
                System.out.println("Received initiate file to media initiated event: " + e.fileId());
            case MediaUpdateEvent.FileDeleted e ->
                System.out.println("Received file delete event: " + e.fileId());
            case MediaUpdateEvent.GrouperItemMoved e ->
                System.out.println("Received move grouper item event: " + e.childMediaId());
            case MediaUpdateEvent.MediaFileLengthUpdate e ->
                System.out.println("Received update media length event: " + e.mediaId() + " " + e.length());
            case MediaUpdateEvent.MediaPreviewUpdated e ->
                System.out.println("Received create media preview event: " + e.mediaId());
            default -> {
                System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
            }
        }

        System.out.println("======= =======");
    }




    private final NameEntityModifyService nameEntityModifyService;
    private void controlAddAuthor(String userId, String author) {
        nameEntityModifyService.addAuthor(userId, author);
    }

    private void controlAddTag(String userId, String tag) {
        nameEntityModifyService.addTag(userId, tag);
    }

    private void controlAddNameEntitiesToMedia(String userId, long mediaId, Long[] nameEntityIds, MediaNameEntityConstant nameEntity) {
        List<NameEntityDTO> addingIds = Arrays.stream(nameEntityIds)
                .map(id -> new NameEntityDTO(id, null))
                .toList();
        mediaMetadataModifyService.updateNameEntityInMedia(
                userId,
                new MediaMetadataModifyService.UpdateList(
                        addingIds, null, nameEntity
                ),
                mediaId,
                true,
                null
        );
    }
}
