package dev.chinh.streamingservice.mediaupload.modify.service;

import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.exception.ResourceNotFoundException;
import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.mediaupload.event.MediaUploadEventProducer;
import dev.chinh.streamingservice.mediaupload.modify.dto.NameAndThumbnailPostRequest;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.mediaupload.upload.service.MinIOService;
import dev.chinh.streamingservice.mediapersistence.entity.*;
import dev.chinh.streamingservice.mediapersistence.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NameEntityModifyService {

    private final MediaAuthorRepository mediaAuthorRepository;
    private final MediaCharacterRepository mediaCharacterRepository;
    private final MediaUniverseRepository mediaUniverseRepository;
    private final MediaTagRepository mediaTagRepository;
    private final MinIOService minIOService;
    private final ApplicationEventPublisher eventPublisher;

    public Long[] getMediaNameEntityIdByUserIdAndIdIn(long userId, List<Long> ids, MediaNameEntityConstant nameEntity) {
        List<Long> result = switch (nameEntity) {
            case MediaNameEntityConstant.AUTHORS -> mediaAuthorRepository.findIdByUserIdAndIdIn(userId, ids);
            case MediaNameEntityConstant.CHARACTERS -> mediaCharacterRepository.findIdByUserIdAndIdIn(userId, ids);
            case MediaNameEntityConstant.UNIVERSES -> mediaUniverseRepository.findIdByUserIdAndIdIn(userId, ids);
            case MediaNameEntityConstant.TAGS -> mediaTagRepository.findIdByUserIdAndIdIn(userId, ids);
        };
        return result.toArray(new Long[0]);
    }

    public void incrementEntityLengthCount(long userId, Long[] nameEntityIds, MediaNameEntityConstant nameEntityConstant) {
        int updated = switch (nameEntityConstant) {
            case MediaNameEntityConstant.AUTHORS -> mediaAuthorRepository.incrementLength(userId, nameEntityIds);
            case MediaNameEntityConstant.CHARACTERS -> mediaCharacterRepository.incrementLength(userId, nameEntityIds);
            case MediaNameEntityConstant.UNIVERSES -> mediaUniverseRepository.incrementLength(userId, nameEntityIds);
            case MediaNameEntityConstant.TAGS -> mediaTagRepository.incrementLength(userId, nameEntityIds);
        };
        if (updated == 0) throw new ResourceNotFoundException("No " + nameEntityConstant.getName() + " entry found with id: " + Arrays.toString(nameEntityIds));
    }

    public void decrementNameEntityLengthCount(long userId, Long[] nameEntityIds, MediaNameEntityConstant nameEntityConstant) {
        int updated = switch (nameEntityConstant) {
            case MediaNameEntityConstant.AUTHORS -> mediaAuthorRepository.decrementLength(userId, nameEntityIds);
            case MediaNameEntityConstant.CHARACTERS -> mediaCharacterRepository.decrementLength(userId, nameEntityIds);
            case MediaNameEntityConstant.UNIVERSES -> mediaUniverseRepository.decrementLength(userId, nameEntityIds);
            case MediaNameEntityConstant.TAGS -> mediaTagRepository.decrementLength(userId, nameEntityIds);
        };
        if (updated == 0) throw new ResourceNotFoundException("No " + nameEntityConstant.getName() + " entry found with id: " + Arrays.toString(nameEntityIds));
    }


    @Transactional
    public void addAuthor(String userId, String name) {
        addNameEntity(userId, name, MediaNameEntityConstant.AUTHORS, new MediaAuthor(Long.parseLong(userId), name), mediaAuthorRepository);
    }

    @Transactional
    public void addCharacter(String userId, NameAndThumbnailPostRequest request) {
        addNameEntity(userId, request, MediaNameEntityConstant.CHARACTERS, new MediaCharacter(Long.parseLong(userId), request.getName()), mediaCharacterRepository);
    }

    @Transactional
    public void addUniverse(String userId, NameAndThumbnailPostRequest request) {
        addNameEntity(userId, request, MediaNameEntityConstant.UNIVERSES, new MediaUniverse(Long.parseLong(userId), request.getName()), mediaUniverseRepository);
    }

    @Transactional
    public void addTag(String userId, String name) {
        addNameEntity(userId, name, MediaNameEntityConstant.TAGS, new MediaTag(Long.parseLong(userId), name), mediaTagRepository);
    }

    @Transactional
    protected <T extends MediaNameEntity> void addNameEntity(String userId,
                                                             String name,
                                                             MediaNameEntityConstant mediaNameEntityConstant,
                                                             T mediaNameEntity,
                                                             MediaNameEntityRepository<T, Long> repository) {
        name = validateNameEntity(name);
        mediaNameEntity.setName(name);

        try {
            T added = repository.save(mediaNameEntity);
            long id = added.getId();

            eventPublisher.publishEvent(new MediaUploadEventProducer.EventWrapper(
                    EventTopics.MEDIA_SEARCH_TOPIC,
                    new MediaUpdateEvent.NameEntityCreated(userId, mediaNameEntityConstant, id, null)
            ));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Name already exists: " + name);
        }
    }

    @Transactional
    protected <T extends MediaNameEntityWithThumbnail> void addNameEntity(String userId,
                                                                          NameAndThumbnailPostRequest request,
                                                                          MediaNameEntityConstant mediaNameEntityConstant,
                                                                          T mediaNameEntity,
                                                                          MediaNameEntityRepository<T, Long> repository) {
        String thumbnailPath = null;
        try {
            String name = validateNameEntity(request.getName());

            mediaNameEntity.setName(name);

            T added = repository.save(mediaNameEntity);
            long id = added.getId();

            if (request.getThumbnail() != null) {
                String extension = request.getThumbnail().getOriginalFilename() == null ? ".jpg"
                        : request.getThumbnail().getOriginalFilename().substring(request.getThumbnail().getOriginalFilename().lastIndexOf("."));

                thumbnailPath = createNameEntityThumbnail(userId, mediaNameEntityConstant, id, extension);

                minIOService.uploadFile(ContentMetaData.THUMBNAIL_BUCKET, thumbnailPath, request.getThumbnail());
            }

            if (request.getThumbnail() != null)
                mediaNameEntity.setThumbnail(thumbnailPath);
            repository.save(mediaNameEntity);

            String topic = thumbnailPath != null
                    ? EventTopics.MEDIA_SEARCH_AND_BACKUP_TOPIC
                    : EventTopics.MEDIA_SEARCH_TOPIC;
            eventPublisher.publishEvent(new MediaUploadEventProducer.EventWrapper(
                    topic,
                    new MediaUpdateEvent.NameEntityCreated(userId, mediaNameEntityConstant, id, thumbnailPath)
            ));
        } catch (Exception e) {
            try {
                if (thumbnailPath != null) {
                    minIOService.removeFile(ContentMetaData.THUMBNAIL_BUCKET, thumbnailPath);
                    System.out.println("Compensation successful: Deleted file " + thumbnailPath + " from Object Storage.");
                }
            } catch (Exception deleteEx) {
                System.err.println("CRITICAL: Failed to clean up orphan file: " + thumbnailPath);
            }
            if (e instanceof DataIntegrityViolationException) {
                throw new IllegalArgumentException("Name already exists: " + request.getName());
            }
            throw new RuntimeException("Entity creation failed after file upload.", e);
        }
    }


    @Transactional
    public void updateAuthor(String userId, long id, String name) {
        updateNameEntity(userId, id, name, MediaNameEntityConstant.AUTHORS, mediaAuthorRepository);
    }

    @Transactional
    public void updateCharacter(String userId, long id, NameAndThumbnailPostRequest request) {
        updateNameEntity(userId, id, request, MediaNameEntityConstant.CHARACTERS, mediaCharacterRepository);
    }

    @Transactional
    public void updateUniverse(String userId, long id, NameAndThumbnailPostRequest request) {
        updateNameEntity(userId, id, request, MediaNameEntityConstant.UNIVERSES, mediaUniverseRepository);
    }

    @Transactional
    public void updateTag(String userId, long id, String name) {
        updateNameEntity(userId, id, name, MediaNameEntityConstant.TAGS, mediaTagRepository);
    }

    @Transactional
    protected <T extends MediaNameEntity> void updateNameEntity(String userId, long id, String name,
                                                                MediaNameEntityConstant mediaNameEntityConstant,
                                                                MediaNameEntityRepository<T, Long> repository) {
        T nameEntity = findByIdAndUserId(Long.parseLong(userId), id, mediaNameEntityConstant, repository);
        name = validateNameEntity(name);
        if (nameEntity.getName().equals(name))
            return;
        nameEntity.setName(name);

        try {
            repository.save(nameEntity);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Name already exists: " + name);
        }

        eventPublisher.publishEvent(new MediaUploadEventProducer.EventWrapper(
                EventTopics.MEDIA_SEARCH_TOPIC,
                new MediaUpdateEvent.NameEntityUpdated(userId, mediaNameEntityConstant, id, null, null)
        ));
    }

    @Transactional
    protected <T extends MediaNameEntityWithThumbnail> void updateNameEntity(String userId, long id, NameAndThumbnailPostRequest request,
                                                                             MediaNameEntityConstant mediaNameEntityConstant,
                                                                             MediaNameEntityRepository<T, Long> repository) {
        if (request.getThumbnail() == null && (request.getName() == null || request.getName().isBlank()))
            throw new IllegalArgumentException("No name or thumbnail provided");

        T nameEntity = findByIdAndUserId(Long.parseLong(userId), id, mediaNameEntityConstant, repository);
        String oldName = nameEntity.getName();
        String newName = request.getName() == null ? oldName : validateNameEntity(request.getName());

        // The old thumbnail path to be deleted upon successful commit
        String oldThumbnailPath = nameEntity.getThumbnail();
        String newThumbnailPath = null;

        try {
            // UPLOAD NEW FILE FIRST (OUTSIDE TRANSACTION) ---
            if (request.getThumbnail() != null) {
                String extension = request.getThumbnail().getOriginalFilename() == null ? ".jpg" :
                        request.getThumbnail().getOriginalFilename().substring(request.getThumbnail().getOriginalFilename().lastIndexOf("."));

                newThumbnailPath = oldThumbnailPath.endsWith(extension)
                        ? oldThumbnailPath
                        : createNameEntityThumbnail(userId, mediaNameEntityConstant, id, extension);
                minIOService.uploadFile(ContentMetaData.THUMBNAIL_BUCKET, newThumbnailPath, request.getThumbnail());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to update thumbnail for " + mediaNameEntityConstant.getName() + " " + id, e);
        }

        try {
            boolean nameChanged = !oldName.equals(newName);
            boolean thumbnailChanged = newThumbnailPath != null && !newThumbnailPath.equals(oldThumbnailPath);

            if (nameChanged) nameEntity.setName(newName);
            if (thumbnailChanged) nameEntity.setThumbnail(newThumbnailPath);

            if (nameChanged || thumbnailChanged) {
                try {
                    repository.save(nameEntity);
                } catch (DataIntegrityViolationException e) {
                    throw new IllegalArgumentException("Name already exists: " + newName);
                }

                String topic = newThumbnailPath != null // new file has uploaded
                        ? EventTopics.MEDIA_SEARCH_AND_BACKUP_TOPIC
                        : EventTopics.MEDIA_SEARCH_TOPIC;
                eventPublisher.publishEvent(new MediaUploadEventProducer.EventWrapper(
                        topic,
                        new MediaUpdateEvent.NameEntityUpdated(userId, mediaNameEntityConstant, id, oldThumbnailPath, newThumbnailPath)
                ));
                if (thumbnailChanged)
                    eventPublisher.publishEvent(new MediaUploadEventProducer.EventWrapper(
                            EventTopics.MEDIA_OBJECT_TOPIC,
                            new MediaUpdateEvent.ObjectDeleted(ContentMetaData.THUMBNAIL_BUCKET, List.of(oldThumbnailPath))
                    ));
            }

        } catch (Exception e) {
            if (newThumbnailPath != null && !newThumbnailPath.equals(oldThumbnailPath)) {
                try {
                    minIOService.removeFile(ContentMetaData.THUMBNAIL_BUCKET, newThumbnailPath);
                } catch (Exception deleteEx) {
                    System.err.println("CRITICAL: Failed to clean up orphan new uploaded file: " + newThumbnailPath);
                }
            }
            throw e;
        }
    }

    private String createNameEntityThumbnail(String userId, MediaNameEntityConstant name, long id, String extension) {
        return userId + "/" + name + "/" + id + "_" + UUID.randomUUID() + extension;
    }


    @Transactional
    public void deleteAuthor(String userId, long id) {
        deleteNameEntity(userId, id, MediaNameEntityConstant.AUTHORS, mediaAuthorRepository);
    }

    @Transactional
    public void deleteCharacter(String userId, long id) {
        deleteNameEntityWithThumbnail(userId, id, MediaNameEntityConstant.CHARACTERS, mediaCharacterRepository);
    }

    @Transactional
    public void deleteUniverse(String userId, long id) {
        deleteNameEntityWithThumbnail(userId, id, MediaNameEntityConstant.UNIVERSES, mediaUniverseRepository);
    }

    @Transactional
    public void deleteTag(String userId, long id) {
        deleteNameEntity(userId, id, MediaNameEntityConstant.TAGS, mediaTagRepository);
    }

    // to be used locally for check
    @Transactional
    protected <T extends MediaNameEntity> void deleteNameEntity(String userId, long id, MediaNameEntityConstant mediaNameEntityConstant, MediaNameEntityRepository<T, Long> repository) {
        repository.deleteByIdAndUserId(id, Long.parseLong(userId));

        eventPublisher.publishEvent(new MediaUploadEventProducer.EventWrapper(
                EventTopics.MEDIA_SEARCH_TOPIC,
                new MediaUpdateEvent.NameEntityDeleted(mediaNameEntityConstant, id, null)
        ));
    }

    @Transactional
    protected <T extends MediaNameEntityWithThumbnail> void deleteNameEntityWithThumbnail(String userId, long id, MediaNameEntityConstant mediaNameEntityConstant,
                                                                                          MediaNameEntityRepository<T, Long> repository) {
        T nameEntity = findByIdAndUserId(Long.parseLong(userId), id, mediaNameEntityConstant, repository);

        String thumbnailPath = nameEntity.getThumbnail();

        repository.delete(nameEntity);

        String topic = thumbnailPath == null
                ? EventTopics.MEDIA_SEARCH_TOPIC
                : EventTopics.MEDIA_SEARCH_AND_BACKUP_TOPIC;
        eventPublisher.publishEvent(new MediaUploadEventProducer.EventWrapper(
                topic,
                new MediaUpdateEvent.NameEntityDeleted(mediaNameEntityConstant, id, thumbnailPath)
        ));
        if (thumbnailPath != null)
            eventPublisher.publishEvent(new MediaUploadEventProducer.EventWrapper(
                    EventTopics.MEDIA_OBJECT_TOPIC,
                    new MediaUpdateEvent.ObjectDeleted(ContentMetaData.THUMBNAIL_BUCKET, List.of(thumbnailPath))
            ));
    }


    private <T extends MediaNameEntity> T findByIdAndUserId(long userId, long id, MediaNameEntityConstant mediaNameEntityConstant, MediaNameEntityRepository<T, Long> repository) {
        return repository.findByIdAndUserId(id, userId).orElseThrow(() ->
                new ResourceNotFoundException("No " + mediaNameEntityConstant.getName() + " entry found with id: " + id));
    }

    private String validateNameEntity(String name) {
        if (name == null)
            throw new IllegalArgumentException("Name must not be null");
        name = name.trim();
        if (name.isEmpty())
            throw new IllegalArgumentException("Name must not be empty");
        if (name.length() < 3)
            throw new IllegalArgumentException("Name must be at least 3 chars: " + name);
        if (name.length() > 200)
            throw new IllegalArgumentException("Name must be at most 200 chars");
        return name;
    }
}
