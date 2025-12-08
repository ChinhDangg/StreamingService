package dev.chinh.streamingservice.event;

import dev.chinh.streamingservice.MediaMapper;
import dev.chinh.streamingservice.data.ContentMetaData;
import dev.chinh.streamingservice.data.entity.MediaMetaData;
import dev.chinh.streamingservice.data.repository.MediaMetaDataRepository;
import dev.chinh.streamingservice.modify.NameEntityDTO;
import dev.chinh.streamingservice.modify.service.MediaMetadataModifyService;
import dev.chinh.streamingservice.search.data.MediaGroupInfo;
import dev.chinh.streamingservice.search.data.MediaSearchItem;
import dev.chinh.streamingservice.search.service.OpenSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MediaEventConsumer {

    private final MediaMetaDataRepository mediaMetaDataRepository;
    private final OpenSearchService openSearchService;
    private final MediaMapper mediaMapper;
    private final MediaMetadataModifyService mediaMetadataModifyService;

    private final String media_group_id = "media-service";

    private MediaMetaData findMediaById(Long mediaId) {
        return mediaMetaDataRepository.findById(mediaId).orElseThrow(
                () -> new IllegalArgumentException("Media not found with id " + mediaId)
        );
    }

    @KafkaListener(topics = MediaEventProducer.updateLengthOpenSearchTopic, groupId = media_group_id)
    public void onUpdateLengthOpenSearch(MediaUpdateEvent.LengthUpdated event) {
        System.out.println("Received update length event: " + event.mediaId());
        try {
            openSearchService.partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, event.mediaId(), Map.of(ContentMetaData.LENGTH, event.newLength()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to update OpenSearch index length field for media " + event.mediaId(), e);
        }
    }

    @KafkaListener(topics = MediaEventProducer.createMediaOpenSearchTopic, groupId = media_group_id)
    public void onCreateMediaIndexOpenSearch(MediaUpdateEvent.MediaCreated event) {
        System.out.println("Received new index event: " + event.mediaId());
        MediaMetaData mediaMetaData = findMediaById(event.mediaId());
        MediaSearchItem mediaSearchItem = mediaMapper.map(mediaMetaData);
        if (event.isGrouper()) {
            mediaSearchItem.setMediaGroupInfo(new MediaGroupInfo(-1L));
        }
        try {
            openSearchService.indexDocument(OpenSearchService.MEDIA_INDEX_NAME, mediaMetaData.getId(), mediaSearchItem);
        } catch (IOException e) {
            throw new RuntimeException("Failed to index media " + mediaMetaData.getId() + " to OpenSearch", e);
        }
    }

    @KafkaListener(topics = MediaEventProducer.updateMediaNameEntityOpenSearchTopic, groupId = media_group_id)
    public void onUpdateMediaNameEntityOpenSearch(MediaUpdateEvent.MediaNameEntityUpdated event) {
        System.out.println("Received update media name entity event: " + event.mediaId());
        List<NameEntityDTO> updatedMediaNameEntityList = mediaMetadataModifyService.getMediaNameEntityInfo(
                event.mediaId(), event.nameEntityConstant());
        List<String> nameEntityList = updatedMediaNameEntityList.stream().map(NameEntityDTO::getName).toList();
        try {
            openSearchService.partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, event.mediaId(), Map.of(event.nameEntityConstant().getName(), nameEntityList));
        } catch (IOException e) {
            throw new RuntimeException("Failed to update OpenSearch index field for media " + event.mediaId(), e);
        }
    }

    @KafkaListener(topics = MediaEventProducer.updateMediaTitleOpenSearchTopic, groupId = media_group_id)
    public void onUpdateMediaTitleOpenSearch(MediaUpdateEvent.MediaTitleUpdated event) {
        System.out.println("Received update media title event: " + event.mediaId());
        String title = mediaMetaDataRepository.getMediaTitle(event.mediaId());
        try {
            openSearchService.partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, event.mediaId(), Map.of(ContentMetaData.TITLE, title));
        } catch (IOException e) {
            throw new RuntimeException("Failed to update OpenSearch index title field for media " + event.mediaId(), e);
        }
    }
}











