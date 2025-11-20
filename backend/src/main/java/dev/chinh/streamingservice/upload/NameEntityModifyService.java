package dev.chinh.streamingservice.upload;

import dev.chinh.streamingservice.content.service.MinIOService;
import dev.chinh.streamingservice.data.ContentMetaData;
import dev.chinh.streamingservice.data.entity.*;
import dev.chinh.streamingservice.data.repository.*;
import dev.chinh.streamingservice.data.service.ThumbnailService;
import dev.chinh.streamingservice.exception.DuplicateEntryException;
import dev.chinh.streamingservice.exception.ResourceNotFoundException;
import dev.chinh.streamingservice.search.service.OpenSearchService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
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

    public List<NameEntityDTO> searchNameContaining(String index, String name) throws IOException {
        SearchResponse<NameEntityDTO> response = openSearchService.searchContaining(index, ContentMetaData.NAME, name, NameEntityDTO.class);
        return response.hits().hits().stream()
                .map(h -> {
                    assert h.source() != null;
                    return h.source();
                })
                .toList();
    }


    public record NameAndThumbnailPostRequest(
            @NotBlank
            String name,
            @NotNull
            MultipartFile thumbnail
    ) {}

    @Transactional
    public void addAuthor(String name) {
        addNameEntity(name, ContentMetaData.AUTHORS, new MediaAuthor(name, Instant.now()), mediaAuthorRepository);
    }

    @Transactional
    public void addCharacter(NameAndThumbnailPostRequest request) {
        addNameEntity(request, ContentMetaData.CHARACTERS, new MediaCharacter(request.name, Instant.now()), mediaCharacterRepository);
    }

    @Transactional
    public void addUniverse(NameAndThumbnailPostRequest request) {
        addNameEntity(request, ContentMetaData.UNIVERSES, new MediaUniverse(request.name, Instant.now()), mediaUniverseRepository);
    }

    @Transactional
    public void addTag(String name) {
        addNameEntity(name, ContentMetaData.TAGS, new MediaTag(name, Instant.now()), mediaTagRepository);
    }

    @Transactional
    protected <T extends MediaNameEntity> void addNameEntity(String name, String listName, T mediaNameEntity, MediaNameEntityRepository<T, Long> repository) {
        name = validateNameEntity(name);
        try {
            repository.save(mediaNameEntity);
        } catch(DataIntegrityViolationException e) {
            if (e.getRootCause() != null && e.getRootCause().getMessage().contains("unique constraint")) {
                throw new DuplicateEntryException(
                        "The name entry '" + name + "' already exists in the " + listName + " list."
                );
            }
            // Handle other DataIntegrityViolationExceptions (e.g., foreign key)
            throw e;
        }
    }

    @Transactional
    protected <T extends MediaNameEntityWithThumbnail> void addNameEntity(NameAndThumbnailPostRequest request, String listName, T mediaNameEntity, MediaNameEntityRepository<T, Long> repository) {
        String extension = request.thumbnail.getOriginalFilename() == null ? ".jpg"
                : request.thumbnail.getOriginalFilename().substring(request.thumbnail.getOriginalFilename().lastIndexOf("."));

        String path = listName + "/" + request.name + extension;

        try {
            // upload first to not start transaction first and hold the database connection
            minIOService.uploadFile(ThumbnailService.thumbnailBucket, path, request.thumbnail);

            mediaNameEntity.setThumbnail(path);
            addNameEntity(request.name, listName, mediaNameEntity, repository);
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
        updateNameEntity(id, name, ContentMetaData.AUTHORS, mediaAuthorRepository);
    }

    @Transactional
    public void updateCharacter(long id, NameAndThumbnailPostRequest request) {
        updateNameEntity(id, request.name, ContentMetaData.CHARACTERS, mediaCharacterRepository);
    }

    @Transactional
    public void updateUniverse(long id, NameAndThumbnailPostRequest request) {
        updateNameEntity(id, request.name, ContentMetaData.UNIVERSES, mediaUniverseRepository);
    }

    @Transactional
    public void updateTag(long id, String name) {
        updateNameEntity(id, name, ContentMetaData.TAGS, mediaTagRepository);
    }

    @Transactional
    protected <T extends MediaNameEntity> void updateNameEntity(long id, String name, String listName, MediaNameEntityRepository<T, Long> repository) {
        T nameEntity = findById(id, listName, repository);
        name = validateNameEntity(name);
        if (nameEntity.getName().equals(name))
            return;
        nameEntity.setName(name);
        addNameEntity(name, listName, nameEntity, repository);
    }

    @Transactional
    protected <T extends MediaNameEntityWithThumbnail> void updateNameEntity(long id, NameAndThumbnailPostRequest request, String listName, MediaNameEntityRepository<T, Long> repository) {
        T nameEntity = findById(id, listName, repository);

        // The old thumbnail path to be deleted upon successful commit
        String oldThumbnailPath = nameEntity.getThumbnail();
        String newThumbnailPath = null;

        try {
            // --- 1. UPLOAD NEW FILE FIRST (OUTSIDE TRANSACTION) ---
            if (request.thumbnail != null) {
                String extension = request.thumbnail.getOriginalFilename() == null ? ".jpg" :
                        request.thumbnail.getOriginalFilename().substring(request.thumbnail.getOriginalFilename().lastIndexOf("."));

                newThumbnailPath = listName + "/" + UUID.randomUUID() + extension;
                
                minIOService.uploadFile(ThumbnailService.thumbnailBucket, newThumbnailPath, request.thumbnail);
            }

            // START/COMMIT TRANSACTION ---
            if (newThumbnailPath != null) {
                nameEntity.setThumbnail(newThumbnailPath);
            }

            String newName = request.name == null ? nameEntity.getName() : validateNameEntity(request.name);

            if (!nameEntity.getName().equals(newName)) {
                nameEntity.setName(newName);
            }
            if (newThumbnailPath != null || !nameEntity.getName().equals(newName)) {
                repository.save(nameEntity);
            }
            if (newThumbnailPath != null && oldThumbnailPath != null) {
                // New file is saved and DB committed. Now delete the old file.
                minIOService.removeFile(ThumbnailService.thumbnailBucket, oldThumbnailPath);
            }

        } catch (DataIntegrityViolationException e) {
            // Handle constraint violation (DB rolled back automatically)
            handleUploadRollback(newThumbnailPath);
            throw new DuplicateEntryException("The name entry '" + request.name + "' already exists in the " + listName + " list.");
        } catch (Exception e) {
            // Handle other failures (e.g., MinIO upload failed, or other exceptions)
            handleUploadRollback(newThumbnailPath);
            throw new RuntimeException("Update failed for entity ID " + id, e);
        }
    }

    private void handleUploadRollback(String uploadedPath) {
        if (uploadedPath != null) {
            // If the DB save failed, this file should NOT exist. Delete the newly uploaded file.
            try {
                minIOService.removeFile(ThumbnailService.thumbnailBucket, uploadedPath);
                System.out.println("Rollback compensation: Deleted newly uploaded file: " + uploadedPath);
            } catch (Exception deleteEx) {
                // Log the critical failure
                System.err.println("CRITICAL: Failed to clean up temporary orphan file: " + uploadedPath);
            }
        }
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
