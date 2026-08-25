package dev.chinh.streamingservice.search.event;

import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.search.config.KafkaRedPandaConfig;
import dev.chinh.streamingservice.search.data.NameEntityField;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class MediaSearchEventConsumer {

    private final MediaSearchEventService mediaSearchEventService;

    private void onCreateMediaIndexSearch(MediaUpdateEvent.MediaCreatedReadyForSearch event) {
        System.out.println("Received new index event: " + event.id());
        try {
            mediaSearchEventService.handleCreateMediaIndexSearch(event);
        } catch (IOException e) {
            throw new RuntimeException("Failed to index media " + event.id() + " to Search", e);
        }
    }

    private void onDeleteMediaIndexSearch(MediaUpdateEvent.FileDeleted event) {
        System.out.println("Received delete index event: " + event.mediaId());
        try {
            mediaSearchEventService.handleDeleteMediaIndexSearch(event.mediaId(), event.mediaType());
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete media index " + event.mediaId() + " from Search", e);
        }
    }

    private void onUpdateMediaNameEntitySearch(MediaUpdateEvent.MediaNameEntityUpdated event) {
        System.out.println("Received update media name entity event: " + event.mediaId());
        try {
            mediaSearchEventService.handleUpdateMediaNameEntitySearch(event.mediaId(), event.nameEntityConstant(), event.nameEntityIdsToNames());
        } catch (IOException e) {
            throw new RuntimeException("Failed to update Search index field for media " + event.mediaId(), e);
        }
    }

    private void onUpdateMediaTitleSearch(MediaUpdateEvent.MediaTitleUpdated event) {
        System.out.println("Received update media title event: " + event.mediaId());
        try {
            mediaSearchEventService.handleUpdateMediaTitleSearch(event.userId(), event.mediaId(), event.title());
        } catch (IOException e) {
            throw new RuntimeException("Failed to update Search index title field for media " + event.mediaId(), e);
        }
    }

    private void onUpdateMediaThumbnailSearch(MediaUpdateEvent.MediaThumbnailUpdatedReady event) {
        System.out.println("Received update media thumbnail event: " + event.mediaId());
        try {
            mediaSearchEventService.handleUpdateMediaThumbnailSearch(event.mediaId(), event.newThumbnail());
        } catch (IOException e) {
            throw new RuntimeException("Failed to update Search index thumbnail field for media " + event.mediaId(), e);
        }
    }

    private void onUpdateMediaLengthSearch(MediaUpdateEvent.LengthUpdated event) {
        System.out.println("Received update length event: " + event.mediaIds());
        System.out.println(event);
        try {
            mediaSearchEventService.handleUpdateMediaLengthSearch(event.mediaIds(), event.deltaLength(), event.version());
        } catch (IOException e) {
            throw new RuntimeException("Failed to update Search index length field for media " + event.mediaIds(), e);
        }
    }

    private void onUpdateMediaPreview(MediaUpdateEvent.MediaPreviewUpdated event) {
        System.out.println("Received create media preview: " + event.mediaId());
        try {
            mediaSearchEventService.handleUpdateMediaPreview(event.mediaId(), event.previewObject());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create media preview: " + event.mediaId(), e);
        }
    }

    private void onMoveGrouperItem(MediaUpdateEvent.GrouperItemMoved event) throws IOException {
        System.out.println("Received moving grouper item: " + event.childMediaId());
        try {
            mediaSearchEventService.handleMoveGrouperItem(event);
        } catch (Exception e) {
            System.err.println("Failed to move grouper item outside grouper: " + event.childMediaId());
            throw e;
        }
    }


    private void onCreateNameEntitySearch(MediaUpdateEvent.NameEntityCreated event) {
        System.out.println("Received create name entity: " + event.nameEntityConstant() + " nameEntityId: " + event.nameEntityId());
        try {
            mediaSearchEventService.handleCreateNameEntitySearch(event.nameEntityConstant(), event.nameEntityId(),
                    new NameEntityField(
                            event.nameEntityId(),
                            Long.parseLong(event.userId()),
                            event.name(),
                            event.length(),
                            event.thumbnailPath()
                    )
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to update Search index field for name entity " + event.nameEntityId(), e);
        }
    }

    private void onDeleteNameEntitySearch(MediaUpdateEvent.NameEntityDeleted event) {
        System.out.println("Received delete name entity");
        try {
            mediaSearchEventService.handleDeleteNameEntitySearch(event.nameEntityId(), event.nameEntityConstant());
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete Search index field for name entity " + event.nameEntityConstant().getName() + ", id:" + event.nameEntityId(), e);
        }
    }

    private void onUpdateNameEntitySearch(MediaUpdateEvent.NameEntityUpdated event) {
        System.out.println("Received update name entity");
        try {
            mediaSearchEventService.handleUpdateNameEntitySearch(event.nameEntityConstant(), event.nameEntityId(),
                    event.newName(),
                    event.oldThumbnail(), event.newThumbnail());
        } catch (IOException e) {
            throw new RuntimeException("Failed to update Search index field for name entity " + event.nameEntityId(), e);
        }
    }

    private void onUpdateNameEntityLength(MediaUpdateEvent.NameEntityLengthUpdated event) {
        System.out.println("Received update name entity length");
        try {
            mediaSearchEventService.handleUpdateNameEntityLength(event.nameEntityConstant(), event.nameEntityIds(), event.deltaLength());
        } catch (IOException e) {
            throw new RuntimeException("Failed to update Search name entity length in batch: " + event.nameEntityConstant().getName() + " for user: " + event.userId());
        }
    }


    @KafkaListener(topics = {
            EventTopics.MEDIA_SEARCH_TOPIC,
            EventTopics.MEDIA_SEARCH_AND_BACKUP_TOPIC,
            EventTopics.MEDIA_FILE_SEARCH_AND_BACKUP_TOPIC,
            EventTopics.MEDIA_FILE_HANDLER_SEARCH_AND_BACKUP_TOPIC,
    }, groupId = KafkaRedPandaConfig.MEDIA_GROUP_ID)
    public void handle(@Payload MediaUpdateEvent event, Acknowledgment ack) throws IOException {
        try {
            switch (event) {
                case MediaUpdateEvent.MediaCreatedReadyForSearch e -> onCreateMediaIndexSearch(e);
                case MediaUpdateEvent.FileDeleted e -> onDeleteMediaIndexSearch(e);
                case MediaUpdateEvent.MediaThumbnailUpdatedReady e -> onUpdateMediaThumbnailSearch(e);
                case MediaUpdateEvent.MediaNameEntityUpdated e -> onUpdateMediaNameEntitySearch(e);
                case MediaUpdateEvent.LengthUpdated e -> onUpdateMediaLengthSearch(e);
                case MediaUpdateEvent.MediaTitleUpdated e -> onUpdateMediaTitleSearch(e);
                case MediaUpdateEvent.MediaPreviewUpdated e -> onUpdateMediaPreview(e);
                case MediaUpdateEvent.GrouperItemMoved e -> onMoveGrouperItem(e);

                case MediaUpdateEvent.NameEntityCreated e -> onCreateNameEntitySearch(e);
                case MediaUpdateEvent.NameEntityDeleted e -> onDeleteNameEntitySearch(e);
                case MediaUpdateEvent.NameEntityUpdated e -> onUpdateNameEntitySearch(e);
                case MediaUpdateEvent.NameEntityLengthUpdated e -> onUpdateNameEntityLength(e);
                default ->
                    // unknown event type → log and skip
                        System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
            }
            ack.acknowledge();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            // throwing the exception lets DefaultErrorHandler apply retry + DLQ
            throw e;
        }
    }


    // listen to DLQ and print out the event details for now
    @KafkaListener(
            topics = KafkaRedPandaConfig.MEDIA_SEARCH_DLQ_TOPIC,
            groupId = "search-service-dlq-group",
            containerFactory = "dlqListenerContainerFactory"
    )
    public void handleDlq(@Payload MediaUpdateEvent event,
                          Acknowledgment ack,
                          @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) byte[] errorMessage) {
        System.out.println("======= DLQ EVENT DETECTED =======");
        System.out.printf("Error Message: %s\n", errorMessage == null ? "No error message found" : new String(errorMessage));

        // Accessing the POJO data directly
        switch (event) {
            case MediaUpdateEvent.MediaCreatedReadyForSearch e ->
                    System.out.println("Received new index event: " + e.id() + " type: " + e.mediaType());
            case MediaUpdateEvent.FileDeleted e ->
                    System.out.println("Received delete index event: " + e.mediaId());
            case MediaUpdateEvent.MediaThumbnailUpdatedReady e ->
                    System.out.println("Received update media thumbnail event: " + e.mediaId() + " old: " + e.oldThumbnail() + " new: " + e.newThumbnail());
            case MediaUpdateEvent.MediaNameEntityUpdated e ->
                    System.out.println("Received update media name entity event: " + e.mediaId() + " nameEntityConstant: " + e.nameEntityConstant());
            case MediaUpdateEvent.LengthUpdated e ->
                    System.out.println("Received update length event: " + e.mediaIds() + " newLength: " + e.deltaLength());
            case MediaUpdateEvent.MediaTitleUpdated e ->
                    System.out.println("Received update media title event: " + e.mediaId());
            case MediaUpdateEvent.MediaPreviewUpdated e ->
                    System.out.println("Received create media preview event: " + e.mediaId());
            case MediaUpdateEvent.GrouperItemMoved e ->
                    System.out.println("Received move grouper item: " + e.childMediaId() + " parentMediaId: " + e.parentMediaId());

            case MediaUpdateEvent.NameEntityCreated e ->
                    System.out.println("Received create name entity: " + e.nameEntityConstant() + " nameEntityId: " + e.nameEntityId());
            case MediaUpdateEvent.NameEntityDeleted e ->
                    System.out.println("Received delete name entity: " + e.nameEntityConstant() + " nameEntityId: " + e.nameEntityId());
            case MediaUpdateEvent.NameEntityUpdated e ->
                    System.out.println("Received update name entity: " + e.nameEntityConstant() + " nameEntityId: " + e.nameEntityId());
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