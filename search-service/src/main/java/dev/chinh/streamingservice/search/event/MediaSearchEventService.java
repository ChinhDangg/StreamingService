package dev.chinh.streamingservice.search.event;

import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.search.MediaMapper;
import dev.chinh.streamingservice.search.data.NameEntityField;
import dev.chinh.streamingservice.search.persistence.*;
import dev.chinh.streamingservice.search.service.OpenSearchSearchService;
import dev.chinh.streamingservice.search.service.OpenSearchService;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
@AllArgsConstructor
public class MediaSearchEventService {

    private final GrouperMetadataRepository grouperMetadataRepository;

    private final OpenSearchService openSearchService;
    private final OpenSearchSearchService searchService;
    private final MediaMapper mediaMapper;

    private final EntityManager entityManager;

    @Transactional
    public void handleCreateMediaIndexSearch(MediaUpdateEvent.MediaCreatedReadyForSearch event) throws IOException {
        if (event.groupInfoId() != null && (event.groupInfoGrouperId() == null || event.groupInfoGrouperId().equals(event.groupInfoId()))) { // is grouper
            GrouperMediaMetadata grouperMediaMetadata = mediaMapper.mapToGrouperMediaMetadata(event);
            grouperMediaMetadata.setNew(true);
            MediaGroupInfo grouperGroupInfo = new MediaGroupInfo(
                    event.groupInfoId(), grouperMediaMetadata, null, null
            );
            grouperMediaMetadata.setGroupInfo(grouperGroupInfo);
            grouperMetadataRepository.save(grouperMediaMetadata);
        } else if (event.groupInfoId() != null) {
            GrouperMediaMetadata childInGrouperItem = mediaMapper.mapToGrouperMediaMetadata(event);
            childInGrouperItem.setNew(true);
            MediaGroupInfo childGroupInfo = new MediaGroupInfo(
                    event.groupInfoId(), childInGrouperItem, null, event.groupInfoNumInfo()
            );
            childGroupInfo.setGrouperInfo(entityManager.getReference(MediaGroupInfo.class, event.groupInfoGrouperId()));
            childInGrouperItem.setGroupInfo(childGroupInfo);
            grouperMetadataRepository.save(childInGrouperItem);
            return;
        }

        MediaSearchItem item = mediaMapper.map(event);
        openSearchService.indexDocument(OpenSearchService.MEDIA_INDEX_NAME, item.getId(), item);
    }

    public void handleDeleteMediaIndexSearch(long mediaId, MediaType mediaType) throws IOException {
        if (mediaType == MediaType.GROUPER || mediaType == MediaType.ALBUM) {
            grouperMetadataRepository.deleteById(mediaId);
        }
        openSearchService.deleteDocument(OpenSearchService.MEDIA_INDEX_NAME, mediaId);
    }

    public void handleUpdateMediaNameEntitySearch(long mediaId, MediaNameEntityConstant nameEntityConstant, Map<Long, String> nameEntityIdsToNames) throws IOException {
        openSearchService.partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, mediaId, Map.of(nameEntityConstant.getName(), nameEntityIdsToNames));
    }

    public void handleUpdateMediaTitleSearch(String userId, long mediaId, String title) throws IOException {
        if (title == null)
            throw new NullPointerException("Title not found for mediaId: " + mediaId + " userId: " + userId);
        openSearchService.partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, mediaId, Map.of(ContentMetaData.TITLE, title));
    }

    public void handleUpdateMediaThumbnailSearch(long mediaId, String newThumbnail) throws IOException {
        openSearchService.partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, mediaId, Map.of(ContentMetaData.THUMBNAIL, newThumbnail));
    }

    public void handleUpdateMediaLengthSearch(List<Long> mediaIds, int delta, long version) throws IOException {
        openSearchService.updateNumericFieldWithDeltaByIds(
                OpenSearchService.MEDIA_INDEX_NAME,
                mediaIds,
                ContentMetaData.LENGTH,
                delta,
                String.valueOf(version)
        );
    }

    public void handleUpdateMediaPreview(String userId, long mediaId, String previewObject) throws IOException {
        if (previewObject == null) {
            System.err.println("Preview not found for mediaId: " + mediaId + " userId: " + userId);
            return;
        }
        openSearchService.partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, mediaId, Map.of(ContentMetaData.PREVIEW, previewObject));
    }

    @Transactional
    public void handleMoveGrouperItem(MediaUpdateEvent.GrouperItemMoved event) throws IOException {
        if (event.parentMediaId() != null) {
            GrouperMediaMetadata grouperMediaMetadata = null;

            if (event.oldParentIsGrouper()) {
                grouperMediaMetadata = entityManager.getReference(GrouperMediaMetadata.class, event.childMediaId());
            } else {
                SearchResponse<MediaSearchItem> searchItemResponse = searchService.findById(OpenSearchService.MEDIA_INDEX_NAME, Long.parseLong(event.userId()), event.childMediaId(), MediaSearchItem.class);
                MediaSearchItem searchItem = searchItemResponse.hits().hits().getFirst().source();
                if (searchItem != null) {
                    grouperMediaMetadata = mediaMapper.mapToGrouperMediaMetadata(searchItem);
                    grouperMediaMetadata.setNew(true);
                }
            }

            if (grouperMediaMetadata == null)
                throw new NullPointerException("Grouper media metadata not found for mediaId: " + event.childMediaId());

            MediaGroupInfo mediaGroupInfo = new MediaGroupInfo(
                    event.newGroupInfoId(),
                    grouperMediaMetadata,
                    entityManager.getReference(MediaGroupInfo.class, event.parentMediaId()),
                    event.fileName()
            );
            grouperMediaMetadata.setGroupInfo(mediaGroupInfo);
            grouperMetadataRepository.save(grouperMediaMetadata);
            System.out.println("Saved grouper media metadata: " + grouperMediaMetadata.getId());
        } else {
            grouperMetadataRepository.deleteById(event.childMediaId());
        }
        openSearchService.deleteDocument(OpenSearchService.MEDIA_INDEX_NAME, event.childMediaId());
    }


    public void handleCreateNameEntitySearch(MediaNameEntityConstant nameEntityConstant, long nameEntityId, NameEntityField nameEntityField) throws IOException {
        openSearchService.indexDocument(nameEntityConstant.getName(), nameEntityId, nameEntityField);
    }

    public void handleDeleteNameEntitySearch(long nameEntityId, MediaNameEntityConstant nameEntityConstant) throws IOException {
        openSearchService.deleteDocument(nameEntityConstant.getName(), nameEntityId);
    }

    public void handleUpdateNameEntitySearch(MediaNameEntityConstant nameEntityConstant, long nameEntityId,
                                             String newName,
                                             String oldThumbnail, String newThumbnail) throws IOException {
        Map<String, Object> fields = new HashMap<>();
        if (newName != null)
            fields.put(ContentMetaData.NAME, newName);
        if (oldThumbnail != null && newThumbnail != null && !oldThumbnail.equals(newThumbnail))
            fields.put(ContentMetaData.THUMBNAIL, newThumbnail);
        if (!fields.isEmpty())
            openSearchService.partialUpdateDocument(nameEntityConstant.getName(), nameEntityId, fields);
        if (newName != null)
            openSearchService.updateAllNestedFieldNameWithIdInIndex(
                    OpenSearchService.MEDIA_INDEX_NAME,
                    nameEntityConstant.getName(),
                    nameEntityId,
                    ContentMetaData.NAME,
                    newName
            );
    }

    public void handleUpdateNameEntityLength(MediaNameEntityConstant nameEntityConstant, Long[] nameEntityIds, int deltaLength) throws IOException {
        openSearchService.updateNumericFieldWithDeltaByIds(
                nameEntityConstant.getName(),
                List.of(nameEntityIds),
                ContentMetaData.LENGTH,
                deltaLength,
                UUID.randomUUID().toString()
        );
    }
}
