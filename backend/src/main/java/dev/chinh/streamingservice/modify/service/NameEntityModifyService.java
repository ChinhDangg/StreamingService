package dev.chinh.streamingservice.modify.service;

import dev.chinh.streamingservice.content.service.MinIOService;
import dev.chinh.streamingservice.data.ContentMetaData;
import dev.chinh.streamingservice.data.entity.*;
import dev.chinh.streamingservice.data.repository.*;
import dev.chinh.streamingservice.data.service.ThumbnailService;
import dev.chinh.streamingservice.exception.ResourceNotFoundException;
import dev.chinh.streamingservice.modify.MediaNameEntityConstant;
import dev.chinh.streamingservice.search.service.OpenSearchService;
import dev.chinh.streamingservice.modify.NameAndThumbnailPostRequest;
import dev.chinh.streamingservice.modify.NameEntityDTO;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NameEntityModifyService {

    private final MediaAuthorRepository mediaAuthorRepository;
    private final MediaCharacterRepository mediaCharacterRepository;
    private final MediaUniverseRepository mediaUniverseRepository;
    private final MediaTagRepository mediaTagRepository;
    private final OpenSearchService openSearchService;
    private final MinIOService minIOService;

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


    public List<NameEntityDTO> searchNameContaining(String index, String name) throws IOException {
        SearchResponse<NameEntityDTO> response = openSearchService.searchContaining(index, ContentMetaData.NAME, name, NameEntityDTO.class);
        return response.hits().hits().stream()
                .map(h -> {
                    NameEntityDTO dto = h.source();
                    assert dto != null;
                    assert h.id() != null;
                    dto.setId(Long.parseLong(h.id()));
                    return dto;
                })
                .toList();
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

        String finalName = name;
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            openSearchService.indexDocument(mediaNameEntityConstant.getName(), id,
                                    Map.of(ContentMetaData.NAME, finalName));
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to index " + mediaNameEntityConstant.getName() + " " + id + " to OpenSearch");
                        }
                    }
                }
        );
    }

    @Transactional
    protected <T extends MediaNameEntityWithThumbnail> void addNameEntity(NameAndThumbnailPostRequest request,
                                                                          MediaNameEntityConstant mediaNameEntityConstant,
                                                                          T mediaNameEntity,
                                                                          MediaNameEntityRepository<T, Long> repository) {
        String extension = request.getThumbnail().getOriginalFilename() == null ? ".jpg"
                : request.getThumbnail().getOriginalFilename().substring(request.getThumbnail().getOriginalFilename().lastIndexOf("."));

        String path = mediaNameEntityConstant.getName() + "/" + request.getName() + extension;

        try {
            // upload first to not start transaction first and hold the database connection
            minIOService.uploadFile(ThumbnailService.thumbnailBucket, path, request.getThumbnail());

            mediaNameEntity.setThumbnail(path);
            addNameEntity(request.getName(), mediaNameEntityConstant, mediaNameEntity, repository);
        } catch (Exception e) {
            try {
                minIOService.removeFile(ThumbnailService.thumbnailBucket, path);
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
        T nameEntity = findById(id, mediaNameEntityConstant.getName(), repository);
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

        T nameEntity = findById(id, mediaNameEntityConstant.getName(), repository);
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

                newThumbnailPath = mediaNameEntityConstant.getName() + "/" + UUID.randomUUID() + extension;
                minIOService.uploadFile(ThumbnailService.thumbnailBucket, newThumbnailPath, request.getThumbnail());
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

            String finalNewName = newName;
            String finalNewThumbnail = newThumbnailPath;

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            if (nameChanged) {
                                try {
                                    openSearchService.partialUpdateDocument(mediaNameEntityConstant.getName(), id, Map.of(ContentMetaData.NAME, finalNewName));
                                } catch (IOException ie) {
                                    throw new RuntimeException("Failed to update OpenSearch index for " + mediaNameEntityConstant.getName() + " " + id, ie);
                                }
                            }
                            if (thumbnailChanged && oldThumbnailPath != null) {
                                try {
                                    minIOService.removeFile(ThumbnailService.thumbnailBucket, oldThumbnailPath);
                                } catch (Exception e) {
                                    throw new RuntimeException("Failed to clean up old thumbnail file: " + oldThumbnailPath, e);
                                }
                            }
                        }

                        @Override
                        public void afterCompletion(int status) {
                            if (status == STATUS_ROLLED_BACK && finalNewThumbnail != null) {
                                try {
                                    minIOService.removeFile(ThumbnailService.thumbnailBucket, finalNewThumbnail);
                                } catch (Exception e) {
                                    throw new RuntimeException("Failed to clean up newly uploaded thumbnail file: " + finalNewThumbnail, e);
                                }
                            }
                        }
                    }
            );
        } catch (Exception e) {
            if (newThumbnailPath != null) {
                try {
                    minIOService.removeFile(ThumbnailService.thumbnailBucket, newThumbnailPath);
                } catch (Exception deleteEx) {
                    System.err.println("CRITICAL: Failed to clean up orphan file: " + newThumbnailPath);
                }
            }
        }
    }


    @Transactional
    public void deleteAuthor(long id) {
        deleteNameEntity(id, ContentMetaData.AUTHORS, mediaAuthorRepository);
    }

    @Transactional
    public void deleteCharacter(long id) {
        deleteNameEntityWithThumbnail(id, ContentMetaData.CHARACTERS, mediaCharacterRepository);
    }

    @Transactional
    public void deleteUniverse(long id) {
        deleteNameEntityWithThumbnail(id, ContentMetaData.UNIVERSES, mediaUniverseRepository);
    }

    @Transactional
    public void deleteTag(long id) {
        deleteNameEntity(id, ContentMetaData.TAGS, mediaTagRepository);
    }

    // to be used locally for check
    @Transactional
    protected <T extends MediaNameEntity> void deleteNameEntity(long id, String listName, MediaNameEntityRepository<T, Long> repository) {
        T nameEntity = findById(id, listName, repository);

        repository.delete(nameEntity);

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            openSearchService.deleteDocument(listName, id);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete opensearch name index" + listName + " entry with ID " + id, e);
                        }
                    }
                });
    }

    @Transactional
    protected <T extends MediaNameEntityWithThumbnail> void deleteNameEntityWithThumbnail(long id, String listName, MediaNameEntityRepository<T, Long> repository) {
        T nameEntity = findById(id, listName, repository);

        String thumbnailPath = nameEntity.getThumbnail();

        repository.delete(nameEntity);

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            openSearchService.deleteDocument(listName, id);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete opensearch name index" + listName + " entry with ID " + id, e);
                        }
                        try {
                            if (nameEntity.getThumbnail() != null)
                                minIOService.removeFile(ThumbnailService.thumbnailBucket, thumbnailPath);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to delete thumbnail file: " + thumbnailPath, e);
                        }
                    }
                });
    }


    private <T extends MediaNameEntity> T findById(long id, String listName, MediaNameEntityRepository<T, Long> repository) {
        return repository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("No " + listName + " entry found with id: " + id));
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
