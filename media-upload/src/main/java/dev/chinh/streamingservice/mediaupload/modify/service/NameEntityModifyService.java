package dev.chinh.streamingservice.mediaupload.modify.service;

import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.exception.ResourceNotFoundException;
import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.mediaupload.modify.dto.NameAndThumbnailPostRequest;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.mediaupload.upload.service.MinIOService;
import dev.chinh.streamingservice.persistence.entity.*;
import dev.chinh.streamingservice.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    public void incrementNameEntityLengthCount(MediaNameEntityConstant nameEntityConstant, long nameEntityId) {
        int updated = switch (nameEntityConstant) {
            case MediaNameEntityConstant.AUTHORS -> mediaAuthorRepository.incrementLength(nameEntityId);
            case MediaNameEntityConstant.CHARACTERS -> mediaCharacterRepository.incrementLength(nameEntityId);
            case MediaNameEntityConstant.UNIVERSES -> mediaUniverseRepository.incrementLength(nameEntityId);
            case MediaNameEntityConstant.TAGS -> mediaTagRepository.incrementLength(nameEntityId);
        };
        if (updated == 0) throw new ResourceNotFoundException("No " + nameEntityConstant.getName() + " entry found with id: " + nameEntityId);
    }

    public void decrementNameEntityLengthCount(MediaNameEntityConstant nameEntityConstant, long nameEntityId) {
        int updated = switch (nameEntityConstant) {
            case MediaNameEntityConstant.AUTHORS -> mediaAuthorRepository.decrementLength(nameEntityId);
            case MediaNameEntityConstant.CHARACTERS -> mediaCharacterRepository.decrementLength(nameEntityId);
            case MediaNameEntityConstant.UNIVERSES -> mediaUniverseRepository.decrementLength(nameEntityId);
            case MediaNameEntityConstant.TAGS -> mediaTagRepository.decrementLength(nameEntityId);
        };
        if (updated == 0) throw new ResourceNotFoundException("No " + nameEntityConstant.getName() + " entry found with id: " + nameEntityId);
    }


    @Transactional
    public void addAuthor(String name) {
        addNameEntity(name, MediaNameEntityConstant.AUTHORS, new MediaAuthor(name), mediaAuthorRepository);
    }

    @Transactional
    public void addCharacter(NameAndThumbnailPostRequest request) {
        addNameEntity(request, MediaNameEntityConstant.CHARACTERS, new MediaCharacter(request.getName()), mediaCharacterRepository);
    }

    @Transactional
    public void addUniverse(NameAndThumbnailPostRequest request) {
        addNameEntity(request, MediaNameEntityConstant.UNIVERSES, new MediaUniverse(request.getName()), mediaUniverseRepository);
    }

    @Transactional
    public void addTag(String name) {
        addNameEntity(name, MediaNameEntityConstant.TAGS, new MediaTag(name), mediaTagRepository);
    }

    @Transactional
    protected <T extends MediaNameEntity> void addNameEntity(String name, MediaNameEntityConstant mediaNameEntityConstant, T mediaNameEntity, MediaNameEntityRepository<T, Long> repository) {
        name = validateNameEntity(name);

        mediaNameEntity.setName(name);

        T added = repository.save(mediaNameEntity);
        long id = added.getId();

        eventPublisher.publishEvent(new MediaUpdateEvent.NameEntityCreated(mediaNameEntityConstant, id));
    }

    @Transactional
    protected <T extends MediaNameEntityWithThumbnail> void addNameEntity(NameAndThumbnailPostRequest request,
                                                                          MediaNameEntityConstant mediaNameEntityConstant,
                                                                          T mediaNameEntity,
                                                                          MediaNameEntityRepository<T, Long> repository) {
        String extension = request.getThumbnail().getOriginalFilename() == null ? ".jpg"
                : request.getThumbnail().getOriginalFilename().substring(request.getThumbnail().getOriginalFilename().lastIndexOf("."));

        String path = mediaNameEntityConstant.getName() + "/" + request.getName() + "_" + UUID.randomUUID() + extension;

        try {
            // upload first to not start transaction first and hold the database connection
            minIOService.uploadFile(ContentMetaData.THUMBNAIL_BUCKET, path, request.getThumbnail());

            mediaNameEntity.setThumbnail(path);
            addNameEntity(request.getName(), mediaNameEntityConstant, mediaNameEntity, repository);
        } catch (Exception e) {
            try {
                minIOService.removeFile(ContentMetaData.THUMBNAIL_BUCKET, path);
                System.out.println("Compensation successful: Deleted file " + path + " from MinIO.");
            } catch (Exception deleteEx) {
                System.err.println("CRITICAL: Failed to clean up orphan file: " + path);
            }
            throw new RuntimeException("Entity creation failed after file upload.", e);
        }
    }


    @Transactional
    public void updateAuthor(long id, String name) {
        updateNameEntity(id, name, MediaNameEntityConstant.AUTHORS, mediaAuthorRepository);
    }

    @Transactional
    public void updateCharacter(long id, NameAndThumbnailPostRequest request) {
        updateNameEntity(id, request, MediaNameEntityConstant.CHARACTERS, mediaCharacterRepository);
    }

    @Transactional
    public void updateUniverse(long id, NameAndThumbnailPostRequest request) {
        updateNameEntity(id, request, MediaNameEntityConstant.UNIVERSES, mediaUniverseRepository);
    }

    @Transactional
    public void updateTag(long id, String name) {
        updateNameEntity(id, name, MediaNameEntityConstant.TAGS, mediaTagRepository);
    }

    @Transactional
    protected <T extends MediaNameEntity> void updateNameEntity(long id, String name,
                                                                MediaNameEntityConstant mediaNameEntityConstant,
                                                                MediaNameEntityRepository<T, Long> repository) {
        T nameEntity = findById(id, mediaNameEntityConstant, repository);
        name = validateNameEntity(name);
        if (nameEntity.getName().equals(name))
            return;
        nameEntity.setName(name);
        addNameEntity(name, mediaNameEntityConstant, nameEntity, repository);
    }

    @Transactional
    protected <T extends MediaNameEntityWithThumbnail> void updateNameEntity(long id, NameAndThumbnailPostRequest request,
                                                                             MediaNameEntityConstant mediaNameEntityConstant,
                                                                             MediaNameEntityRepository<T, Long> repository) {
        if (request.getThumbnail() == null && (request.getName() == null || request.getName().isBlank()))
            throw new IllegalArgumentException("No name or thumbnail provided");

        T nameEntity = findById(id, mediaNameEntityConstant, repository);
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

                newThumbnailPath = mediaNameEntityConstant.getName() + "/" + newName + "_" + UUID.randomUUID() + extension;
                minIOService.uploadFile(ContentMetaData.THUMBNAIL_BUCKET, newThumbnailPath, request.getThumbnail());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to update thumbnail for " + mediaNameEntityConstant.getName() + " " + id, e);
        }

        try {
            boolean nameChanged = !oldName.equals(newName);
            boolean thumbnailChanged = newThumbnailPath != null;

            if (nameChanged) nameEntity.setName(newName);
            if (thumbnailChanged) nameEntity.setThumbnail(newThumbnailPath);

            if (nameChanged || thumbnailChanged) repository.save(nameEntity);

            if (nameChanged) {
                try {
                    eventPublisher.publishEvent(new MediaUpdateEvent.NameEntityUpdated(mediaNameEntityConstant, id));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to publish event name entity updated for " + mediaNameEntityConstant.getName() + " " + id, e);
                }
            }

            if (thumbnailChanged && oldThumbnailPath != null) {
                try {
                    eventPublisher.publishEvent(new MediaUpdateEvent.ThumbnailDeleted(oldThumbnailPath));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to publish event clean up old thumbnail file: " + oldThumbnailPath, e);
                }
            }
        } catch (Exception e) {
            if (newThumbnailPath != null) {
                try {
                    minIOService.removeFile(ContentMetaData.THUMBNAIL_BUCKET, newThumbnailPath);
                } catch (Exception deleteEx) {
                    System.err.println("CRITICAL: Failed to clean up orphan new uploaded file: " + newThumbnailPath);
                }
            }
        }
    }


    @Transactional
    public void deleteAuthor(long id) {
        deleteNameEntity(id, MediaNameEntityConstant.AUTHORS, mediaAuthorRepository);
    }

    @Transactional
    public void deleteCharacter(long id) {
        deleteNameEntityWithThumbnail(id, MediaNameEntityConstant.CHARACTERS, mediaCharacterRepository);
    }

    @Transactional
    public void deleteUniverse(long id) {
        deleteNameEntityWithThumbnail(id, MediaNameEntityConstant.UNIVERSES, mediaUniverseRepository);
    }

    @Transactional
    public void deleteTag(long id) {
        deleteNameEntity(id, MediaNameEntityConstant.TAGS, mediaTagRepository);
    }

    // to be used locally for check
    @Transactional
    protected <T extends MediaNameEntity> void deleteNameEntity(long id, MediaNameEntityConstant mediaNameEntityConstant, MediaNameEntityRepository<T, Long> repository) {
        repository.deleteById(id);
        eventPublisher.publishEvent(new MediaUpdateEvent.NameEntityDeleted(mediaNameEntityConstant, id));
    }

    @Transactional
    protected <T extends MediaNameEntityWithThumbnail> void deleteNameEntityWithThumbnail(long id, MediaNameEntityConstant mediaNameEntityConstant,
                                                                                          MediaNameEntityRepository<T, Long> repository) {
        T nameEntity = findById(id, mediaNameEntityConstant, repository);

        String thumbnailPath = nameEntity.getThumbnail();

        repository.delete(nameEntity);

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            eventPublisher.publishEvent(new MediaUpdateEvent.NameEntityDeleted(mediaNameEntityConstant, id));
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to publish event delete opensearch name index" + mediaNameEntityConstant.getName() + " " + id, e);
                        }
                        try {
                            if (nameEntity.getThumbnail() != null)
                                eventPublisher.publishEvent(new MediaUpdateEvent.ThumbnailDeleted(thumbnailPath));
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to publish event delete thumbnail file: " + thumbnailPath, e);
                        }
                    }
                });
    }


    private <T extends MediaNameEntity> T findById(long id, MediaNameEntityConstant mediaNameEntityConstant, MediaNameEntityRepository<T, Long> repository) {
        return repository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("No " + mediaNameEntityConstant.getName() + " entry found with id: " + id));
    }

    private String validateNameEntity(String name) {
        if (name == null)
            throw new IllegalArgumentException("Name must not be null");
        name = name.toLowerCase().trim();
        if (name.isEmpty())
            throw new IllegalArgumentException("Name must not be empty");
        if (name.length() < 3)
            throw new IllegalArgumentException("Name must be at least 3 chars: " + name);
        if (name.length() > 200)
            throw new IllegalArgumentException("Name must be at most 200 chars");
        return name;
    }
}
