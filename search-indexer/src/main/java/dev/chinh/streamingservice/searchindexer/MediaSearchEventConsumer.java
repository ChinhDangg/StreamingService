package dev.chinh.streamingservice.searchindexer;

import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.mediapersistence.projection.MediaGroupInfo;
import dev.chinh.streamingservice.mediapersistence.projection.MediaNameSearchItem;
import dev.chinh.streamingservice.mediapersistence.projection.MediaSearchItem;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.mediapersistence.entity.MediaMetaData;
import dev.chinh.streamingservice.mediapersistence.projection.NameEntityDTO;
import dev.chinh.streamingservice.mediapersistence.repository.*;
import dev.chinh.streamingservice.searchindexer.config.KafkaRedPandaConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MediaSearchEventConsumer {

    private final MediaMetaDataRepository mediaMetaDataRepository;
    private final MediaAuthorRepository mediaAuthorRepository;
    private final MediaCharacterRepository mediaCharacterRepository;
    private final MediaUniverseRepository mediaUniverseRepository;
    private final MediaTagRepository mediaTagRepository;
    private final OpenSearchService openSearchService;
    private final MediaMapper mediaMapper;

    private void onCreateMediaIndexSearch(MediaUpdateEvent.MediaCreatedReady event) {
        System.out.println("Received new index event: " + event.mediaId());
        Optional<MediaMetaData> mediaMetaData = mediaMetaDataRepository.findByIdWithAllInfo(Long.parseLong(event.userId()), event.mediaId());
        if (mediaMetaData.isEmpty()) {
            System.err.println("MediaMetaData not found for mediaId: " + event.mediaId());
            return;
        }
        MediaSearchItem mediaSearchItem = mediaMapper.map(mediaMetaData.get());
        if (event.mediaType() == MediaType.GROUPER) {
            mediaSearchItem.setMediaGroupInfo(new MediaGroupInfo(mediaMetaData.get().getGrouperId(), -1L));
        }
        try {
            openSearchService.indexDocument(OpenSearchService.MEDIA_INDEX_NAME, mediaMetaData.get().getId(), mediaSearchItem);
        } catch (IOException e) {
            throw new RuntimeException("Failed to index media " + mediaMetaData.get().getId() + " to Search", e);
        }
    }

    private void onDeleteMediaIndexSearch(MediaUpdateEvent.FileDeleted event) {
        System.out.println("Received delete index event: " + event.mediaId());
        try {
            openSearchService.deleteDocument(OpenSearchService.MEDIA_INDEX_NAME, event.mediaId());
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete media index " + event.mediaId() + " from Search", e);
        }
    }

    private void onUpdateMediaNameEntitySearch(MediaUpdateEvent.MediaNameEntityUpdated event) {
        System.out.println("Received update media name entity event: " + event.mediaId());
        List<NameEntityDTO> updatedMediaNameEntityList = getMediaNameEntityInfo(
                Long.parseLong(event.userId()), event.mediaId(), event.nameEntityConstant());
        List<MediaNameSearchItem> nameEntityList = updatedMediaNameEntityList.stream()
                .map(n -> new MediaNameSearchItem(n.getId(), n.getName()))
                .toList();
        try {
            openSearchService.partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, event.mediaId(), Map.of(event.nameEntityConstant().getName(), nameEntityList));
        } catch (IOException e) {
            throw new RuntimeException("Failed to update Search index field for media " + event.mediaId(), e);
        }
    }

    private void onUpdateMediaTitleSearch(MediaUpdateEvent.MediaTitleUpdated event) {
        System.out.println("Received update media title event: " + event.mediaId());
        String title = mediaMetaDataRepository.getMediaTitle(Long.parseLong(event.userId()), event.mediaId());
        if (title == null)
            throw new NullPointerException("Title not found for mediaId: " + event.mediaId() + " userId: " + event.userId());
        try {
            openSearchService.partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, event.mediaId(), Map.of(ContentMetaData.TITLE, title));
        } catch (IOException e) {
            throw new RuntimeException("Failed to update Search index title field for media " + event.mediaId(), e);
        }
    }

    private void onUpdateMediaThumbnailSearch(MediaUpdateEvent.MediaThumbnailUpdatedReady event) {
        System.out.println("Received update media thumbnail event: " + event.mediaId());
        try {
            openSearchService.partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, event.mediaId(), Map.of(ContentMetaData.THUMBNAIL, event.newThumbnail()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to update Search index thumbnail field for media " + event.mediaId(), e);
        }
    }

    private void onUpdateLengthSearch(MediaUpdateEvent.LengthUpdated event) {
        System.out.println("Received update length event: " + event.mediaId());
        try {
            openSearchService.partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, event.mediaId(), Map.of(ContentMetaData.LENGTH, event.newLength()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to update Search index length field for media " + event.mediaId(), e);
        }
    }

    private void onUpdateMediaPreview(MediaUpdateEvent.MediaPreviewUpdated event) {
        System.out.println("Received create media preview: " + event.mediaId());
        String previewObject = mediaMetaDataRepository.getMediaPreview(Long.parseLong(event.userId()), event.mediaId());
        if (previewObject == null)
            throw new NullPointerException("Preview not found for mediaId: " + event.mediaId() + " userId: " + event.userId());
        try {
            openSearchService.partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, event.mediaId(), Map.of(ContentMetaData.PREVIEW, previewObject));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create media preview: " + event.mediaId(), e);
        }
    }

    private void onCreateNameEntitySearch(MediaUpdateEvent.NameEntityCreated event) {
        System.out.println("Received create name entity: " + event.nameEntityConstant() + " nameEntityId: " + event.nameEntityId());
        String name = getNameEntityName(Long.parseLong(event.userId()), event.nameEntityConstant(), event.nameEntityId());
        if (name != null) {
            try {
                Map<String, Object> fields = new HashMap<>();
                fields.put(ContentMetaData.NAME, name);
                fields.put(ContentMetaData.USER_ID, Long.parseLong(event.userId()));
                if (event.thumbnailPath() != null) {
                    fields.put(ContentMetaData.THUMBNAIL, event.thumbnailPath());
                }
                openSearchService.indexDocument(event.nameEntityConstant().getName(), event.nameEntityId(), fields);
            } catch (IOException e) {
                throw new RuntimeException("Failed to update Search index field for name entity " + event.nameEntityId(), e);
            }
        }
    }

    private void onUpdateNameEntitySearch(MediaUpdateEvent.NameEntityUpdated event) {
        System.out.println("Received update name entity");
        String name = getNameEntityName(Long.parseLong(event.userId()), event.nameEntityConstant(), event.nameEntityId());
        if (name != null) {
            try {
                openSearchService.partialUpdateDocument(event.nameEntityConstant().getName(), event.nameEntityId(), Map.of(ContentMetaData.NAME, name));
                openSearchService.updateAllNestedFieldNameWithIdInIndex(
                        OpenSearchService.MEDIA_INDEX_NAME,
                        event.nameEntityConstant().getName(),
                        event.nameEntityId(),
                        ContentMetaData.NAME,
                        name
                );
            } catch (IOException e) {
                throw new RuntimeException("Failed to update Search index field for name entity " + event.nameEntityId(), e);
            }
        }
    }

    private void onDeleteNameEntitySearch(MediaUpdateEvent.NameEntityDeleted event) {
        System.out.println("Received delete name entity");
        try {
            openSearchService.deleteDocument(event.nameEntityConstant().getName(), event.nameEntityId());
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete Search index field for name entity " + event.nameEntityConstant().getName() + ", id:" + event.nameEntityId(), e);
        }
    }

    private List<NameEntityDTO> getMediaNameEntityInfo(long userId, long mediaId, MediaNameEntityConstant nameEntity) {
        return switch (nameEntity) {
            case AUTHORS -> mediaMetaDataRepository.findAuthorsByMediaId(userId, mediaId);
            case CHARACTERS -> mediaMetaDataRepository.findCharactersByMediaId(userId, mediaId);
            case UNIVERSES -> mediaMetaDataRepository.findUniversesByMediaId(userId, mediaId);
            case TAGS -> mediaMetaDataRepository.findTagsByMediaId(userId, mediaId);
            default -> throw new IllegalArgumentException("Invalid name entity type: " + nameEntity);
        };
    }

    private String getNameEntityName(long userId, MediaNameEntityConstant nameEntity, long nameEntityId) {
        return switch (nameEntity) {
            case AUTHORS -> mediaAuthorRepository.getNameEntityNameById(userId, nameEntityId);
            case CHARACTERS -> mediaCharacterRepository.getNameEntityNameById(userId, nameEntityId);
            case UNIVERSES -> mediaUniverseRepository.getNameEntityNameById(userId, nameEntityId);
            case TAGS -> mediaTagRepository.getNameEntityNameById(userId, nameEntityId);
        };
    }


    @KafkaListener(topics = {
            EventTopics.MEDIA_SEARCH_TOPIC,
            EventTopics.MEDIA_SEARCH_AND_BACKUP_TOPIC,
            EventTopics.MEDIA_FILE_SEARCH_AND_BACKUP_TOPIC,
            EventTopics.MEDIA_FILE_UPLOAD_SEARCH_AND_BACKUP_TOPIC,
    }, groupId = KafkaRedPandaConfig.MEDIA_GROUP_ID)
    public void handle(@Payload MediaUpdateEvent event, Acknowledgment ack) {
        try {
            switch (event) {
                case MediaUpdateEvent.MediaCreatedReady e -> onCreateMediaIndexSearch(e);
                case MediaUpdateEvent.FileDeleted e -> onDeleteMediaIndexSearch(e);
                case MediaUpdateEvent.MediaThumbnailUpdatedReady e -> onUpdateMediaThumbnailSearch(e);
                case MediaUpdateEvent.MediaNameEntityUpdated e -> onUpdateMediaNameEntitySearch(e);
                case MediaUpdateEvent.LengthUpdated e -> onUpdateLengthSearch(e);
                case MediaUpdateEvent.MediaTitleUpdated e -> onUpdateMediaTitleSearch(e);
                case MediaUpdateEvent.MediaPreviewUpdated e -> onUpdateMediaPreview(e);

                case MediaUpdateEvent.NameEntityCreated e -> onCreateNameEntitySearch(e);
                case MediaUpdateEvent.NameEntityUpdated e -> onUpdateNameEntitySearch(e);
                case MediaUpdateEvent.NameEntityDeleted e -> onDeleteNameEntitySearch(e);

                default ->
                    // unknown event type → log and skip
                        System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
            }
            ack.acknowledge();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            // throwing the exception lets DefaultErrorHandler apply retry + DLQ
            throw e;
        }
    }


    // listen to DLQ and print out the event details for now
    @KafkaListener(
            topics = KafkaRedPandaConfig.MEDIA_SEARCH_DLQ_TOPIC,
            groupId = "media-search-dlq-group",
            containerFactory = "dlqListenerContainerFactory"
    )
    public void handleDlq(@Payload MediaUpdateEvent event,
                          Acknowledgment ack,
                          @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) byte[] errorMessage) {
        System.out.println("======= DLQ EVENT DETECTED =======");
        System.out.printf("Error Message: %s\n", errorMessage == null ? "No error message found" : new String(errorMessage));

        // Accessing the POJO data directly
        switch (event) {
            case MediaUpdateEvent.MediaCreatedReady e ->
                    System.out.println("Received new index event: " + e.mediaId() + " type: " + e.mediaType());
            case MediaUpdateEvent.FileDeleted e ->
                    System.out.println("Received delete index event: " + e.mediaId());
            case MediaUpdateEvent.MediaThumbnailUpdatedReady e ->
                    System.out.println("Received update media thumbnail event: " + e.mediaId() + " old: " + e.oldThumbnail() + " new: " + e.newThumbnail());
            case MediaUpdateEvent.MediaNameEntityUpdated e ->
                    System.out.println("Received update media name entity event: " + e.mediaId() + " nameEntityConstant: " + e.nameEntityConstant());
            case MediaUpdateEvent.LengthUpdated e ->
                    System.out.println("Received update length event: " + e.mediaId() + " newLength: " + e.newLength());
            case MediaUpdateEvent.MediaTitleUpdated e ->
                    System.out.println("Received update media title event: " + e.mediaId());
            case MediaUpdateEvent.MediaPreviewUpdated e ->
                    System.out.println("Received create media preview event: " + e.mediaId());

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