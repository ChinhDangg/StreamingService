package dev.chinh.streamingservice.modify.service;

import dev.chinh.streamingservice.data.ContentMetaData;
import dev.chinh.streamingservice.data.repository.MediaMetaDataRepository;
import dev.chinh.streamingservice.data.service.MediaMetadataService;
import dev.chinh.streamingservice.search.service.OpenSearchService;
import dev.chinh.streamingservice.modify.MediaNameEntityConstant;
import dev.chinh.streamingservice.modify.NameEntityDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MediaMetadataModifyService {

    private final MediaMetaDataRepository mediaMetaDataRepository;
    private final MediaMetadataService mediaMetadataService;
    private final OpenSearchService openSearchService;

    public List<NameEntityDTO> getMediaNameEntityInfo(long mediaId, MediaNameEntityConstant nameEntity) {
        return switch (nameEntity.getName()) {
            case ContentMetaData.AUTHORS -> mediaMetaDataRepository.findAuthorsByMediaId(mediaId);
            case ContentMetaData.CHARACTERS -> mediaMetaDataRepository.findCharactersByMediaId(mediaId);
            case ContentMetaData.UNIVERSES -> mediaMetaDataRepository.findUniversesByMediaId(mediaId);
            case ContentMetaData.TAGS -> mediaMetaDataRepository.findTagsByMediaId(mediaId);
            default -> throw new IllegalArgumentException("Invalid name entity type: " + nameEntity);
        };
    }

    @Transactional
    public String updateMediaTitle(long mediaId, String newTitle) {
        if (newTitle == null) {
            throw new IllegalArgumentException("Title must not be null");
        }
        newTitle = newTitle.trim();
        if (newTitle.isEmpty())
            throw new IllegalArgumentException("Name must not be empty");
        if (newTitle.length() < 5)
            throw new IllegalArgumentException("Name must be at least 5 chars: " + newTitle);
        if (newTitle.length() > 300)
            throw new IllegalArgumentException("Name must be at most 300 chars");

        mediaMetaDataRepository.updateMediaTitle(mediaId, newTitle);
        try {
            openSearchService.partialUpdateDocument(OpenSearchService.INDEX_NAME, mediaId, Map.of(ContentMetaData.TITLE, newTitle));
        } catch (IOException e) {
            throw new RuntimeException("Failed to update OpenSearch index title field for media " + mediaId, e);
        }
        mediaMetadataService.removeCachedMediaSearchItem(mediaId);
        return newTitle;
    }

    public record UpdateList(
            List<NameEntityDTO> adding, List<NameEntityDTO> removing, MediaNameEntityConstant nameEntity
    ) {}

    @Transactional
    public List<String> updateNameEntityInMedia(UpdateList updateList, long mediaId) {
        if (updateList.adding.isEmpty() && updateList.removing.isEmpty()) return new ArrayList<>();
        for (NameEntityDTO nameEntityDTO : updateList.removing) {
            removeNameEntityInMedia(mediaId, nameEntityDTO.getId(), updateList.nameEntity);
        }
        for (NameEntityDTO nameEntityDTO : updateList.adding) {
            addNameEntityInMedia(mediaId, nameEntityDTO.getId(), updateList.nameEntity);
        }
        List<NameEntityDTO> updatedMediaNameEntityList = getMediaNameEntityInfo(mediaId, updateList.nameEntity);
        List<String> nameEntityList = updatedMediaNameEntityList.stream().map(NameEntityDTO::getName).toList();
        try {
            openSearchService.partialUpdateDocument(OpenSearchService.INDEX_NAME, mediaId, Map.of(updateList.nameEntity.getName(), nameEntityList));
        } catch (IOException e) {
            throw new RuntimeException("Failed to update OpenSearch index field for media " + mediaId, e);
        }
        mediaMetadataService.removeCachedMediaSearchItem(mediaId);
        return nameEntityList;
    }

    private void removeNameEntityInMedia(long mediaId, long nameEntityId, MediaNameEntityConstant nameEntity) {
        switch (nameEntity.getName()) {
            case ContentMetaData.AUTHORS -> mediaMetaDataRepository.deleteAuthorFromMedia(mediaId, nameEntityId);
            case ContentMetaData.CHARACTERS -> mediaMetaDataRepository.deleteCharacterFromMedia(mediaId, nameEntityId);
            case ContentMetaData.UNIVERSES -> mediaMetaDataRepository.deleteUniverseFromMedia(mediaId, nameEntityId);
            case ContentMetaData.TAGS -> mediaMetaDataRepository.deleteTagFromMedia(mediaId, nameEntityId);
            default -> throw new IllegalArgumentException("Invalid name entity type: " + nameEntity);
        }
    }

    private void addNameEntityInMedia(long mediaId, long nameEntityId, MediaNameEntityConstant nameEntity) {
        switch (nameEntity.getName()) {
            case ContentMetaData.AUTHORS -> mediaMetaDataRepository.addAuthorToMedia(mediaId, nameEntityId);
            case ContentMetaData.CHARACTERS -> mediaMetaDataRepository.addCharacterToMedia(mediaId, nameEntityId);
            case ContentMetaData.UNIVERSES -> mediaMetaDataRepository.addUniverseToMedia(mediaId, nameEntityId);
            case ContentMetaData.TAGS -> mediaMetaDataRepository.addTagToMedia(mediaId, nameEntityId);
            default -> throw new IllegalArgumentException("Invalid name entity type: " + nameEntity);
        }
    }
}
