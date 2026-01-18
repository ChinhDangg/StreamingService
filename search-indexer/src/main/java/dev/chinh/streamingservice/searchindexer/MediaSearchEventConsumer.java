package dev.chinh.streamingservice.searchindexer;

import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.persistence.projection.MediaGroupInfo;
import dev.chinh.streamingservice.persistence.projection.MediaSearchItem;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.persistence.entity.MediaMetaData;
import dev.chinh.streamingservice.persistence.projection.NameEntityDTO;
import dev.chinh.streamingservice.persistence.repository.*;
import dev.chinh.streamingservice.searchindexer.config.KafkaRedPandaConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;
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

    private void onUpdateLengthSearch(MediaUpdateEvent.LengthUpdated event) {
        System.out.println("Received update length event: " + event.mediaId());
        try {
            openSearchService.partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, event.mediaId(), Map.of(ContentMetaData.LENGTH, event.newLength()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to update OpenSearch index length field for media " + event.mediaId(), e);
        }
    }

    private void onCreateMediaIndexSearch(MediaUpdateEvent.MediaCreated event) {
        System.out.println("Received new index event: " + event.mediaId());
        Optional<MediaMetaData> mediaMetaData = mediaMetaDataRepository.findByIdWithAllInfo(event.mediaId());
        if (mediaMetaData.isEmpty()) {
            return;
        }
        MediaSearchItem mediaSearchItem = mediaMapper.map(mediaMetaData.get());
        if (event.mediaType() == MediaType.GROUPER) {
            mediaSearchItem.setMediaGroupInfo(new MediaGroupInfo(mediaMetaData.get().getGrouperId(), -1L));
        }
        try {
            openSearchService.indexDocument(OpenSearchService.MEDIA_INDEX_NAME, mediaMetaData.get().getId(), mediaSearchItem);
        } catch (IOException e) {
            throw new RuntimeException("Failed to index media " + mediaMetaData.get().getId() + " to OpenSearch", e);
        }
    }

    private void onDeleteMediaIndexSearch(MediaUpdateEvent.MediaDeleted event) {
        System.out.println("Received delete index event: " + event.mediaId());
        try {
            openSearchService.deleteDocument(OpenSearchService.MEDIA_INDEX_NAME, event.mediaId());
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete media " + event.mediaId() + " from OpenSearch", e);
        }
    }

    private void onUpdateMediaNameEntitySearch(MediaUpdateEvent.MediaNameEntityUpdated event) {
        System.out.println("Received update media name entity event: " + event.mediaId());
        List<NameEntityDTO> updatedMediaNameEntityList = getMediaNameEntityInfo(
                event.mediaId(), event.nameEntityConstant());
        List<String> nameEntityList = updatedMediaNameEntityList.stream().map(NameEntityDTO::getName).toList();
        try {
            openSearchService.partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, event.mediaId(), Map.of(event.nameEntityConstant().getName(), nameEntityList));
        } catch (IOException e) {
            throw new RuntimeException("Failed to update OpenSearch index field for media " + event.mediaId(), e);
        }
    }

    private void onUpdateMediaTitleSearch(MediaUpdateEvent.MediaTitleUpdated event) {
        System.out.println("Received update media title event: " + event.mediaId());
        String title = mediaMetaDataRepository.getMediaTitle(event.mediaId());
        try {
            openSearchService.partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, event.mediaId(), Map.of(ContentMetaData.TITLE, title));
        } catch (IOException e) {
            throw new RuntimeException("Failed to update OpenSearch index title field for media " + event.mediaId(), e);
        }
    }

    private void onUpdateMediaThumbnailSearch(MediaUpdateEvent.MediaThumbnailUpdated event) {
        System.out.println("Received update media thumbnail event: " + event.mediaId());
        try {
            openSearchService.partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, event.mediaId(), Map.of(ContentMetaData.THUMBNAIL, event.thumbnailObject()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to update OpenSearch index thumbnail field for media " + event.mediaId(), e);
        }
    }


    private void onCreateNameEntitySearch(MediaUpdateEvent.NameEntityCreated event) {
        System.out.println("Received create name entity");
        String name = getNameEntityName(event.nameEntityConstant(), event.nameEntityId());
        if (name != null) {
            try {
                openSearchService.indexDocument(event.nameEntityConstant().getName(), event.nameEntityId(), Map.of(ContentMetaData.NAME, name));
            } catch (IOException e) {
                throw new RuntimeException("Failed to update OpenSearch index field for name entity " + event.nameEntityId(), e);
            }
        }
    }

    private void onUpdateNameEntitySearch(MediaUpdateEvent.NameEntityUpdated event) {
        System.out.println("Received update name entity");
        String name = getNameEntityName(event.nameEntityConstant(), event.nameEntityId());
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
                throw new RuntimeException("Failed to update OpenSearch index field for name entity " + event.nameEntityId(), e);
            }
        }
    }

    private void onDeleteNameEntitySearch(MediaUpdateEvent.NameEntityDeleted event) {
        System.out.println("Received delete name entity");
        try {
            openSearchService.deleteDocument(event.nameEntityConstant().getName(), event.nameEntityId());
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete OpenSearch index field for name entity " + event.nameEntityId(), e);
        }
    }

    private List<NameEntityDTO> getMediaNameEntityInfo(long mediaId, MediaNameEntityConstant nameEntity) {
        return switch (nameEntity.getName()) {
            case ContentMetaData.AUTHORS -> mediaMetaDataRepository.findAuthorsByMediaId(mediaId);
            case ContentMetaData.CHARACTERS -> mediaMetaDataRepository.findCharactersByMediaId(mediaId);
            case ContentMetaData.UNIVERSES -> mediaMetaDataRepository.findUniversesByMediaId(mediaId);
            case ContentMetaData.TAGS -> mediaMetaDataRepository.findTagsByMediaId(mediaId);
            default -> throw new IllegalArgumentException("Invalid name entity type: " + nameEntity);
        };
    }

    private String getNameEntityName(MediaNameEntityConstant nameEntity, long nameEntityId) {
        return switch (nameEntity) {
            case AUTHORS -> mediaAuthorRepository.getNameEntityName(nameEntityId);
            case CHARACTERS -> mediaCharacterRepository.getNameEntityName(nameEntityId);
            case UNIVERSES -> mediaUniverseRepository.getNameEntityName(nameEntityId);
            case TAGS -> mediaTagRepository.getNameEntityName(nameEntityId);
        };
    }


    @KafkaListener(topics = {
            EventTopics.MEDIA_SEARCH_TOPIC,
            EventTopics.MEDIA_CREATED_TOPIC,
            EventTopics.MEDIA_DELETED_TOPIC,
            EventTopics.MEDIA_THUMBNAIL_UPDATED_TOPIC,
    }, groupId = KafkaRedPandaConfig.MEDIA_GROUP_ID)
    public void handle(@Payload MediaUpdateEvent event, Acknowledgment ack) {
        try {
            switch (event) {
                case MediaUpdateEvent.LengthUpdated e -> onUpdateLengthSearch(e);
                case MediaUpdateEvent.MediaNameEntityUpdated e -> onUpdateMediaNameEntitySearch(e);
                case MediaUpdateEvent.MediaTitleUpdated e -> onUpdateMediaTitleSearch(e);
                case MediaUpdateEvent.NameEntityCreated e -> onCreateNameEntitySearch(e);
                case MediaUpdateEvent.NameEntityUpdated e -> onUpdateNameEntitySearch(e);
                case MediaUpdateEvent.NameEntityDeleted e -> onDeleteNameEntitySearch(e);
                case MediaUpdateEvent.MediaCreated e -> onCreateMediaIndexSearch(e);
                case MediaUpdateEvent.MediaDeleted e -> onDeleteMediaIndexSearch(e);
                case MediaUpdateEvent.MediaThumbnailUpdated e -> onUpdateMediaThumbnailSearch(e);
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
                          @Header(name = "x-exception-message", required = false) String errorMessage) {
        System.out.println("======= DLQ EVENT DETECTED =======");
        System.out.printf("Error Message: %s\n", errorMessage);

        // Accessing the POJO data directly
        switch (event) {
            case MediaUpdateEvent.LengthUpdated(long mediaId, Integer newLength) ->
                    System.out.println("Received update length event: " + mediaId + " newLength: " + newLength);
            case MediaUpdateEvent.MediaCreated e ->
                    System.out.println("Received new index event: " + e.mediaId() + " type: " + e.mediaType());
            case MediaUpdateEvent.MediaNameEntityUpdated(long mediaId, MediaNameEntityConstant nameEntityConstant) ->
                    System.out.println("Received update media name entity event: " + mediaId + " nameEntityConstant: " + nameEntityConstant);
            case MediaUpdateEvent.MediaTitleUpdated(long mediaId) ->
                    System.out.println("Received update media title event: " + mediaId);
            case MediaUpdateEvent.NameEntityCreated(MediaNameEntityConstant nameEntityConstant, long nameEntityId) ->
                    System.out.println("Received create name entity: " + nameEntityConstant + " nameEntityId: " + nameEntityId);
            case MediaUpdateEvent.NameEntityUpdated(MediaNameEntityConstant nameEntityConstant, long nameEntityId) ->
                    System.out.println("Received update name entity: " + nameEntityConstant + " nameEntityId: " + nameEntityId);
            case MediaUpdateEvent.NameEntityDeleted(MediaNameEntityConstant nameEntityConstant, long nameEntityId) ->
                    System.out.println("Received delete name entity: " + nameEntityConstant + " nameEntityId: " + nameEntityId);
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