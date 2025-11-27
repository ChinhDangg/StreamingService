package dev.chinh.streamingservice.upload;

import dev.chinh.streamingservice.data.ContentMetaData;
import dev.chinh.streamingservice.data.repository.MediaMetaDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaMetadataModifyService {

    private final MediaMetaDataRepository mediaMetaDataRepository;

    public List<NameEntityDTO> getMediaAuthorInfo(long mediaId) {
        return mediaMetaDataRepository.findAuthorsByMediaId(mediaId);
    }

    public List<NameEntityDTO> getMediaCharacterInfo(long mediaId) {
        return mediaMetaDataRepository.findCharactersByMediaId(mediaId);
    }

    public List<NameEntityDTO> getMediaUniverseInfo(long mediaId) {
        return mediaMetaDataRepository.findUniversesByMediaId(mediaId);
    }

    public List<NameEntityDTO> getMediaTagInfo(long mediaId) {
        return mediaMetaDataRepository.findTagsByMediaId(mediaId);
    }

    public record UpdateList(
            List<NameEntityDTO> adding, List<NameEntityDTO> removing, String nameEntity
    ) {}

    @Transactional
    public void updateNameEntityInMedia(UpdateList updateList, long mediaId) {
        for (NameEntityDTO nameEntityDTO : updateList.removing) {
            removeNameEntityInMedia(mediaId, nameEntityDTO.getId(), updateList.nameEntity);
        }
        for (NameEntityDTO nameEntityDTO : updateList.adding) {
            addNameEntityInMedia(mediaId, nameEntityDTO.getId(), updateList.nameEntity);
        }
    }

    private void removeNameEntityInMedia(long mediaId, long nameEntityId, String nameEntity) {
        switch (nameEntity) {
            case ContentMetaData.AUTHORS -> mediaMetaDataRepository.deleteAuthorFromMedia(mediaId, nameEntityId);
            case ContentMetaData.CHARACTERS -> mediaMetaDataRepository.deleteCharacterFromMedia(mediaId, nameEntityId);
            case ContentMetaData.UNIVERSES -> mediaMetaDataRepository.deleteUniverseFromMedia(mediaId, nameEntityId);
            case ContentMetaData.TAGS -> mediaMetaDataRepository.deleteTagFromMedia(mediaId, nameEntityId);
            default -> throw new RuntimeException("Invalid name entity type: " + nameEntity);
        }
    }

    private void addNameEntityInMedia(long mediaId, long nameEntityId, String nameEntity) {
        switch (nameEntity) {
            case ContentMetaData.AUTHORS -> mediaMetaDataRepository.addAuthorToMedia(mediaId, nameEntityId);
            case ContentMetaData.CHARACTERS -> mediaMetaDataRepository.addCharacterToMedia(mediaId, nameEntityId);
            case ContentMetaData.UNIVERSES -> mediaMetaDataRepository.addUniverseToMedia(mediaId, nameEntityId);
            case ContentMetaData.TAGS -> mediaMetaDataRepository.addTagToMedia(mediaId, nameEntityId);
            default -> throw new RuntimeException("Invalid name entity type: " + nameEntity);
        }
    }
}
