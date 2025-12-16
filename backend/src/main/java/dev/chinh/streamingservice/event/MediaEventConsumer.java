package dev.chinh.streamingservice.event;

import dev.chinh.streamingservice.MediaMapper;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.data.entity.MediaMetaData;
import dev.chinh.streamingservice.data.repository.MediaMetaDataRepository;
import dev.chinh.streamingservice.event.config.KafkaRedPandaConfig;
import dev.chinh.streamingservice.modify.NameEntityDTO;
import dev.chinh.streamingservice.modify.service.MediaMetadataModifyService;
import dev.chinh.streamingservice.search.data.MediaGroupInfo;
import dev.chinh.streamingservice.search.data.MediaSearchItem;
import dev.chinh.streamingservice.search.service.MediaSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MediaEventConsumer {

    private final MediaMetaDataRepository mediaMetaDataRepository;
    private final MediaSearchService mediaSearchService;
    private final MediaMapper mediaMapper;
    private final MediaMetadataModifyService mediaMetadataModifyService;

    private final String MEDIA_GROUP_ID = KafkaRedPandaConfig.MEDIA_GROUP_ID;

    private static final String mediaUpdateOpenSearchTopic = KafkaRedPandaConfig.mediaUpdatedOpenSearchTopic;

    private void onUpdateLengthOpenSearch(MediaUpdateEvent.LengthUpdated event) {
        System.out.println("Received update length event: " + event.mediaId());
        try {
            mediaSearchService.partialUpdateDocument(MediaSearchService.MEDIA_INDEX_NAME, event.mediaId(), Map.of(ContentMetaData.LENGTH, event.newLength()));
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
            mediaSearchItem.setMediaGroupInfo(new MediaGroupInfo(-1L));
        }
        try {
            mediaSearchService.indexDocument(MediaSearchService.MEDIA_INDEX_NAME, mediaMetaData.get().getId(), mediaSearchItem);
        } catch (IOException e) {
            throw new RuntimeException("Failed to index media " + mediaMetaData.get().getId() + " to OpenSearch", e);
        }
    }

    private void onUpdateMediaNameEntityOpenSearch(MediaUpdateEvent.MediaNameEntityUpdated event) {
        System.out.println("Received update media name entity event: " + event.mediaId());
        List<NameEntityDTO> updatedMediaNameEntityList = mediaMetadataModifyService.getMediaNameEntityInfo(
                event.mediaId(), event.nameEntityConstant());
        List<String> nameEntityList = updatedMediaNameEntityList.stream().map(NameEntityDTO::getName).toList();
        try {
            mediaSearchService.partialUpdateDocument(MediaSearchService.MEDIA_INDEX_NAME, event.mediaId(), Map.of(event.nameEntityConstant().getName(), nameEntityList));
        } catch (IOException e) {
            throw new RuntimeException("Failed to update OpenSearch index field for media " + event.mediaId(), e);
        }
    }

    private void onUpdateMediaTitleOpenSearch(MediaUpdateEvent.MediaTitleUpdated event) {
        System.out.println("Received update media title event: " + event.mediaId());
        String title = mediaMetaDataRepository.getMediaTitle(event.mediaId());
        try {
            mediaSearchService.partialUpdateDocument(MediaSearchService.MEDIA_INDEX_NAME, event.mediaId(), Map.of(ContentMetaData.TITLE, title));
        } catch (IOException e) {
            throw new RuntimeException("Failed to update OpenSearch index title field for media " + event.mediaId(), e);
        }
    }

    @KafkaListener(topics = mediaUpdateOpenSearchTopic, groupId = MEDIA_GROUP_ID)
    public void handle(MediaUpdateEvent event) {
        if (event instanceof MediaUpdateEvent.LengthUpdated e) {
            onUpdateLengthOpenSearch(e);
        } else if (event instanceof MediaUpdateEvent.MediaCreated e) {
            onCreateMediaIndexOpenSearch(e);
        } else if (event instanceof MediaUpdateEvent.MediaNameEntityUpdated e) {
            onUpdateMediaNameEntityOpenSearch(e);
        } else if (event instanceof MediaUpdateEvent.MediaTitleUpdated e) {
            onUpdateMediaTitleOpenSearch(e);
        }
    }

}











