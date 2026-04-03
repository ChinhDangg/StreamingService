package dev.chinh.streamingservice.mediaupload.modify.service;

import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.common.exception.DuplicateEntryException;
import dev.chinh.streamingservice.mediapersistence.repository.*;
import dev.chinh.streamingservice.mediaupload.event.MediaUploadEventProducer;
import dev.chinh.streamingservice.mediaupload.upload.service.MediaDisplayService;
import dev.chinh.streamingservice.mediaupload.upload.service.MediaSearchCacheService;
import dev.chinh.streamingservice.mediaupload.upload.service.MediaUploadService;
import dev.chinh.streamingservice.mediaupload.upload.service.MinIOService;
import dev.chinh.streamingservice.mediapersistence.entity.MediaGroupMetaData;
import dev.chinh.streamingservice.mediapersistence.entity.MediaMetaData;
import dev.chinh.streamingservice.mediapersistence.projection.NameEntityDTO;
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

    public List<NameEntityDTO> getMediaNameEntityInfo(String userIdStr, long mediaId, MediaNameEntityConstant nameEntity) {
        long userId = Long.parseLong(userIdStr);
        return switch (nameEntity.getName()) {
            case ContentMetaData.AUTHORS -> mediaMetaDataRepository.findAuthorsByMediaId(userId, mediaId);
            case ContentMetaData.CHARACTERS -> mediaMetaDataRepository.findCharactersByMediaId(userId, mediaId);
            case ContentMetaData.UNIVERSES -> mediaMetaDataRepository.findUniversesByMediaId(userId, mediaId);
            case ContentMetaData.TAGS -> mediaMetaDataRepository.findTagsByMediaId(userId, mediaId);
            default -> throw new IllegalArgumentException("Invalid name entity type: " + nameEntity);
        };
    }

    @Transactional
    public String updateMediaTitle(String userId, long mediaId, String newTitle) {
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

        int updated = mediaMetaDataRepository.updateMediaTitle(Long.parseLong(userId), mediaId, newTitle);
        if (updated == 0) throw new IllegalArgumentException("Media not found: " + mediaId);

        eventPublisher.publishEvent(new MediaUploadEventProducer.EventWrapper(
                EventTopics.MEDIA_SEARCH_TOPIC,
                new MediaUpdateEvent.MediaTitleUpdated(userId, mediaId)
        ));

        mediaSearchCacheService.removeCachedMediaSearchItem(mediaId);
        return newTitle;
    }

    public record UpdateList(
            List<NameEntityDTO> adding, List<NameEntityDTO> removing, MediaNameEntityConstant nameEntity
    ) {}

    @Transactional
    public void updateNameEntityInMediaInBatch(String userId, List<UpdateList> updateLists, long mediaId, boolean publishSearchUpdate) {
        MediaMetaData mediaMetaData = getMediaMetaData(mediaId);
        for (UpdateList updateList : updateLists) {
            updateNameEntityInMedia(userId, updateList, mediaId, publishSearchUpdate, mediaMetaData);
        }
    }

    @Transactional
    public List<NameEntityDTO> updateNameEntityInMedia(String userIdStr, UpdateList updateList, long mediaId, boolean publishSearchUpdate, MediaMetaData mediaMetaData) {
        if ((updateList.adding == null || updateList.adding.isEmpty()) && (updateList.removing == null || updateList.removing.isEmpty())) return new ArrayList<>();

        long userId = Long.parseLong(userIdStr);

        mediaMetaData = mediaMetaData == null ? getMediaMetaData(mediaId) : mediaMetaData;
        if (mediaMetaData.getUserId() != userId)
            throw new IllegalArgumentException("No media found for user: " + userId);

        List<Long> uniqueAdding = updateList.adding == null
                ? new ArrayList<>()
                : new ArrayList<>(updateList.adding.stream().map(NameEntityDTO::getId).distinct().toList());
        List<Long> uniqueRemoving = updateList.removing == null
                ? new ArrayList<>()
                : updateList.removing.stream().map(NameEntityDTO::getId).distinct().toList();
        uniqueAdding.removeAll(uniqueRemoving);

        if (uniqueAdding.isEmpty() && uniqueRemoving.isEmpty()) return new ArrayList<>();

        if (!uniqueAdding.isEmpty()) {
            Long[] addingIds = nameEntityModifyService.getMediaNameEntityIdByUserIdAndIdIn(userId, uniqueAdding, updateList.nameEntity);
            if (addingIds.length > 0) {
                int added = addNameEntitiesToMedia(mediaId, addingIds, updateList.nameEntity);
                System.out.println("Adding: " + addingIds.length + " Added: " + added);
                nameEntityModifyService.incrementEntityLengthCount(userId, addingIds, updateList.nameEntity);
            }
        }

        if (!uniqueRemoving.isEmpty()) {
            Long[] removingIds = nameEntityModifyService.getMediaNameEntityIdByUserIdAndIdIn(userId, uniqueRemoving, updateList.nameEntity);
            if (removingIds.length > 0) {
                int removed = removeNameEntitiesFromMedia(mediaId, removingIds, updateList.nameEntity);
                System.out.println("Removing: " + removingIds.length + " Removed: " + removed);
                nameEntityModifyService.decrementNameEntityLengthCount(userId, removingIds, updateList.nameEntity);
            }
        }

        List<NameEntityDTO> updatedMediaNameEntityList = getMediaNameEntityInfo(userIdStr, mediaId, updateList.nameEntity);

        if (publishSearchUpdate)
            eventPublisher.publishEvent(new MediaUploadEventProducer.EventWrapper(
                    EventTopics.MEDIA_SEARCH_TOPIC,
                    new MediaUpdateEvent.MediaNameEntityUpdated(userIdStr, mediaId, updateList.nameEntity)
            ));

        mediaSearchCacheService.removeCachedMediaSearchItem(mediaId);
        return updatedMediaNameEntityList;
    }

    private int addNameEntitiesToMedia(long mediaId, Long[] nameEntityIds, MediaNameEntityConstant nameEntity) {
        try {
            return switch (nameEntity) {
                case MediaNameEntityConstant.AUTHORS -> mediaMetaDataRepository.addAuthorsToMedia(mediaId, nameEntityIds);
                case MediaNameEntityConstant.CHARACTERS -> mediaMetaDataRepository.addCharactersToMedia(mediaId, nameEntityIds);
                case MediaNameEntityConstant.UNIVERSES -> mediaMetaDataRepository.addUniversesToMedia(mediaId, nameEntityIds);
                case MediaNameEntityConstant.TAGS -> mediaMetaDataRepository.addTagsToMedia(mediaId, nameEntityIds);
            };
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateEntryException("Duplicate name entity in media: " + e.getMessage());
        }
    }

    private int removeNameEntitiesFromMedia(long mediaId, Long[] nameEntityIds, MediaNameEntityConstant nameEntity) {
        return switch (nameEntity) {
            case MediaNameEntityConstant.AUTHORS -> mediaMetaDataRepository.deleteAuthorsFromMedia(mediaId, nameEntityIds);
            case MediaNameEntityConstant.CHARACTERS -> mediaMetaDataRepository.deleteCharactersFromMedia(mediaId, nameEntityIds);
            case MediaNameEntityConstant.UNIVERSES -> mediaMetaDataRepository.deleteUniversesFromMedia(mediaId, nameEntityIds);
            case MediaNameEntityConstant.TAGS -> mediaMetaDataRepository.deleteTagsFromMedia(mediaId, nameEntityIds);
        };
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
    public void deleteMedia(String userIdStr, long mediaId) {
        MediaMetaData mediaMetaData = mediaMetaDataRepository.findById(mediaId).orElseThrow(
                () -> new IllegalArgumentException("Media not found: " + mediaId)
        );

        long userId = Long.parseLong(userIdStr);
        if (mediaMetaData.getUserId() != userId)
            throw new IllegalArgumentException("No media found for user: " + userId);

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
            Integer newLength = mediaMetaDataRepository.decrementLengthReturning(userId, grouperMediaId);
            eventPublisher.publishEvent(new MediaUploadEventProducer.EventWrapper(
                    EventTopics.MEDIA_SEARCH_TOPIC,
                    new MediaUpdateEvent.LengthUpdated(grouperMediaId, newLength)
            ));
        }

        mediaMetaDataRepository.decrementAuthorLengths(userId, mediaMetaData.getId());
        mediaMetaDataRepository.decrementCharacterLengths(userId, mediaMetaData.getId());
        mediaMetaDataRepository.decrementUniverseLengths(userId, mediaMetaData.getId());
        mediaMetaDataRepository.decrementTagLengths(userId, mediaMetaData.getId());

        mediaMetaDataRepository.deleteById(mediaMetaData.getId());

        if (mediaMetaData.getMediaType() == MediaType.GROUPER) {
            mediaDisplayService.removeCacheGroupOfMedia(mediaMetaData.getId());
        } else if (mediaMetaData.getGrouperId() != null && grouperMediaId != null) {
            mediaDisplayService.removeCacheGroupOfMedia(grouperMediaId);
            mediaSearchCacheService.removeCachedMediaSearchItem(grouperMediaId);
        }
        mediaSearchCacheService.removeCachedMediaSearchItem(mediaId);
    }

    @Transactional
    public void incrementMediaLength(long userId, long mediaId) {
        Integer newLength = mediaMetaDataRepository.incrementLengthReturning(userId, mediaId);
        if (newLength == null)
            throw new IllegalArgumentException("Media not found: " + mediaId);

        eventPublisher.publishEvent(new MediaUploadEventProducer.EventWrapper(
                EventTopics.MEDIA_SEARCH_TOPIC,
                new MediaUpdateEvent.LengthUpdated(mediaId, newLength)
        ));
        mediaDisplayService.removeCacheGroupOfMedia(mediaId);
        mediaSearchCacheService.removeCachedMediaSearchItem(mediaId);
    }

    private MediaMetaData getMediaMetaData(long mediaId) {
        return mediaMetaDataRepository.findById(mediaId).orElseThrow(
                () -> new IllegalArgumentException("Media not found: " + mediaId)
        );
    }

    @Transactional(readOnly = true)
    public void updateMediaThumbnail(String userId, long mediaId, Double num, MultipartFile multipartFile) throws Exception {
        boolean hasFile = multipartFile != null && multipartFile.getSize() > 0;
        if (!hasFile && num == null) {
            throw new IllegalArgumentException("Thumbnail file is empty and no thumbnail number provided");
        }
        MediaMetaData mediaMetaData = getMediaMetaData(mediaId);
        if (mediaMetaData.getUserId() != Long.parseLong(userId))
            throw new IllegalArgumentException("No media found for user: " + userId);

        MediaType mediaType = mediaMetaData.getMediaType();

        if (hasFile) {
            String extension = MediaUploadService.getFileExtension(multipartFile.getOriginalFilename());
            String newThumbnail = mediaMetaData.getThumbnail().endsWith(extension)
                    ? mediaMetaData.getThumbnail()
                    : MediaUploadService.createMediaThumbnailString(userId, mediaType, mediaId, multipartFile.getOriginalFilename());
            minIOService.uploadFile(ContentMetaData.THUMBNAIL_BUCKET, newThumbnail, multipartFile);
            if (newThumbnail != null && !newThumbnail.equals(mediaMetaData.getThumbnail())) {
                eventPublisher.publishEvent(new MediaUploadEventProducer.EventWrapper(
                        EventTopics.MEDIA_OBJECT_TOPIC,
                        new MediaUpdateEvent.MediaThumbnailUpdated(userId, mediaId, mediaType, null, mediaMetaData.getBucket(), newThumbnail)
                ));
            }
        } else if (mediaType == MediaType.VIDEO || mediaType == MediaType.ALBUM) {
            if (num < 0 || num >= mediaMetaData.getLength()) {
                throw new IllegalArgumentException("Invalid thumbnail number: " + num);
            }
            if (mediaType == MediaType.VIDEO) {
                eventPublisher.publishEvent(new MediaUploadEventProducer.EventWrapper(
                        EventTopics.MEDIA_OBJECT_TOPIC,
                        new MediaUpdateEvent.MediaThumbnailUpdated(
                                userId,
                                mediaMetaData.getId(),
                                mediaType,
                                num,
                                mediaMetaData.getBucket(),
                                mediaMetaData.getThumbnail()
                        )
                ));
            }
            if (mediaType == MediaType.ALBUM) {
                eventPublisher.publishEvent(new MediaUploadEventProducer.EventWrapper(
                        EventTopics.MEDIA_FILE_TOPIC,
                        new MediaUpdateEvent.MediaThumbnailUpdateInitiated(
                                userId,
                                mediaMetaData.getId(),
                                mediaType,
                                Integer.parseInt(num.toString())
                        )
                ));
            }
        } else
            return;
        mediaSearchCacheService.removeCachedMediaSearchItem(mediaId);
    }
}
