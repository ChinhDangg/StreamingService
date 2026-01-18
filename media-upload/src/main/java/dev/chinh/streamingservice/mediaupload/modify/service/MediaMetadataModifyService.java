package dev.chinh.streamingservice.mediaupload.modify.service;

import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.common.exception.DuplicateEntryException;
import dev.chinh.streamingservice.mediaupload.upload.service.MediaDisplayService;
import dev.chinh.streamingservice.mediaupload.upload.service.MediaSearchCacheService;
import dev.chinh.streamingservice.mediaupload.upload.service.MediaUploadService;
import dev.chinh.streamingservice.mediaupload.upload.service.MinIOService;
import dev.chinh.streamingservice.persistence.entity.MediaGroupMetaData;
import dev.chinh.streamingservice.persistence.entity.MediaMetaData;
import dev.chinh.streamingservice.persistence.projection.NameEntityDTO;
import dev.chinh.streamingservice.persistence.repository.MediaGroupMetaDataRepository;
import dev.chinh.streamingservice.persistence.repository.MediaMetaDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

        Long grouperMediaId = null;
        if (mediaMetaData.getMediaType() == MediaType.GROUPER) {
            // for grouper of album - delete each album first - only delete grouper if it is empty
            Optional<MediaGroupMetaData> grouperItems = mediaGroupMetaDataRepository.findFirstByGrouperMetaDataId(mediaMetaData.getGrouperId());
            if (grouperItems.isPresent())
                throw new IllegalArgumentException("Non-empty Grouper media cannot be deleted: " + mediaMetaData.getId());
        } else if (mediaMetaData.getGrouperId() != null) { // else if not a grouper
            // media is part of a grouper - decrement grouper length
            MediaGroupMetaData grouperMetaData = mediaGroupMetaDataRepository.findById(mediaMetaData.getGrouperId()).orElseThrow(
                    () -> new IllegalArgumentException("Grouper media not found: " + mediaMetaData.getGrouperId())
            );
            grouperMediaId = grouperMetaData.getMediaMetaDataId();
            Integer newLength = mediaMetaDataRepository.decrementLengthReturning(grouperMediaId);
            eventPublisher.publishEvent(new MediaUpdateEvent.LengthUpdated(grouperMediaId, newLength));
            mediaGroupMetaDataRepository.decrementNumInfo(mediaMetaData.getGrouperId());
        }

        mediaMetaDataRepository.decrementAuthorLengths(mediaMetaData.getId());
        mediaMetaDataRepository.decrementCharacterLengths(mediaMetaData.getId());
        mediaMetaDataRepository.decrementUniverseLengths(mediaMetaData.getId());
        mediaMetaDataRepository.decrementTagLengths(mediaMetaData.getId());

        mediaMetaDataRepository.deleteById(mediaMetaData.getId());

        try {
            eventPublisher.publishEvent(new MediaUpdateEvent.MediaDeleted(
                    mediaId,
                    mediaMetaData.getBucket(),
                    mediaMetaData.getPath(),
                    mediaMetaData.hasThumbnail(),
                    mediaMetaData.getThumbnail(),
                    mediaMetaData.getMediaType(),
                    mediaMetaData.getAbsoluteFilePath()
            ));
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish event delete media: " + mediaId, e);
        }

        if (mediaMetaData.getMediaType() == MediaType.GROUPER) {
            mediaDisplayService.removeCacheGroupOfMedia(mediaMetaData.getId());
        } else if (mediaMetaData.getGrouperId() != null && grouperMediaId != null) {
            mediaDisplayService.removeCacheGroupOfMedia(grouperMediaId);
            mediaSearchCacheService.removeCachedMediaSearchItem(grouperMediaId);
        }
        mediaSearchCacheService.removeCachedMediaSearchItem(mediaId);
    }

    private MediaMetaData getMediaMetaData(long mediaId) {
        return mediaMetaDataRepository.findById(mediaId).orElseThrow(
                () -> new IllegalArgumentException("Media not found: " + mediaId)
        );
    }

    @Transactional(readOnly = true)
    public void updateMediaThumbnail(long mediaId, Double num, MultipartFile multipartFile) throws Exception {
        boolean hasFile = multipartFile != null && multipartFile.getSize() > 0;
        if (!hasFile && num == null) {
            throw new IllegalArgumentException("Thumbnail file is empty and no thumbnail number provided");
        }
        MediaMetaData mediaMetaData = getMediaMetaData(mediaId);
        MediaType mediaType = mediaMetaData.getMediaType();
        if (hasFile) {
            minIOService.uploadFile(ContentMetaData.THUMBNAIL_BUCKET, mediaMetaData.getThumbnail(), multipartFile);
        } else if (mediaType == MediaType.VIDEO || mediaType == MediaType.ALBUM) {
            if (num < 0 || num >= mediaMetaData.getLength()) {
                throw new IllegalArgumentException("Invalid thumbnail number: " + num);
            }
            eventPublisher.publishEvent(new MediaUpdateEvent.MediaThumbnailUpdated(
                    mediaMetaData.getId(),
                    mediaType,
                    num,
                    MediaUploadService.createMediaThumbnailString(mediaType, mediaMetaData.getId(), mediaMetaData.getPath())
            ));
        }
        mediaSearchCacheService.removeCachedMediaSearchItem(mediaId);
    }
}
