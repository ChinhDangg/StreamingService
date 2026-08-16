package dev.chinh.streamingservice.search.event;

import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.mediapersistence.entity.MediaMetaData;
import dev.chinh.streamingservice.mediapersistence.entity.MediaNameEntity;
import dev.chinh.streamingservice.mediapersistence.projection.MediaGroupInfo;
import dev.chinh.streamingservice.mediapersistence.projection.MediaNameSearchItem;
import dev.chinh.streamingservice.mediapersistence.projection.MediaSearchItem;
import dev.chinh.streamingservice.mediapersistence.projection.NameEntityDTO;
import dev.chinh.streamingservice.mediapersistence.repository.*;
import dev.chinh.streamingservice.search.MediaMapper;
import dev.chinh.streamingservice.search.service.OpenSearchService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
@AllArgsConstructor
public class MediaSearchEventService {

    private final MediaMetaDataRepository mediaMetaDataRepository;
    private final MediaAuthorRepository mediaAuthorRepository;
    private final MediaCharacterRepository mediaCharacterRepository;
    private final MediaUniverseRepository mediaUniverseRepository;
    private final MediaTagRepository mediaTagRepository;
    private final OpenSearchService openSearchService;
    private final MediaMapper mediaMapper;

    public void handleCreateMediaIndexSearch(String userId, long mediaId) throws IOException {
        Optional<MediaMetaData> mediaMetaData = mediaMetaDataRepository.findByIdWithAllInfo(Long.parseLong(userId), mediaId);
        if (mediaMetaData.isEmpty()) {
            System.err.println("MediaMetaData not found for mediaId: " + mediaId);
            return;
        }
        MediaSearchItem mediaSearchItem = mediaMapper.map(mediaMetaData.get());
        if (mediaMetaData.get().getMediaType() == MediaType.GROUPER) {
            mediaSearchItem.setMediaGroupInfo(new MediaGroupInfo(mediaMetaData.get().getGrouperId(), -1L));
        }
        openSearchService.indexDocument(OpenSearchService.MEDIA_INDEX_NAME, mediaMetaData.get().getId(), mediaSearchItem);
    }

    public void handleDeleteMediaIndexSearch(long mediaId) throws IOException {
        openSearchService.deleteDocument(OpenSearchService.MEDIA_INDEX_NAME, mediaId);
    }

    public void handleUpdateMediaNameEntitySearch(String userId, long mediaId, MediaNameEntityConstant nameEntityConstant) throws IOException {
        List<NameEntityDTO> updatedMediaNameEntityList = getMediaNameEntityInfo(
                Long.parseLong(userId), mediaId, nameEntityConstant);
        List<MediaNameSearchItem> nameEntityList = updatedMediaNameEntityList.stream()
                .map(n -> new MediaNameSearchItem(n.getId(), n.getName()))
                .toList();
        openSearchService.partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, mediaId, Map.of(nameEntityConstant.getName(), nameEntityList));
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
//        openSearchService.partialUpdateDocument(
//                OpenSearchService.MEDIA_INDEX_NAME, mediaIds, Map.of(ContentMetaData.LENGTH, newLength));
    }

    public void handleUpdateMediaPreview(String userId, long mediaId, String previewObject) throws IOException {
        if (previewObject == null) {
            System.err.println("Preview not found for mediaId: " + mediaId + " userId: " + userId);
            return;
        }
        openSearchService.partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, mediaId, Map.of(ContentMetaData.PREVIEW, previewObject));
    }

    public void handleCreateNameEntitySearch(String userId, MediaNameEntityConstant nameEntityConstant, long nameEntityId) throws IOException {
        MediaNameEntity nameEntity = getMediaNameEntity(Long.parseLong(userId), nameEntityConstant, nameEntityId);
        if (nameEntity == null) {
            System.err.println("Name Entity not found with id: " + nameEntityId + " for nameEntityConstant: " + nameEntityConstant.getName());
            return;
        }
        openSearchService.indexDocument(nameEntityConstant.getName(), nameEntityId, nameEntity);
    }

    public void handleDeleteNameEntitySearch(long nameEntityId, MediaNameEntityConstant nameEntityConstant) throws IOException {
        openSearchService.deleteDocument(nameEntityConstant.getName(), nameEntityId);
    }

    public void handleUpdateNameEntitySearch(String userId, MediaNameEntityConstant nameEntityConstant, long nameEntityId,
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


    private List<NameEntityDTO> getMediaNameEntityInfo(long userId, long mediaId, MediaNameEntityConstant nameEntity) {
        return switch (nameEntity) {
            case AUTHORS -> mediaMetaDataRepository.findAuthorsByMediaId(userId, mediaId);
            case CHARACTERS -> mediaMetaDataRepository.findCharactersByMediaId(userId, mediaId);
            case UNIVERSES -> mediaMetaDataRepository.findUniversesByMediaId(userId, mediaId);
            case TAGS -> mediaMetaDataRepository.findTagsByMediaId(userId, mediaId);
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

    private MediaNameEntity getMediaNameEntity(long userId, MediaNameEntityConstant nameEntity, long nameEntityId) {
        var result =  switch (nameEntity) {
            case AUTHORS -> mediaAuthorRepository.findByIdAndUserId(nameEntityId, userId);
            case CHARACTERS -> mediaCharacterRepository.findByIdAndUserId(nameEntityId, userId);
            case UNIVERSES -> mediaUniverseRepository.findByIdAndUserId(nameEntityId, userId);
            case TAGS -> mediaTagRepository.findByIdAndUserId(nameEntityId, userId);
        };
        return result.orElse(null);
    }
}
