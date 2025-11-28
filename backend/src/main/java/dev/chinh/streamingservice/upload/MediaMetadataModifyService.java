package dev.chinh.streamingservice.upload;

import dev.chinh.streamingservice.data.ContentMetaData;
import dev.chinh.streamingservice.data.repository.MediaMetaDataRepository;
import dev.chinh.streamingservice.data.service.MediaMetadataService;
import dev.chinh.streamingservice.search.service.OpenSearchService;
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

    public List<NameEntityDTO> getMediaNameEntityInfo(long mediaId, MediaNameEntity nameEntity) {
        return switch (nameEntity.getName()) {
            case ContentMetaData.AUTHORS -> mediaMetaDataRepository.findAuthorsByMediaId(mediaId);
            case ContentMetaData.CHARACTERS -> mediaMetaDataRepository.findCharactersByMediaId(mediaId);
            case ContentMetaData.UNIVERSES -> mediaMetaDataRepository.findUniversesByMediaId(mediaId);
            case ContentMetaData.TAGS -> mediaMetaDataRepository.findTagsByMediaId(mediaId);
            default -> throw new IllegalArgumentException("Invalid name entity type: " + nameEntity);
        };
    }

    public record UpdateList(
            List<NameEntityDTO> adding, List<NameEntityDTO> removing, MediaNameEntity nameEntity
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
            openSearchService.partialUpdateDocument(OpenSearchService.INDEX_NAME, mediaId, Map.of(ContentMetaData.NAME, nameEntityList));
        } catch (IOException e) {
            throw new RuntimeException("Failed to update OpenSearch index field for media " + mediaId, e);
        }
        mediaMetadataService.removeCachedMediaSearchItem(String.valueOf(mediaId));
        return nameEntityList;
    }

    private void removeNameEntityInMedia(long mediaId, long nameEntityId, MediaNameEntity nameEntity) {
        switch (nameEntity.getName()) {
            case ContentMetaData.AUTHORS -> mediaMetaDataRepository.deleteAuthorFromMedia(mediaId, nameEntityId);
            case ContentMetaData.CHARACTERS -> mediaMetaDataRepository.deleteCharacterFromMedia(mediaId, nameEntityId);
            case ContentMetaData.UNIVERSES -> mediaMetaDataRepository.deleteUniverseFromMedia(mediaId, nameEntityId);
            case ContentMetaData.TAGS -> mediaMetaDataRepository.deleteTagFromMedia(mediaId, nameEntityId);
            default -> throw new IllegalArgumentException("Invalid name entity type: " + nameEntity);
        }
    }

    private void addNameEntityInMedia(long mediaId, long nameEntityId, MediaNameEntity nameEntity) {
        switch (nameEntity.getName()) {
            case ContentMetaData.AUTHORS -> mediaMetaDataRepository.addAuthorToMedia(mediaId, nameEntityId);
            case ContentMetaData.CHARACTERS -> mediaMetaDataRepository.addCharacterToMedia(mediaId, nameEntityId);
            case ContentMetaData.UNIVERSES -> mediaMetaDataRepository.addUniverseToMedia(mediaId, nameEntityId);
            case ContentMetaData.TAGS -> mediaMetaDataRepository.addTagToMedia(mediaId, nameEntityId);
            default -> throw new IllegalArgumentException("Invalid name entity type: " + nameEntity);
        }
    }
}
