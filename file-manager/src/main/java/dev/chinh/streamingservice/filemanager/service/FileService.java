package dev.chinh.streamingservice.filemanager.service;

import com.mongodb.client.result.UpdateResult;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.EventTopics;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.common.validation.FileSystemValidator;
import dev.chinh.streamingservice.filemanager.constant.FileStatus;
import dev.chinh.streamingservice.filemanager.constant.FileType;
import dev.chinh.streamingservice.filemanager.data.FileItemField;
import dev.chinh.streamingservice.filemanager.data.FileSystemItem;
import dev.chinh.streamingservice.filemanager.data.FolderLocks;
import dev.chinh.streamingservice.filemanager.event.FileEventProducer;
import dev.chinh.streamingservice.filemanager.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.mongodb.MongoTransactionException;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRepository fileRepository;
    private final ApplicationEventPublisher publisher;

    private final FileUtilService fileUtilService;
    private final FileCacheService fileCacheService;
    private final FileLockService fileLockService;

    private final FileTrashService fileTrashService;

    public static final String MEDIA_PATH = ContentMetaData.MEDIA_BUCKET;


    public String getFileObjectUrl(String userId, String fileId) {
        var item = fileUtilService.getFileSystemItem(userId, fileId, true);
        if (!FileType.isNotDir(item.getFileType())) {
            throw new IllegalArgumentException("File is a directory");
        }
        String objectWithoutUserId = ContentMetaData.removeUserIdDirFromObjectKey(userId, item.getObjectName());
        return "/stream/object/" + UriUtils.encodePathSegment(item.getBucket(), StandardCharsets.UTF_8) + "/" + UriUtils.encodePath(objectWithoutUserId, StandardCharsets.UTF_8);
    }


    @Retryable(
            retryFor = { QueryTimeoutException.class, MongoTransactionException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public String addFileAsVideoMedia(String userId, String fileId, String nameUpdateListAsJson) {
        FileSystemItem item = fileUtilService.getFileSystemItem(userId, fileId, true);
        if (item.getFileType() != FileType.VIDEO) {
            throw new IllegalArgumentException("File is not a video");
        }
        if (item.getMId() != null && item.getMId() != 0) {
            throw new IllegalArgumentException("Item is already marked as video");
        }
        Map<String, Set<FileStatus>> parentIgnoreStatus = fileUtilService.getCommonIds(item.getPath()).stream()
                .collect(Collectors.toMap(key -> key, _ -> Set.of(FileStatus.BEING_MOVED_INTO)));
        FolderLocks folderIsLocked = fileLockService.checkIfFileItemInLock(fileUtilService.getCommonIds(item.getPath() + item.getId()), parentIgnoreStatus);
        if (folderIsLocked != null) {
            throw new IllegalArgumentException(fileUtilService.getLockedInfoString(userId, folderIsLocked.getId(), folderIsLocked.getStatusCode()));
        }

        fileLockService.lockFileItem(userId, Set.of(item.getId()), Collections.emptyMap());
        fileCacheService.invalidateFileCache(item.getId());

        publisher.publishEvent(new FileEventProducer.EventWrapper(
                EventTopics.MEDIA_HANDLER_TOPIC,
                userId,
                new MediaUpdateEvent.FileToVideoMediaInitiated(
                        userId,
                        fileId,
                        item.getBucket(), item.getObjectName(),
                        item.getName(), item.getUploadDate(),
                        nameUpdateListAsJson
                )
        ));
        return "Processing as video";
    }

    @Retryable(
            retryFor = { QueryTimeoutException.class, MongoTransactionException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public String addDirectoryAsAlbumMedia(String userId, String fileId) {
        FileSystemItem item = fileUtilService.getFileSystemItem(userId, fileId, false);
        if (item.getFileType() == FileType.ALBUM) {
            throw new IllegalArgumentException("Item is already an album");
        }
        if (item.getFileType() != FileType.DIR) {
            throw new IllegalArgumentException("File is not a directory");
        }
        if (item.getMId() != null && item.getMId() != 0) {
            throw new IllegalArgumentException("Item is already marked as media");
        }
        Map<String, Set<FileStatus>> parentIgnoreStatus = fileUtilService.getCommonIds(item.getPath()).stream()
                .collect(Collectors.toMap(key -> key, _ -> Set.of(FileStatus.BEING_MOVED_INTO)));
        FolderLocks folderIsLocked = fileLockService.checkIfFileItemInLock(fileUtilService.getCommonIds(item.getPath() + item.getId()), parentIgnoreStatus);
        if (folderIsLocked != null) {
            throw new IllegalArgumentException(fileUtilService.getLockedInfoString(userId, folderIsLocked.getId(), folderIsLocked.getStatusCode()));
        }

        Criteria criteria = Criteria.where(FileItemField.FILE_TYPE).is(FileType.ALBUM);
        List<FileSystemItem> items = fileUtilService.getItemInIds(fileUtilService.getCommonIds(item.getPath()), true, criteria, f -> f.getFileType() == FileType.ALBUM);
        if (!items.isEmpty()) {
            throw new IllegalArgumentException("Has parent as album - cannot have album in an album");
        }

        fileLockService.lockFileItem(userId, Set.of(item.getId()), Collections.emptyMap());
        fileCacheService.invalidateFileCache(item.getId());

        FileSystemItem parent = fileUtilService.getFileSystemItem(userId, item.getParentId(), true);
        boolean parentIsGrouper = parent.getFileType() == FileType.GROUPER;

        publisher.publishEvent(new FileEventProducer.EventWrapper(
                EventTopics.MEDIA_FILE_TOPIC,
                userId,
                new MediaUpdateEvent.DirectoryToAlbumMediaInitiated(
                        userId,
                        fileId,
                        null, null,
                        item.getName(), item.getUploadDate(),
                        !parentIsGrouper, 0, 0,
                        parentIsGrouper ? parent.getMId() : null
                )
        ));

        if (parentIsGrouper)
            return "Processing as album in grouper";
        return "Processing as album";
    }

    @Retryable(
            retryFor = { QueryTimeoutException.class, MongoTransactionException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public String addDirectoryAsGrouperMedia(String userId, String fileId) {
        FileSystemItem item = fileUtilService.getFileSystemItem(userId, fileId, false);
        if (item.getFileType() == FileType.GROUPER) {
            throw new IllegalArgumentException("Item is already a grouper");
        }
        if (item.getFileType() != FileType.DIR) {
            throw new IllegalArgumentException("File is not a directory");
        }
        if (item.getMId() != null && item.getMId() != 0) {
            throw new IllegalArgumentException("Item is already marked as media");
        }
        Map<String, Set<FileStatus>> parentIgnoreStatus = fileUtilService.getCommonIds(item.getPath()).stream()
                .collect(Collectors.toMap(key -> key, _ -> Set.of(FileStatus.BEING_MOVED_INTO)));
        FolderLocks folderIsLocked = fileLockService.checkIfFileItemInLock(fileUtilService.getCommonIds(item.getPath() + item.getId()), parentIgnoreStatus);
        if (folderIsLocked != null) {
            throw new IllegalArgumentException(fileUtilService.getLockedInfoString(userId, folderIsLocked.getId(), folderIsLocked.getStatusCode()));
        }

        Criteria criteria = Criteria.where(FileItemField.FILE_TYPE).is(FileType.ALBUM);
        List<FileSystemItem> items = fileUtilService.getItemInIds(fileUtilService.getCommonIds(item.getPath()), true, criteria, f -> f.getFileType() == FileType.ALBUM);
        if (!items.isEmpty()) {
            throw new IllegalArgumentException("Has parent as album - cannot have grouper in an album");
        }

        boolean anyDirectFile = fileRepository.checkExistWithUserId(userId, Criteria
                        .where(FileItemField.PARENT_ID).is(fileId)
                        .and(FileItemField.FILE_TYPE).nin(FileType.DIR, FileType.ALBUM, FileType.GROUPER));
        if (anyDirectFile) {
            throw new IllegalArgumentException("Contains direct files - can't be grouped - must include only direct directories");
        }

        fileLockService.lockFileItem(userId, Set.of(item.getId()), Collections.emptyMap());
        fileCacheService.invalidateFileCache(item.getId());

//        FileSystemItem first = findFirstImageOrVideo(userId, getPathForFileItem(item.getPath(), item.getId()));
        publisher.publishEvent(new FileEventProducer.EventWrapper(
                EventTopics.MEDIA_HANDLER_TOPIC, // send to media_handler first to get grouper/parent media id
                userId,
                new MediaUpdateEvent.NestedDirectoryToGrouperMediaInitiated(
                        userId,
                        item.getId(),
                        null,
                        null, null,
                        item.getName(), item.getUploadDate(),
                        false,
                        MediaType.ALBUM,
                        0
                )
        ));
        return "Processing as grouper";
    }

    @Retryable(
            retryFor = { QueryTimeoutException.class, MongoTransactionException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public FileSystemItem createNewDirectory(String userId, String parentId, String newFolderName) {
        String error = FileSystemValidator.isValidName(newFolderName);
        if (error != null)
            throw new IllegalArgumentException(error);
        FileSystemItem parent = fileUtilService.findById(userId, parentId, false);
        if (parent == null)
            throw new IllegalArgumentException("Parent folder not found: " + parentId);
        if (FileType.isNotDir(parent.getFileType()))
            throw new IllegalArgumentException("Parent folder is not a directory: " + parentId);
        if (fileUtilService.itemWithNameExists(userId, parentId, newFolderName))
            throw new IllegalArgumentException("Folder already exists: " + newFolderName);
        FolderLocks folderIsLocked = fileLockService.checkIfFileItemInLock(fileUtilService.getCommonIds(parent.getPath() + parent.getId()), null);
        if (folderIsLocked != null) {
            throw new IllegalArgumentException(fileUtilService.getLockedInfoString(userId, folderIsLocked.getId(), folderIsLocked.getStatusCode()));
        }

        FileSystemItem item = FileSystemItem.builder()
                .userId(Long.parseLong(userId))
                .parentId(parentId)
                .path(parent.getPath() + parentId + "/")
                .fileType(FileType.DIR)
                .name(newFolderName)
                .uploadDate(Instant.now())
                .build();

        var saved = fileRepository.insert(item);

        publisher.publishEvent(new FileEventProducer.EventWrapper(
                EventTopics.MEDIA_BACKUP_TOPIC,
                userId,
                new MediaUpdateEvent.DirectoryCreated(saved.getId(), fileUtilService.addUserIdToPathStart(userId, fileUtilService.getFullPathInName(saved, true)))
        ));
        log.info("Created new directory: {} {}", saved.getId(), saved.getName());

        return saved;
    }

    @Transactional
    public String renameFileItem(String userId, String fileId, String newName) {
        FileSystemItem item = fileUtilService.getFileSystemItem(userId, fileId, true);
        String error = FileSystemValidator.isValidName(newName);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        if (item.getParentId().equals(fileTrashService.getTRASH_FOLDER_ID())) {
            throw new IllegalArgumentException("Cannot rename item in trash");
        }
        if (item.getName().equals(newName)) {
            return newName;
        }
        if (fileUtilService.itemWithNameExists(userId, item.getParentId(), newName)) {
            throw new IllegalArgumentException("File already exists with name: " + newName);
        }
        Map<String, Set<FileStatus>> parentIgnoreStatus = fileUtilService.getCommonIds(item.getPath()).stream()
                .collect(Collectors.toMap(key -> key, _ -> Set.of(FileStatus.BEING_MOVED_INTO, FileStatus.BEING_MOVED, FileStatus.IN_USE, FileStatus.PROCESSING)));
        FolderLocks folderIsLocked = fileLockService.checkIfFileItemInLock(fileUtilService.getCommonIds(item.getPath() + item.getId()), parentIgnoreStatus);
        if (folderIsLocked != null) {
            throw new IllegalArgumentException(fileUtilService.getLockedInfoString(userId, folderIsLocked.getId(), folderIsLocked.getStatusCode()));
        }

        Update update = new Update().set(FileItemField.NAME, newName);
        fileRepository.updateFirstByIdAndUserId(userId, fileId, update);

        publisher.publishEvent(new FileEventProducer.EventWrapper(
                EventTopics.MEDIA_BACKUP_TOPIC,
                userId,
                new MediaUpdateEvent.FileRenamed(item.getId(), fileUtilService.addUserIdToPathStart(userId, fileUtilService.getFullPathInName(item, true)), newName)
        ));
        log.info("Renamed file: {}", fileId);

        return newName;
    }

    @Transactional
    public FileSystemItem initiateMoveFileItem(String userId, String fileId, String newParentId) {
        FileSystemItem item = fileUtilService.getFileSystemItem(userId, fileId, false);
        if (item.getParentId().equals(newParentId)) {
            throw new IllegalArgumentException("Item is already in parent directory: " + newParentId);
        }
        if (newParentId.equals(fileTrashService.getTRASH_FOLDER_ID())) {
            fileTrashService.moveToTrash(userId, List.of(fileId));
            return item;
        }
        FileSystemItem newParent = fileUtilService.findById(userId, newParentId, false);
        if (newParent == null) {
            throw new IllegalArgumentException("Parent folder not found: " + newParentId);
        }
        if (FileType.isNotDir(newParent.getFileType())) {
            throw new IllegalArgumentException("Parent is not a directory: " + newParentId);
        }
        if (item.getId().equals(newParentId)) {
            throw new IllegalArgumentException("Cannot move item to itself");
        }
        if (newParent.getPath().contains(item.getId())) {
            throw new IllegalArgumentException("Cannot move item to a child of itself");
        }
        if (item.getParentId().equals(fileTrashService.getTRASH_FOLDER_ID())) {
            fileTrashService.moveOutOfTrash(userId, List.of(fileId), Optional.of(newParent.getId()));
            return item;
        }
        Set<String> parentIds = fileUtilService.getCommonIds(newParent.getPath() + newParent.getId());
        Map<String, Set<FileStatus>> parentStatusMap = parentIds.stream()
                .collect(Collectors.toMap(id -> id, _ -> Set.of(FileStatus.BEING_MOVED_INTO)));

        Set<String> commonIds = fileUtilService.getCommonIds(item.getPath() + item.getId() + newParent.getPath() + newParent.getId());
        FolderLocks folderIsLocked = fileLockService.checkIfFileItemInLock(commonIds, parentStatusMap);
        if (folderIsLocked != null) {
            throw new IllegalArgumentException(fileUtilService.getLockedInfoString(userId, folderIsLocked.getId(), folderIsLocked.getStatusCode()));
        }
        if (fileUtilService.itemWithNameExists(userId, newParentId, item.getName())) {
            throw new IllegalArgumentException("File already exists with name: " + item.getName());
        }

        Update update = new Update()
                .set(FileItemField.PARENT_ID, newParentId)
                .set(FileItemField.PATH, newParent.getPath() + newParent.getId() + "/");

        if (!FileType.isNotDir(item.getFileType())) { // if a directory
            fileLockService.lockFileItem(userId, commonIds, parentIds.stream().collect(Collectors.toMap(id -> id, _ -> FileStatus.BEING_MOVED_INTO)));
            fileCacheService.invalidateFileCache(commonIds);

            publisher.publishEvent(new FileEventProducer.EventWrapper(
                    EventTopics.MEDIA_FILE_AND_BACKUP_TOPIC,
                    userId,
                    new MediaUpdateEvent.DirectoryMoved(
                            userId, fileId, newParentId,
                            item.getParentId(), item.getPath(), // old parent id, and old path
                            fileUtilService.addUserIdToPathStart(userId, fileUtilService.getFullPathInName(item, true)),
                            fileUtilService.addUserIdToPathStart(userId, fileUtilService.getFullPathInName(newParent, true)),
                            item.getFileType().name()
                    )
            ));

            if (item.getFileType() == FileType.ALBUM) {
                FileSystemItem oldParent = fileUtilService.findById(userId, item.getParentId(), true);
                boolean newParentIsGrouper = newParent.getFileType() == FileType.GROUPER;
                boolean oldParentIsGrouper = oldParent.getFileType() == FileType.GROUPER;
                if (oldParentIsGrouper || newParentIsGrouper) {
                    publisher.publishEvent(new FileEventProducer.EventWrapper(
                            EventTopics.MEDIA_HANDLER_TOPIC,
                            userId,
                            new MediaUpdateEvent.GrouperItemMoved(userId, item.getMId(), newParent.getMId(), item.getName(), oldParentIsGrouper, null)
                    ));
                }
                if (oldParentIsGrouper && !newParentIsGrouper) {
                    update.unset(FileItemField.MEDIA_ID);
                    update.unset(FileItemField.RESOLUTION_INFO);
                    update.unset(FileItemField.LENGTH);
                    update.unset(FileItemField.THUMBNAIL);
                    update.set(FileItemField.FILE_TYPE, FileType.DIR);
                }
            }
        } else {
            publisher.publishEvent(new FileEventProducer.EventWrapper(
                    EventTopics.MEDIA_BACKUP_TOPIC,
                    userId,
                    new MediaUpdateEvent.FileMoved(
                            fileId, newParent.getId(), fileUtilService.addUserIdToPathStart(userId, fileUtilService.getFullPathInName(item, true)), fileUtilService.addUserIdToPathStart(userId, fileUtilService.getFullPathInName(newParent, true))
                    )
            ));
        }

        FileSystemItem moved = fileRepository.findAndModifyById(fileId, update);

        if (moved != null && (item.getFileType() == FileType.IMAGE || item.getThumbnail() != null)) {
            String thumbnailPath = ThumbnailService.getThumbnailPath(
                    ThumbnailService.getThumbnailParentPath(),
                    item.getMId() == null ? item.getId() : item.getMId().toString(),
                    item.getThumbnail() == null ? item.getObjectName() : item.getThumbnail()
            );
            moved.setThumbnail(thumbnailPath);
        }

        return moved;
    }

    @Retryable(
            retryFor = { QueryTimeoutException.class, MongoTransactionException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public UpdateResult updateFileMetadataAsMedia(String userId, String fileId, long mediaId, FileType fileType, String thumbnailObject,
                                                  int length, Integer width, Integer height) {
        Update update = new Update()
                .set(FileItemField.MEDIA_ID, mediaId)
                .set(FileItemField.FILE_TYPE, fileType)
                .set(FileItemField.LENGTH, length);
        if (thumbnailObject != null)
            update.set(FileItemField.THUMBNAIL, thumbnailObject);
        if (width != null && height != null)
            update.set(FileItemField.RESOLUTION_INFO, new FileSystemItem.ResolutionInfo(width, height));
        return fileRepository.updateFirstByIdAndUserId(userId, fileId, update);
    }


    public void createRootFolder() {
        Query query = new Query(Criteria
                .where(FileItemField.NAME).is(MEDIA_PATH)
                .and(FileItemField.PATH).is("/")
                .and(FileItemField.FILE_TYPE).is(FileType.DIR)
        );

        Update update = new Update()
                .setOnInsert(FileItemField.NAME, MEDIA_PATH)
                .setOnInsert(FileItemField.PATH, "/")
                .setOnInsert(FileItemField.FILE_TYPE, FileType.DIR);

        UpdateResult result = fileRepository.upsert(query, update);
        if (!result.wasAcknowledged()) throw new RuntimeException("Failed to create root folder");
    }
}