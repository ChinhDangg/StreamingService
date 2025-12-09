package dev.chinh.streamingservice.modify.service;

import dev.chinh.streamingservice.data.ContentMetaData;
import dev.chinh.streamingservice.data.repository.MediaMetaDataRepository;
import dev.chinh.streamingservice.data.service.MediaMetadataService;
import dev.chinh.streamingservice.event.MediaUpdateEvent;
import dev.chinh.streamingservice.modify.MediaNameEntityConstant;
import dev.chinh.streamingservice.modify.NameEntityDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaMetadataModifyService {

    private final MediaMetaDataRepository mediaMetaDataRepository;
    private final MediaMetadataService mediaMetadataService;
    private final NameEntityModifyService nameEntityModifyService;

    private final ApplicationEventPublisher eventPublisher;

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

        int updated = mediaMetaDataRepository.updateMediaTitle(mediaId, newTitle);
        if (updated == 0) throw new IllegalArgumentException("Media not found: " + mediaId);

        eventPublisher.publishEvent(new MediaUpdateEvent.MediaTitleUpdated(mediaId));

        mediaMetadataService.removeCachedMediaSearchItem(mediaId);
        return newTitle;
    }

    public record UpdateList(
            List<NameEntityDTO> adding, List<NameEntityDTO> removing, MediaNameEntityConstant nameEntity
    ) {}

    @Transactional
    public List<String> updateNameEntityInMedia(UpdateList updateList, long mediaId) {
        if (updateList.adding.isEmpty() && updateList.removing.isEmpty()) return new ArrayList<>();

        List<NameEntityDTO> uniqueAdding = new ArrayList<>(updateList.adding.stream().distinct().toList());
        List<NameEntityDTO> uniqueRemoving = updateList.removing.stream().distinct().toList();
        uniqueAdding.removeAll(uniqueRemoving);

        if (uniqueAdding.isEmpty() && uniqueRemoving.isEmpty()) return new ArrayList<>();

        for (NameEntityDTO nameEntityDTO : uniqueRemoving) {
            int removed = removeNameEntityInMedia(mediaId, nameEntityDTO.getId(), updateList.nameEntity);
            if (removed == 0) throw new IllegalArgumentException("Name entity not found: " + nameEntityDTO.getId());
            nameEntityModifyService.decrementNameEntityLengthCount(updateList.nameEntity, nameEntityDTO.getId());
        }
        for (NameEntityDTO nameEntityDTO : uniqueAdding) {
            int added = addNameEntityInMedia(mediaId, nameEntityDTO.getId(), updateList.nameEntity);
            if (added == 0) throw new IllegalArgumentException("Name entity not found: " + nameEntityDTO.getId());
            nameEntityModifyService.incrementNameEntityLengthCount(updateList.nameEntity, nameEntityDTO.getId());
        }
        List<NameEntityDTO> updatedMediaNameEntityList = getMediaNameEntityInfo(mediaId, updateList.nameEntity);
        List<String> nameEntityList = updatedMediaNameEntityList.stream().map(NameEntityDTO::getName).toList();

        eventPublisher.publishEvent(new MediaUpdateEvent.MediaNameEntityUpdated(mediaId, updateList.nameEntity));

        mediaMetadataService.removeCachedMediaSearchItem(mediaId);
        return nameEntityList;
    }

    private int removeNameEntityInMedia(long mediaId, long nameEntityId, MediaNameEntityConstant nameEntity) {
        return switch (nameEntity) {
            case MediaNameEntityConstant.AUTHORS -> mediaMetaDataRepository.deleteAuthorFromMedia(mediaId, nameEntityId);
            case MediaNameEntityConstant.CHARACTERS -> mediaMetaDataRepository.deleteCharacterFromMedia(mediaId, nameEntityId);
            case MediaNameEntityConstant.UNIVERSES -> mediaMetaDataRepository.deleteUniverseFromMedia(mediaId, nameEntityId);
            case MediaNameEntityConstant.TAGS -> mediaMetaDataRepository.deleteTagFromMedia(mediaId, nameEntityId);
        };
    }

    private int addNameEntityInMedia(long mediaId, long nameEntityId, MediaNameEntityConstant nameEntity) {
        return switch (nameEntity) {
            case MediaNameEntityConstant.AUTHORS -> mediaMetaDataRepository.addAuthorToMedia(mediaId, nameEntityId);
            case MediaNameEntityConstant.CHARACTERS -> mediaMetaDataRepository.addCharacterToMedia(mediaId, nameEntityId);
            case MediaNameEntityConstant.UNIVERSES -> mediaMetaDataRepository.addUniverseToMedia(mediaId, nameEntityId);
            case MediaNameEntityConstant.TAGS -> mediaMetaDataRepository.addTagToMedia(mediaId, nameEntityId);
        };
    }
}
