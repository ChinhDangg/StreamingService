package dev.chinh.streamingservice.mediaupload.modify.service;

import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.common.exception.DuplicateEntryException;
import dev.chinh.streamingservice.mediaupload.upload.service.MediaDisplayService;
import dev.chinh.streamingservice.mediaupload.upload.service.MediaSearchCacheService;
import dev.chinh.streamingservice.mediaupload.upload.service.MinIOService;
import dev.chinh.streamingservice.mediaupload.upload.service.ThumbnailService;
import dev.chinh.streamingservice.persistence.entity.MediaGroupMetaData;
import dev.chinh.streamingservice.persistence.entity.MediaMetaData;
import dev.chinh.streamingservice.persistence.projection.NameEntityDTO;
import dev.chinh.streamingservice.persistence.repository.MediaGroupMetaDataRepository;
import dev.chinh.streamingservice.persistence.repository.MediaMetaDataRepository;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MediaMetadataModifyService {

    private final MediaMetaDataRepository mediaMetaDataRepository;
    private final MediaGroupMetaDataRepository mediaGroupMetaDataRepository;
    private final MediaSearchCacheService mediaSearchCacheService;
    private final NameEntityModifyService nameEntityModifyService;
    private final MediaDisplayService mediaDisplayService;
    private final ThumbnailService thumbnailService;
    private final MinIOService minIOService;

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

        mediaSearchCacheService.removeCachedMediaSearchItem(mediaId);
        return newTitle;
    }

    public record UpdateList(
            List<NameEntityDTO> adding, List<NameEntityDTO> removing, MediaNameEntityConstant nameEntity
    ) {}

    @Transactional
    public List<NameEntityDTO> updateNameEntityInMedia(UpdateList updateList, long mediaId) {
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

        eventPublisher.publishEvent(new MediaUpdateEvent.MediaNameEntityUpdated(mediaId, updateList.nameEntity));

        mediaSearchCacheService.removeCachedMediaSearchItem(mediaId);
        return updatedMediaNameEntityList;
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
        try {
            return switch (nameEntity) {
                case MediaNameEntityConstant.AUTHORS -> mediaMetaDataRepository.addAuthorToMedia(mediaId, nameEntityId);
                case MediaNameEntityConstant.CHARACTERS -> mediaMetaDataRepository.addCharacterToMedia(mediaId, nameEntityId);
                case MediaNameEntityConstant.UNIVERSES -> mediaMetaDataRepository.addUniverseToMedia(mediaId, nameEntityId);
                case MediaNameEntityConstant.TAGS -> mediaMetaDataRepository.addTagToMedia(mediaId, nameEntityId);
            };
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateEntryException("Duplicate name entity in media: " + nameEntityId);
        }
    }

    @Transactional
    public void deleteMedia(long mediaId) {
        MediaMetaData mediaMetaData = mediaMetaDataRepository.findById(mediaId).orElseThrow(
                () -> new IllegalArgumentException("Media not found: " + mediaId)
        );

        if (mediaMetaData.getMediaType() == MediaType.GROUPER) {
            // for grouper of album - delete each album first - only delete grouper if it is empty
            Optional<MediaGroupMetaData> grouperItems = mediaGroupMetaDataRepository.findFirstByGrouperMetaDataId(mediaMetaData.getId());
            if (grouperItems.isPresent())
                throw new IllegalArgumentException("Non-empty Grouper media cannot be deleted: " + mediaMetaData.getId());
        }

        mediaMetaDataRepository.decrementAuthorLengths(mediaMetaData.getId());
        mediaMetaDataRepository.decrementCharacterLengths(mediaMetaData.getId());
        mediaMetaDataRepository.decrementUniverseLengths(mediaMetaData.getId());
        mediaMetaDataRepository.decrementTagLengths(mediaMetaData.getId());

        mediaMetaDataRepository.deleteById(mediaMetaData.getId());

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            eventPublisher.publishEvent(new MediaUpdateEvent.MediaDeleted(mediaId));
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to publish event delete opensearch index: " + mediaId, e);
                        }

                        if (mediaMetaData.getMediaType() == MediaType.VIDEO) {
                            try {
                                if (mediaMetaData.hasThumbnail())
                                    thumbnailService.deleteMediaThumbnail(mediaMetaData.getThumbnail());
                            } catch (Exception e) {
                                throw new RuntimeException("Failed to delete thumbnail file: " + mediaMetaData.getThumbnail(), e);
                            }
                            try {
                                minIOService.removeFile(mediaMetaData.getBucket(), mediaMetaData.getPath());
                            } catch (Exception e) {
                                throw new RuntimeException("Failed to delete media file: " + mediaMetaData.getPath(), e);
                            }
                        } else if (mediaMetaData.getMediaType() == MediaType.ALBUM) {
                            try {
                                Iterable<Result<Item>> results = minIOService.getAllItemsInBucketWithPrefix(mediaMetaData.getBucket(), mediaMetaData.getPath());
                                for (Result<Item> result : results) {
                                    String objectName = result.get().objectName();
                                    minIOService.removeFile(mediaMetaData.getBucket(), objectName);
                                }
                            } catch (Exception e) {
                                throw new RuntimeException("Failed to delete media files in album: " + mediaMetaData.getPath(), e);
                            }
                        } else if (mediaMetaData.getMediaType() == MediaType.GROUPER) {
                            mediaDisplayService.removeCacheGroupOfMedia(mediaMetaData.getId());
                        }
                        mediaSearchCacheService.removeCachedMediaSearchItem(mediaId);
                    }
                });
    }
}
