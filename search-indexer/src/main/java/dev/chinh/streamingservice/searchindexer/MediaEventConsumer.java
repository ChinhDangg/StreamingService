package dev.chinh.streamingservice.searchindexer;

import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.common.data.ContentMetaData;
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
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MediaEventConsumer {

    private final MediaMetaDataRepository mediaMetaDataRepository;
    private final MediaAuthorRepository mediaAuthorRepository;
    private final MediaCharacterRepository mediaCharacterRepository;
    private final MediaUniverseRepository mediaUniverseRepository;
    private final MediaTagRepository mediaTagRepository;
    private final OpenSearchService openSearchService;
    private final MediaMapper mediaMapper;

    private void onUpdateLengthOpenSearch(MediaUpdateEvent.LengthUpdated event) {
        System.out.println("Received update length event: " + event.mediaId());
        try {
            openSearchService.partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, event.mediaId(), Map.of(ContentMetaData.LENGTH, event.newLength()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to update OpenSearch index length field for media " + event.mediaId(), e);
        }
    }

    private void onCreateMediaIndexOpenSearch(MediaUpdateEvent.MediaCreated event) {
        System.out.println("Received new index event: " + event.mediaId());
        Optional<MediaMetaData> mediaMetaData = mediaMetaDataRepository.findByIdWithAllInfo(event.mediaId());
        if (mediaMetaData.isEmpty()) {
            return;
        }
        MediaSearchItem mediaSearchItem = mediaMapper.map(mediaMetaData.get());
        if (event.isGrouper()) {
            mediaSearchItem.setMediaGroupInfo(new MediaGroupInfo(mediaMetaData.get().getGrouperId(), -1L));
        }
        try {
            openSearchService.indexDocument(OpenSearchService.MEDIA_INDEX_NAME, mediaMetaData.get().getId(), mediaSearchItem);
        } catch (IOException e) {
            throw new RuntimeException("Failed to index media " + mediaMetaData.get().getId() + " to OpenSearch", e);
        }
    }

    private void onUpdateMediaNameEntityOpenSearch(MediaUpdateEvent.MediaNameEntityUpdated event) {
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

    private void onUpdateMediaTitleOpenSearch(MediaUpdateEvent.MediaTitleUpdated event) {
        System.out.println("Received update media title event: " + event.mediaId());
        String title = mediaMetaDataRepository.getMediaTitle(event.mediaId());
        try {
            openSearchService.partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, event.mediaId(), Map.of(ContentMetaData.TITLE, title));
        } catch (IOException e) {
            throw new RuntimeException("Failed to update OpenSearch index title field for media " + event.mediaId(), e);
        }
    }

    private void onCreateNameEntityOpenSearch(MediaUpdateEvent.NameEntityCreated event) {
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

    private void onUpdateNameEntityOpenSearch(MediaUpdateEvent.NameEntityUpdated event) {
        System.out.println("Received update name entity");
        String name = getNameEntityName(event.nameEntityConstant(), event.nameEntityId());
        if (name != null) {
            try {
                openSearchService.partialUpdateDocument(event.nameEntityConstant().getName(), event.nameEntityId(), Map.of(ContentMetaData.NAME, name));
            } catch (IOException e) {
                throw new RuntimeException("Failed to update OpenSearch index field for name entity " + event.nameEntityId(), e);
            }
        }
    }

    private void onDeleteNameEntityOpenSearch(MediaUpdateEvent.NameEntityDeleted event) {
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

    @KafkaListener(topics = KafkaRedPandaConfig.MEDIA_UPDATED_OPENSEARCH_TOPIC, groupId = KafkaRedPandaConfig.MEDIA_GROUP_ID)
    public void handle(MediaUpdateEvent event, Acknowledgment acknowledgment) {
        try {
            switch (event) {
                case MediaUpdateEvent.LengthUpdated e -> onUpdateLengthOpenSearch(e);
                case MediaUpdateEvent.MediaCreated e -> onCreateMediaIndexOpenSearch(e);
                case MediaUpdateEvent.MediaNameEntityUpdated e -> onUpdateMediaNameEntityOpenSearch(e);
                case MediaUpdateEvent.MediaTitleUpdated e -> onUpdateMediaTitleOpenSearch(e);
                case MediaUpdateEvent.NameEntityCreated e -> onCreateNameEntityOpenSearch(e);
                case MediaUpdateEvent.NameEntityUpdated e -> onUpdateNameEntityOpenSearch(e);
                case MediaUpdateEvent.NameEntityDeleted e -> onDeleteNameEntityOpenSearch(e);
                default ->
                    // unknown event type → log and skip
                        System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            // throwing the exception lets DefaultErrorHandler apply retry + DLQ
            throw e;
        }
    }

    // listen to DLQ and print out the event details for now - later log to a file or database or consume it
    @KafkaListener(topics = KafkaRedPandaConfig.MEDIA_UPDATE_DLQ_TOPIC, groupId = "media-dlq-group")
    public void handleDlq(MediaUpdateEvent event,
                          Acknowledgment ack,
                          @Header(name = "x-exception-message", required = false) String errorMessage) {
        System.out.println("======= DLQ EVENT DETECTED =======");
        System.out.printf("Error Message: %s\n", errorMessage);

        // Accessing the POJO data directly
        switch (event) {
            case MediaUpdateEvent.LengthUpdated(long mediaId, Integer newLength) ->
                    System.out.println("Received update length event: " + mediaId + " newLength: " + newLength);
            case MediaUpdateEvent.MediaCreated(long mediaId, boolean isGrouper) ->
                    System.out.println("Received new index event: " + mediaId + " isGrouper: " + isGrouper);
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
            default -> System.err.println("Unknown MediaUpdateEvent type: " + event.getClass());
        }

        // ack or it will be re-read from the DLQ on restart or rehandle it manually.
        //ack.acknowledge();
    }


}











